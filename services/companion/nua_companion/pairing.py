from __future__ import annotations

import json
import os
import secrets
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path

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
    granted_scopes: tuple[str, ...] = ()


class PairingStore:
    """Owner-approved device trust store.

    When a path is supplied, pending challenges and trusted/revoked devices survive
    service/CLI process restarts. No private key or bearer token is stored here; device
    public keys and approval state are persisted with owner-only file permissions.
    """

    def __init__(self, path: Path | None = None) -> None:
        self.path = path
        self.pending: dict[str, PendingPairing] = {}
        self.trusted: dict[str, TrustedDevice] = {}
        self._load()

    def create_challenge(self, identity: DeviceIdentity, now: int | None = None) -> PairingChallenge:
        now = now if now is not None else now_ms()
        challenge = PairingChallenge(
            challengeId=secrets.token_hex(16),
            challenger=identity,
            nonceBase64=new_nonce(),
            expiresAtMillis=now + PAIRING_TTL_MS,
        )
        self.pending[challenge.challengeId] = PendingPairing(challenge)
        self._save()
        return challenge

    def verify_proof(self, challenge_id: str, signature_b64: str, now: int | None = None) -> bool:
        now = now if now is not None else now_ms()
        pending = self.pending.get(challenge_id)
        if not pending or now >= pending.challenge.expiresAtMillis:
            return False
        import base64

        message = base64.b64decode(pending.challenge.nonceBase64)
        ok = verify_ed25519(pending.challenge.challenger.publicKey, signature_b64, message)
        pending.proof_verified = ok
        self._save()
        return ok

    def owner_approve(
        self,
        challenge_id: str,
        now: int | None = None,
        granted_scopes: tuple[str, ...] = ("HEALTH", "PRESENCE_READ"),
    ) -> TrustedDevice | None:
        now = now if now is not None else now_ms()
        pending = self.pending.get(challenge_id)
        if not pending or not pending.proof_verified or now >= pending.challenge.expiresAtMillis:
            return None
        scopes = tuple(sorted(set(granted_scopes)))
        trusted = TrustedDevice(pending.challenge.challenger, TrustState.APPROVED, now, scopes)
        self.trusted[trusted.identity.deviceId] = trusted
        self.pending.pop(challenge_id, None)
        self._save()
        return trusted

    def revoke(self, device_id: str) -> bool:
        trusted = self.trusted.get(device_id)
        if not trusted:
            return False
        trusted.state = TrustState.REVOKED
        self._save()
        return True

    def approved(self, device_id: str) -> TrustedDevice | None:
        item = self.trusted.get(device_id)
        return item if item and item.state == TrustState.APPROVED else None

    def _load(self) -> None:
        if self.path is None or not self.path.exists():
            return
        try:
            payload = json.loads(self.path.read_text(encoding="utf-8"))
            for item in payload.get("pending", []):
                raw = item["challenge"]
                identity = DeviceIdentity(**raw["challenger"])
                challenge = PairingChallenge(
                    challengeId=raw["challengeId"],
                    challenger=identity,
                    nonceBase64=raw["nonceBase64"],
                    expiresAtMillis=int(raw["expiresAtMillis"]),
                )
                self.pending[challenge.challengeId] = PendingPairing(
                    challenge=challenge,
                    proof_verified=bool(item.get("proof_verified", False)),
                )
            for item in payload.get("trusted", []):
                identity = DeviceIdentity(**item["identity"])
                trusted = TrustedDevice(
                    identity=identity,
                    state=TrustState(item["state"]),
                    approved_at_ms=int(item["approved_at_ms"]),
                    granted_scopes=tuple(item.get("granted_scopes", [])),
                )
                self.trusted[identity.deviceId] = trusted
        except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError):
            # Corrupt/unreadable trust state fails closed: no device is trusted.
            self.pending.clear()
            self.trusted.clear()

    def _save(self) -> None:
        if self.path is None:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "pending": [
                {
                    "challenge": asdict(item.challenge),
                    "proof_verified": item.proof_verified,
                }
                for item in self.pending.values()
            ],
            "trusted": [
                {
                    "identity": asdict(item.identity),
                    "state": item.state.value,
                    "approved_at_ms": item.approved_at_ms,
                    "granted_scopes": list(item.granted_scopes),
                }
                for item in self.trusted.values()
            ],
        }
        fd, tmp_name = tempfile.mkstemp(prefix=".nua-pairing-", dir=str(self.path.parent))
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(payload, handle, separators=(",", ":"), sort_keys=True)
            os.chmod(tmp_name, 0o600)
            os.replace(tmp_name, self.path)
        finally:
            if os.path.exists(tmp_name):
                os.unlink(tmp_name)
