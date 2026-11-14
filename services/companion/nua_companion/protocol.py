from __future__ import annotations
from dataclasses import dataclass, asdict
from enum import Enum
from typing import Any

PROTOCOL_VERSION = 1

class TrustState(str, Enum):
    PENDING = "PENDING"
    APPROVED = "APPROVED"
    REVOKED = "REVOKED"
    EXPIRED = "EXPIRED"

@dataclass(frozen=True)
class DeviceIdentity:
    deviceId: str
    publicKey: str
    ownerLabel: str
    platform: str
    appVersion: str
    keyAlgorithm: str = "ED25519"
    publicKeyEncoding: str = "RAW"
    protocolVersion: int = PROTOCOL_VERSION

@dataclass(frozen=True)
class PairingChallenge:
    challengeId: str
    challenger: DeviceIdentity
    nonceBase64: str
    expiresAtMillis: int

@dataclass(frozen=True)
class PresenceState:
    identity: DeviceIdentity
    trustState: TrustState
    capabilities: list[dict[str, Any]]
    models: list[dict[str, Any]]
    connectors: list[dict[str, Any]]
    lastVerifiedAtMillis: int

def to_json_dict(value: Any) -> dict[str, Any]:
    data = asdict(value)
    if "trustState" in data and isinstance(data["trustState"], TrustState):
        data["trustState"] = data["trustState"].value
    return data
