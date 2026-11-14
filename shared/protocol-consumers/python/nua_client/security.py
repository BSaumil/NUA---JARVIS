"""Wire-format helpers mirroring services/companion/nua_companion/security.py
exactly, field for field. Client and companion are protocol-interoperable by
construction: both canonicalize a request the same way and both use
cryptography's Ed25519 primitives, so a signature made here verifies there
and vice versa.
"""
from __future__ import annotations

import json
import secrets
import time


def now_ms() -> int:
    return int(time.time() * 1000)


def new_nonce() -> str:
    import base64

    return base64.b64encode(secrets.token_bytes(32)).decode("ascii")


def compact_json(value: object) -> str:
    return json.dumps(value, separators=(",", ":"), sort_keys=True)


def canonical_request(
    protocol_version: int,
    request_id: str,
    client_id: str,
    nonce: str,
    timestamp_ms: int,
    operation: str,
    payload_json: str,
) -> bytes:
    return "|".join(
        [str(protocol_version), request_id, client_id, nonce, str(timestamp_ms), operation, payload_json]
    ).encode("utf-8")


def digest_request(*args) -> bytes:
    import hashlib

    return hashlib.sha256(canonical_request(*args)).digest()
