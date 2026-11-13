from __future__ import annotations
import base64
import secrets
from dataclasses import dataclass
from .protocol import DeviceIdentity, PairingChallenge, TrustState
from .security import now_ms, new_nonce, verify_ed25519

PAIRING_TTL_MS = 5 * 60 * 1000

@dataclass
class PendingPairing:
    challenge: PairingChallenge
    proof_verified: bool = False

@dataclass
class TrustedDevice:
    identity: DeviceIdentity
    state: TrustState
    approved_at_ms: int

class PairingStore:
    def __init__(self) -> None:
        self.pending: dict[str, PendingPairing] = {}
        self.trusted: dict[str, TrustedDevice] = {}

    def create_challenge(self, identity: DeviceIdentity, now: int | None = None) -> PairingChallenge:
        now = now if now is not None else now_ms()
        challenge = PairingChallenge(
            challengeId=secrets.token_hex(16),
            challenger=identity,
            nonceBase64=new_nonce(),
            expiresAtMillis=now + PAIRING_TTL_MS,
        )
        self.pending[challenge.challengeId] = PendingPairing(challenge)
        return challenge

    def verify_proof(self, challenge_id: str, signature_b64: str, now: int | None = None) -> bool:
        now = now if now is not None else now_ms()
        pending = self.pending.get(challenge_id)
        if not pending or now >= pending.challenge.expiresAtMillis:
            return False
        message = base64.b64decode(pending.challenge.nonceBase64)
        ok = verify_ed25519(pending.challenge.challenger.publicKey, signature_b64, message)
        pending.proof_verified = ok
        return ok

    def owner_approve(self, challenge_id: str, now: int | None = None) -> TrustedDevice | None:
        now = now if now is not None else now_ms()
        pending = self.pending.get(challenge_id)
        if not pending or not pending.proof_verified or now >= pending.challenge.expiresAtMillis:
            return None
        trusted = TrustedDevice(pending.challenge.challenger, TrustState.APPROVED, now)
        self.trusted[trusted.identity.deviceId] = trusted
        self.pending.pop(challenge_id, None)
        return trusted

    def revoke(self, device_id: str) -> bool:
        trusted = self.trusted.get(device_id)
        if not trusted:
            return False
        trusted.state = TrustState.REVOKED
        return True

    def approved(self, device_id: str) -> TrustedDevice | None:
        item = self.trusted.get(device_id)
        return item if item and item.state == TrustState.APPROVED else None
