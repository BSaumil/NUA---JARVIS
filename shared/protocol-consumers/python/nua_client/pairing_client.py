"""Client side of the companion's pairing flow (POST /pair/challenge,
POST /pair/prove -- see services/companion/nua_companion/service.py and
pairing.py).

Proving control of the private key is NOT the same as being trusted: the
companion's PairingStore requires an explicit, separate owner_approve call on
the companion side (normally via `nua-companion approve`, run by the human who
owns that node) before this device's signed requests will be authorized. This
client cannot grant itself that approval -- it can only ask for it.
"""
from __future__ import annotations

from dataclasses import dataclass

from .identity import ClientIdentity
from .transport import post_json


@dataclass(frozen=True)
class PairingResult:
    challenge_id: str
    signature_verified: bool
    owner_approval_required: bool


class PairingClient:
    def __init__(self, base_url: str, identity: ClientIdentity) -> None:
        self._base_url = base_url
        self._identity = identity

    def request_pairing(self) -> PairingResult:
        """Requests a challenge, proves control of this device's private key, and
        returns the challenge id the owner must separately approve. Raises
        CompanionUnreachable / CompanionRequestFailed (see transport.py) on
        transport-level failure."""
        import base64

        challenge = post_json(
            self._base_url,
            "/pair/challenge",
            {"identity": self._identity.identity.to_dict()},
        )
        challenge_id = str(challenge["challengeId"])
        nonce = base64.b64decode(str(challenge["nonceBase64"]))
        signature = self._identity.sign(nonce)

        proof = post_json(
            self._base_url,
            "/pair/prove",
            {"challengeId": challenge_id, "signatureBase64": signature},
        )
        return PairingResult(
            challenge_id=challenge_id,
            signature_verified=bool(proof.get("verified", False)),
            owner_approval_required=bool(proof.get("ownerApprovalRequired", False)),
        )
