"""Real integration tests for the companion mounted-files connector
(connector library priority item #10): spins up the actual companion
service with a real configured mount, pairs and gets owner approval the
same way test_pairing_client.py does, then lists/reads through it for
real -- plus the negative cases a read-only, owner-scoped files connector
most needs to get right: an unapproved device, a path-traversal attempt,
and an unconfigured mount name.
"""
from __future__ import annotations

import pytest

from nua_companion import service as companion_service
from nua_companion.mounts import MountRegistry
from nua_companion.pairing import PairingStore
from nua_companion.security import ReplayWindow

from nua_client.api_client import ApiClient
from nua_client.identity import ClientIdentity
from nua_client.pairing_client import PairingClient
from nua_client.transport import CompanionRequestFailed


@pytest.fixture
def companion_with_mount(tmp_path):
    mount_root = tmp_path / "mount-root"
    mount_root.mkdir()
    (mount_root / "readme.txt").write_text("hello from the companion")
    sub = mount_root / "sub"
    sub.mkdir()
    (sub / "nested.txt").write_text("nested file")

    companion_service.STATE.pairing = PairingStore(tmp_path / "pairing.json")
    companion_service.STATE.replay = ReplayWindow()
    companion_service.STATE.mounts = MountRegistry.from_env(f"documents:{mount_root}")
    server = companion_service.serve(host="127.0.0.1", port=0)
    base_url = f"http://127.0.0.1:{server.server_address[1]}"
    try:
        yield base_url, mount_root
    finally:
        server.shutdown()


def _paired_api_client(base_url: str, tmp_path) -> ApiClient:
    identity = ClientIdentity(state_dir=tmp_path / "client")
    pairing = PairingClient(base_url, identity)
    result = pairing.request_pairing()
    companion_service.STATE.pairing.owner_approve(result.challenge_id)
    return ApiClient(base_url, identity)


def test_presence_advertises_the_files_connector_once_a_mount_is_configured(companion_with_mount, tmp_path):
    base_url, _ = companion_with_mount
    identity = ClientIdentity(state_dir=tmp_path / "client")
    api = ApiClient(base_url, identity)

    presence = api.presence()

    connector_ids = [c["id"] for c in presence["connectors"]]
    assert "companion-files" in connector_ids


def test_an_approved_device_can_list_and_read_through_the_mount(companion_with_mount, tmp_path):
    base_url, mount_root = companion_with_mount
    api = _paired_api_client(base_url, tmp_path)

    entries = {entry.name: entry for entry in api.files_list("documents")}
    assert entries["readme.txt"].is_dir is False
    assert entries["readme.txt"].size_bytes == len("hello from the companion")
    assert entries["sub"].is_dir is True

    content = api.files_read("documents", "readme.txt")
    assert content == b"hello from the companion"

    nested = api.files_read("documents", "sub/nested.txt")
    assert nested == b"nested file"


def test_an_unapproved_device_cannot_list_or_read_files(companion_with_mount, tmp_path):
    base_url, _ = companion_with_mount
    identity = ClientIdentity(state_dir=tmp_path / "client")
    pairing = PairingClient(base_url, identity)
    pairing.request_pairing()  # proved the key, but never approved
    api = ApiClient(base_url, identity)

    with pytest.raises(CompanionRequestFailed) as excinfo:
        api.files_list("documents")
    assert excinfo.value.status == 403
    assert excinfo.value.body["error"] == "unpaired_or_revoked"


def test_a_path_traversal_attempt_is_refused_even_from_an_approved_device(companion_with_mount, tmp_path):
    base_url, _ = companion_with_mount
    api = _paired_api_client(base_url, tmp_path)

    with pytest.raises(CompanionRequestFailed) as excinfo:
        api.files_read("documents", "../../etc/passwd")
    assert excinfo.value.status == 404


def test_an_unconfigured_mount_name_is_refused_not_treated_as_a_raw_path(companion_with_mount, tmp_path):
    base_url, _ = companion_with_mount
    api = _paired_api_client(base_url, tmp_path)

    with pytest.raises(CompanionRequestFailed) as excinfo:
        api.files_list("some-other-mount-name")
    assert excinfo.value.status == 404
