from nua_companion.security import ReplayWindow, digest_request

def test_replay_window_rejects_reuse():
    window = ReplayWindow()
    assert window.accept("n1", 1000, now=1000)
    assert not window.accept("n1", 1000, now=1001)

def test_request_digest_binds_operation_and_payload():
    a = digest_request(1, "r", "c", "n", 1000, "health", "{}")
    b = digest_request(1, "r", "c", "n", 1000, "inference", "{}")
    assert a != b
