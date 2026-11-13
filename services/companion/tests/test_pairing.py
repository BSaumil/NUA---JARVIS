import base64
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from nua_companion.pairing import PairingStore
from nua_companion.protocol import DeviceIdentity, TrustState

def identity(private: Ed25519PrivateKey) -> DeviceIdentity:
    public = private.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    return DeviceIdentity("phone-1", base64.b64encode(public).decode(), "Phone", "ANDROID_PHONE", "0.4.0")

def test_pairing_requires_key_proof_and_owner_approval():
    private = Ed25519PrivateKey.generate()
    store = PairingStore()
    challenge = store.create_challenge(identity(private), now=1000)
    signature = private.sign(base64.b64decode(challenge.nonceBase64))
    assert store.verify_proof(challenge.challengeId, base64.b64encode(signature).decode(), now=1001)
    assert store.approved("phone-1") is None
    approved = store.owner_approve(challenge.challengeId, now=1002)
    assert approved is not None
    assert approved.state == TrustState.APPROVED

def test_revocation_blocks_device():
    private = Ed25519PrivateKey.generate()
    store = PairingStore()
    challenge = store.create_challenge(identity(private), now=1000)
    signature = private.sign(base64.b64decode(challenge.nonceBase64))
    assert store.verify_proof(challenge.challengeId, base64.b64encode(signature).decode(), now=1001)
    assert store.owner_approve(challenge.challengeId, now=1002)
    assert store.revoke("phone-1")
    assert store.approved("phone-1") is None
