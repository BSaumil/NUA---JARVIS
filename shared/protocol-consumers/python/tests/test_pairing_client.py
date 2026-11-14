"""Real integration tests: these spin up the actual companion service
(services/companion/nua_companion/service.py's serve()) and drive this
client package's pairing_client/api_client against it over real HTTP and
real Ed25519 signatures -- nothing here is mocked.

The central thing under test is the directive's own safety boundary: proving
control of a keypair (pairing) is necessary but not sufficient for trust.
Only an explicit, separate owner_approve call -- standing in here for a human
running `nua-companion approve` on the companion node -- grants it.
"""
from __future__ import annotations

import pytest

from nua_companion import service as companion_service
from nua_companion.pairing import PairingStore
from nua_companion.security import ReplayWindow

from nua_client.api_client import ApiClient
from nua_client.identity import ClientIdentity
from nua_client.pairing_client import PairingClient
from nua_client.transport import CompanionRequestFailed


@pytest.fixture
def companion(tmp_path):
    # Each test gets its own pairing/replay state, independent of any other
    # test and of whatever happens to be on disk at NUA_COMPANION_STATE.
    companion_service.STATE.pairing = PairingStore(tmp_path / "pairing.json")
    companion_service.STATE.replay = ReplayWindow()
    server = companion_service.serve(host="127.0.0.1", port=0)
    base_url = f"http://127.0.0.1:{server.server_address[1]}"
    try:
        yield base_url
    finally:
        server.shutdown()


def _client_identity(tmp_path, name: str) -> ClientIdentity:
    return ClientIdentity(state_dir=tmp_path / name)


def test_health_and_presence_need_no_pairing_at_all(companion, tmp_path):
    identity = _client_identity(tmp_path, "client")
    api = ApiClient(companion, identity)

    health = api.health()
    presence = api.presence()

    assert health["ready"] is True
    assert presence["trustState"] == "APPROVED"


def test_unapproved_device_is_refused_even_with_a_valid_signature(companion, tmp_path):
    identity = _client_identity(tmp_path, "client")
    pairing = PairingClient(companion, identity)
    result = pairing.request_pairing()
    assert result.signature_verified is True  # proves the key -- but is NOT trust

    api = ApiClient(companion, identity)
    with pytest.raises(CompanionRequestFailed) as excinfo:
        api.inference("hello")
    assert excinfo.value.status == 403
    assert excinfo.value.body["error"] == "unpaired_or_revoked"


def test_owner_approval_is_required_and_sufficient_to_pass_authorization(companion, tmp_path):
    identity = _client_identity(tmp_path, "client")
    pairing = PairingClient(companion, identity)
    result = pairing.request_pairing()

    # Stands in for a human running `nua-companion approve <challenge-id>
    # --scope INFERENCE` on the companion node -- this client cannot and does
    # not do this itself.
    approved = companion_service.STATE.pairing.owner_approve(result.challenge_id, granted_scopes=("INFERENCE",))
    assert approved is not None

    api = ApiClient(companion, identity)
    # No local model is configured in this test environment, so the request
    # correctly fails *after* authorization -- which is exactly what proves
    # authorization (pairing/scope/signature/replay) succeeded here.
    with pytest.raises(CompanionRequestFailed) as excinfo:
        api.inference("hello")
    assert excinfo.value.status == 503
    assert excinfo.value.body["error"] == "no_local_model_configured"


def test_a_tampered_request_is_rejected_by_signature_verification(companion, tmp_path):
    identity = _client_identity(tmp_path, "client")
    pairing = PairingClient(companion, identity)
    result = pairing.request_pairing()
    companion_service.STATE.pairing.owner_approve(result.challenge_id, granted_scopes=("INFERENCE",))

    api = ApiClient(companion, identity)
    # Build a legitimately signed request, then swap *both* payload and
    # payloadJson for a consistent but different pair, keeping the original
    # meta (and so the original signature) -- this is the strongest possible
    # tamper attempt (consistent payload/payloadJson, since inconsistent ones
    # are already caught by the signed_payload_mismatch check on their own)
    # and must still fail: the signature was computed over a specific
    # payload_json string and can't verify over a different one.
    from nua_client.transport import post_json

    body = api._build_signed_body("inference.start", ["INFERENCE"], {"modelId": "", "prompt": "hello"})
    tampered_payload = {"modelId": "", "prompt": "this prompt was swapped out after signing"}
    body["payload"] = tampered_payload
    body["payloadJson"] = '{"modelId":"","prompt":"this prompt was swapped out after signing"}'

    with pytest.raises(CompanionRequestFailed) as excinfo:
        post_json(companion, "/inference", body)
    assert excinfo.value.status == 403
    assert excinfo.value.body["error"] == "bad_signature"


def test_two_devices_cannot_be_confused_with_each_other(companion, tmp_path):
    device_a = _client_identity(tmp_path, "device-a")
    device_b = _client_identity(tmp_path, "device-b")
    assert device_a.identity.deviceId != device_b.identity.deviceId
    assert device_a.public_key_base64() != device_b.public_key_base64()

    pairing_a = PairingClient(companion, device_a)
    result_a = pairing_a.request_pairing()
    companion_service.STATE.pairing.owner_approve(result_a.challenge_id)

    # device_b was never paired at all; its signed request must be refused,
    # even though a *different* device is fully trusted on this companion.
    api_b = ApiClient(companion, device_b)
    with pytest.raises(CompanionRequestFailed) as excinfo:
        api_b.inference("hello")
    assert excinfo.value.status == 403
    assert excinfo.value.body["error"] == "unpaired_or_revoked"
