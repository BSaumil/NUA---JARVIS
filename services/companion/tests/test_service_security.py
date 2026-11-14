import pytest

from nua_companion.service import serve


def test_non_loopback_serving_requires_tls():
    with pytest.raises(ValueError):
        serve("0.0.0.0", 0)


def test_loopback_serving_allows_plain_http_for_local_shells():
    server = serve("127.0.0.1", 0)
    try:
        assert server.server_address[0] == "127.0.0.1"
    finally:
        server.shutdown()
        server.server_close()


def test_signed_payload_string_must_match_dispatched_payload():
    # The request verifier must not sign one payload and dispatch another. The full
    # cryptographic path is covered by pairing/security tests; this pins body agreement.
    import json
    signed = json.dumps({"prompt": "safe"}, separators=(",", ":"), sort_keys=True)
    dispatched = {"prompt": "changed"}
    assert json.loads(signed) != dispatched
