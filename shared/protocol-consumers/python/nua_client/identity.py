"""Device identity for this desktop client: an Ed25519 keypair plus the
DeviceIdentity fields the companion's pairing/authorization logic expects
(see services/companion/nua_companion/protocol.py's DeviceIdentity).

The private key never leaves this module. Only the public key (base64 of the
raw 32-byte Ed25519 public key, matching Ed25519PublicKey.from_public_bytes on
the companion side) is ever sent over the wire.
"""
from __future__ import annotations

import base64
import os
import platform as _platform
import socket
import uuid
from dataclasses import dataclass
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives.serialization import Encoding, NoEncryption, PrivateFormat, PublicFormat

PROTOCOL_VERSION = 1

_DEFAULT_STATE_DIR = Path(os.getenv("NUA_CLIENT_STATE_DIR", "~/.nua")).expanduser()
_KEY_FILENAME = "client-identity.key"


def _detect_platform() -> str:
    system = _platform.system()
    if system == "Windows":
        return "WINDOWS"
    if system == "Darwin":
        return "MACOS"
    if system == "Linux":
        return "LINUX"
    return "UNKNOWN"


@dataclass(frozen=True)
class DeviceIdentity:
    deviceId: str
    publicKey: str
    ownerLabel: str
    platform: str
    appVersion: str
    protocolVersion: int = PROTOCOL_VERSION

    def to_dict(self) -> dict:
        return {
            "deviceId": self.deviceId,
            "publicKey": self.publicKey,
            "ownerLabel": self.ownerLabel,
            "platform": self.platform,
            "appVersion": self.appVersion,
            "protocolVersion": self.protocolVersion,
        }


class ClientIdentity:
    """Loads (or creates) this device's persistent Ed25519 keypair and exposes
    a DeviceIdentity for use in pairing and signed requests."""

    def __init__(self, state_dir: Path | None = None, app_version: str = "0.1.0") -> None:
        self._state_dir = state_dir if state_dir is not None else _DEFAULT_STATE_DIR
        self._key_path = self._state_dir / _KEY_FILENAME
        self._private_key = self._load_or_create_key()
        device_id = os.getenv("NUA_CLIENT_DEVICE_ID") or self._stable_device_id()
        owner_label = os.getenv("NUA_CLIENT_LABEL", f"{socket.gethostname()} ({_detect_platform()})")
        self.identity = DeviceIdentity(
            deviceId=device_id,
            publicKey=self.public_key_base64(),
            ownerLabel=owner_label,
            platform=_detect_platform(),
            appVersion=app_version,
        )

    def _stable_device_id(self) -> str:
        id_path = self._state_dir / "device-id"
        if id_path.exists():
            return id_path.read_text(encoding="utf-8").strip()
        device_id = str(uuid.uuid4())
        self._state_dir.mkdir(parents=True, exist_ok=True)
        id_path.write_text(device_id, encoding="utf-8")
        return device_id

    def _load_or_create_key(self) -> Ed25519PrivateKey:
        if self._key_path.exists():
            raw = base64.b64decode(self._key_path.read_text(encoding="utf-8").strip())
            return Ed25519PrivateKey.from_private_bytes(raw)
        key = Ed25519PrivateKey.generate()
        self._state_dir.mkdir(parents=True, exist_ok=True)
        raw = key.private_bytes(Encoding.Raw, PrivateFormat.Raw, NoEncryption())
        self._key_path.write_text(base64.b64encode(raw).decode("ascii"), encoding="utf-8")
        os.chmod(self._key_path, 0o600)
        return key

    def public_key_base64(self) -> str:
        public_key: Ed25519PublicKey = self._private_key.public_key()
        raw = public_key.public_bytes(Encoding.Raw, PublicFormat.Raw)
        return base64.b64encode(raw).decode("ascii")

    def sign(self, message: bytes) -> str:
        return base64.b64encode(self._private_key.sign(message)).decode("ascii")
