import base64
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
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


def test_approval_and_revocation_survive_restart(tmp_path):
    path = tmp_path / "pairing.json"
    private = Ed25519PrivateKey.generate()
    first = PairingStore(path)
    challenge = first.create_challenge(identity(private), now=1000)
    signature = private.sign(base64.b64decode(challenge.nonceBase64))
    assert first.verify_proof(challenge.challengeId, base64.b64encode(signature).decode(), now=1001)

    # Owner approval may be issued by a separate CLI process.
    second = PairingStore(path)
    assert second.owner_approve(challenge.challengeId, now=1002)
    assert PairingStore(path).approved("phone-1") is not None

    assert PairingStore(path).revoke("phone-1")
    assert PairingStore(path).approved("phone-1") is None


def test_owner_scopes_are_persisted_and_not_client_self_asserted(tmp_path):
    path = tmp_path / "pairing.json"
    private = Ed25519PrivateKey.generate()
    store = PairingStore(path)
    challenge = store.create_challenge(identity(private), now=1000)
    signature = private.sign(base64.b64decode(challenge.nonceBase64))
    assert store.verify_proof(challenge.challengeId, base64.b64encode(signature).decode(), now=1001)
    trusted = store.owner_approve(
        challenge.challengeId,
        now=1002,
        granted_scopes=("HEALTH", "PRESENCE_READ", "INFERENCE"),
    )
    assert trusted is not None
    reloaded = PairingStore(path).approved("phone-1")
    assert reloaded is not None
    assert reloaded.granted_scopes == ("HEALTH", "INFERENCE", "PRESENCE_READ")


def test_p256_android_style_pairing_proof_is_verified():
    private = ec.generate_private_key(ec.SECP256R1())
    public_der = private.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
    android = DeviceIdentity(
        "android-1",
        base64.b64encode(public_der).decode(),
        "Android",
        "ANDROID_PHONE",
        "0.5.0",
        keyAlgorithm="P256_SHA256",
        publicKeyEncoding="X509",
    )
    store = PairingStore()
    challenge = store.create_challenge(android, now=1000)
    signature = private.sign(base64.b64decode(challenge.nonceBase64), ec.ECDSA(hashes.SHA256()))
    assert store.verify_proof(
        challenge.challengeId,
        base64.b64encode(signature).decode(),
        now=1001,
    )
    assert store.owner_approve(challenge.challengeId, now=1002) is not None
