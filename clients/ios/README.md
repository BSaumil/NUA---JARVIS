# NUA Sovereign Client — iOS/iPadOS

## Status: DEFERRED_PLATFORM_LIMITATION

No iOS/iPadOS client code is included here. This sandbox has no macOS
machine, no Xcode, and no Apple provisioning — so Swift code written here
would be entirely unverifiable, not even to the level of compiling, and this
work is governed by a directive that explicitly forbids claiming "code may
be considered implemented" without at least a checked code boundary, and
forbids fabricating signing/provisioning. Writing unverified Swift and
calling it done would violate both.

## What a real implementation should do

To whoever picks this up with access to Xcode: implement the same wire
protocol the Windows/macOS/Linux client and `services/companion` already
implement and test (see `shared/protocol-consumers/python/nua_client/` for
the reference implementation and
`shared/protocol-consumers/python/tests/test_pairing_client.py` for the
protocol's own verification against the real companion service). Concretely:

- **Identity**: an Ed25519 keypair, generated with `Curve25519.Signing` from
  Apple's `CryptoKit` (interoperable with the `cryptography` library's
  Ed25519 used by both the companion and the desktop client — same curve,
  same raw 32-byte key encodings). Persist the private key in the iOS
  Keychain, never in a plist or app-group file. Public key is sent as
  base64 of the raw 32-byte public key, matching
  `Ed25519PublicKey.from_public_bytes` on the companion side.
- **Pairing**: `POST /pair/challenge` with `{"identity": {...}}`, sign the
  returned `nonceBase64` (base64-decoded first) with the private key, then
  `POST /pair/prove` with `{"challengeId", "signatureBase64"}`. Exactly
  mirrors `nua_client/pairing_client.py`. Owner approval
  (`nua-companion approve`) happens on the companion node, not on the phone.
- **Signed requests**: build the exact canonical string
  `"|".join([str(protocolVersion), requestId, clientId, nonce, str(timestampMillis), operation, payloadJson])`
  (UTF-8 encoded), SHA-256 it, sign the digest, and send it as
  `meta.signatureBase64` alongside `scopes`/`operation`/`payload`. See
  `nua_client/security.py` and `services/companion/nua_companion/security.py`
  — these two must stay byte-for-byte identical in their canonicalization,
  and a correct iOS client makes a third implementation of the same exact
  string, not a new format.
- **Transport**: `URLSession` against the companion's base URL (default
  `http://127.0.0.1:8765` only applies on the same machine; over a LAN this
  needs the companion's real address and, per the wider Presence Mesh
  design, should move to TLS rather than plain HTTP before any real network
  exposure — out of scope for this client alone).

No part of this should duplicate or redefine `PresenceProtocol.kt`'s or
`LocalApiContract.kt`'s semantics; it should consume the same wire shapes
`services/companion` and the other clients already implement.
