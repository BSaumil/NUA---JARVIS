from __future__ import annotations
import base64
import hashlib
import json
import secrets
import time
from dataclasses import dataclass
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives.serialization import Encoding, PrivateFormat, PublicFormat, NoEncryption
from pathlib import Path

MAX_CLOCK_SKEW_MS = 120_000

def now_ms() -> int:
    return int(time.time() * 1000)

def canonical_request(protocol_version: int, request_id: str, client_id: str, nonce: str, timestamp_ms: int, operation: str, payload_json: str) -> bytes:
    return "|".join([
        str(protocol_version), request_id, client_id, nonce, str(timestamp_ms), operation, payload_json
    ]).encode("utf-8")

def digest_request(*args) -> bytes:
    return hashlib.sha256(canonical_request(*args)).digest()

def verify_ed25519(public_key_b64: str, signature_b64: str, message: bytes) -> bool:
    try:
        key = Ed25519PublicKey.from_public_bytes(base64.b64decode(public_key_b64))
        key.verify(base64.b64decode(signature_b64), message)
        return True
    except (ValueError, InvalidSignature):
        return False

@dataclass
class ReplayWindow:
    max_entries: int = 4096

    def __post_init__(self) -> None:
        self._seen: dict[str, int] = {}

    def accept(self, nonce: str, timestamp_ms: int, now: int | None = None) -> bool:
        now = now if now is not None else now_ms()
        if abs(now - timestamp_ms) > MAX_CLOCK_SKEW_MS or nonce in self._seen:
            return False
        self._seen[nonce] = timestamp_ms
        if len(self._seen) > self.max_entries:
            oldest = sorted(self._seen.items(), key=lambda item: item[1])[: len(self._seen) - self.max_entries]
            for key, _ in oldest:
                self._seen.pop(key, None)
        return True

def new_nonce() -> str:
    return base64.b64encode(secrets.token_bytes(32)).decode("ascii")

def compact_json(value: object) -> str:
    return json.dumps(value, separators=(",", ":"), sort_keys=True)


def load_or_create_identity_key(path: Path) -> Ed25519PrivateKey:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raw = path.read_bytes()
        return Ed25519PrivateKey.from_private_bytes(raw)
    key = Ed25519PrivateKey.generate()
    raw = key.private_bytes(Encoding.Raw, PrivateFormat.Raw, NoEncryption())
    path.write_bytes(raw)
    path.chmod(0o600)
    return key

def public_key_b64(key: Ed25519PrivateKey) -> str:
    raw = key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    return base64.b64encode(raw).decode("ascii")

def sign_b64(key: Ed25519PrivateKey, message: bytes) -> str:
    return base64.b64encode(key.sign(message)).decode("ascii")
