from __future__ import annotations
import base64
import json
import os
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from .models import configured_models, complete
from .mounts import MountRegistry
from .sandbox import WasmJob, WasmSandboxLimits, run_wasm
from .pairing import PairingStore
from .protocol import DeviceIdentity, PROTOCOL_VERSION, to_json_dict
from .security import (
    ReplayWindow,
    compact_json,
    digest_request,
    load_or_create_identity_key,
    now_ms,
    public_key_b64,
    sign_b64,
    verify_device_signature,
)

MAX_FILE_READ_BYTES = 1 * 1024 * 1024

class CompanionState:
    def __init__(self) -> None:
        state_path = Path(os.getenv("NUA_COMPANION_STATE", "~/.nua/companion-pairing.json")).expanduser()
        key_path = Path(os.getenv("NUA_COMPANION_IDENTITY_KEY", "~/.nua/companion-identity.key")).expanduser()
        self.identity_key = load_or_create_identity_key(key_path)
        self.pairing = PairingStore(state_path)
        self.replay = ReplayWindow()
        self.mounts = MountRegistry.from_env()
        self.device = DeviceIdentity(
            deviceId=os.getenv("NUA_COMPANION_DEVICE_ID", "companion-local"),
            publicKey=public_key_b64(self.identity_key),
            ownerLabel=os.getenv("NUA_COMPANION_LABEL", "NUA Companion"),
            platform=os.getenv("NUA_COMPANION_PLATFORM", "UNKNOWN"),
            appVersion="0.1.0",
        )

STATE = CompanionState()

class Handler(BaseHTTPRequestHandler):
    server_version = "NUA-Sovereign-Companion/0.1"

    def _json(self, status: int, body: object) -> None:
        payload = json.dumps(body, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _read(self) -> dict:
        length = int(self.headers.get("content-length", "0"))
        return json.loads(self.rfile.read(length) or b"{}")

    def do_GET(self) -> None:
        if self.path == "/health":
            self._json(200, {"protocolVersion": PROTOCOL_VERSION, "appVersion": "0.1.0", "deviceId": STATE.device.deviceId, "ready": True})
            return
        if self.path == "/identity":
            nonce = self.headers.get("x-nua-challenge", "").encode("utf-8")
            proof = sign_b64(STATE.identity_key, nonce) if nonce else None
            self._json(200, {
                "identity": to_json_dict(STATE.device),
                "challengeSignatureBase64": proof,
            })
            return
        if self.path == "/presence":
            self._json(200, {
                "identity": to_json_dict(STATE.device),
                "trustState": "APPROVED",
                "capabilities": [
                    {"id": "compute.inference", "version": 1, "readOnly": True, "requiresOwnerApproval": False},
                ],
                "models": [
                    {"id": m.id, "provider": m.provider, "localToNode": True, "supportsVision": False, "supportsStructuredOutput": False}
                    for m in configured_models()
                ],
                "connectors": (
                    [{"id": "companion-files", "version": "1", "readCapabilities": ["files.read"], "writeCapabilities": []}]
                    if STATE.mounts.names()
                    else []
                ),
                "lastVerifiedAtMillis": now_ms(),
            })
            return
        self._json(404, {"error": "not_found"})

    def do_POST(self) -> None:
        body = self._read()
        if self.path == "/pair/challenge":
            try:
                identity = DeviceIdentity(**body["identity"])
                challenge = STATE.pairing.create_challenge(identity)
                self._json(200, to_json_dict(challenge))
            except Exception:
                self._json(400, {"error": "invalid_identity"})
            return

        if self.path == "/pair/prove":
            ok = STATE.pairing.verify_proof(str(body.get("challengeId", "")), str(body.get("signatureBase64", "")))
            self._json(200 if ok else 403, {"verified": ok, "ownerApprovalRequired": ok})
            return

        if self.path == "/inference":
            auth = self._authorize(body, "INFERENCE")
            if auth is not None:
                self._json(403, {"error": auth})
                return
            # Read modelId/prompt from the signed `payload`, never from unsigned
            # top-level fields: _authorize already checked payload against the
            # exact signed payloadJson string, so acting on anything else here
            # would reopen that gap.
            payload = body.get("payload") or {}
            models = configured_models()
            requested = str(payload.get("modelId", ""))
            endpoint = next((m for m in models if m.id == requested), models[0] if models else None)
            if endpoint is None:
                self._json(503, {"error": "no_local_model_configured"})
                return
            try:
                text = complete(endpoint, str(payload.get("prompt", "")))
                self._json(200, {"succeeded": True, "text": text, "provider": endpoint.provider})
            except Exception:
                self._json(502, {"succeeded": False, "errorCode": "MODEL_FAILURE"})
            return

        if self.path == "/sandbox":
            auth = self._authorize(body, "SANDBOX")
            if auth is not None:
                self._json(403, {"error": auth})
                return
            payload = body.get("payload") or {}
            try:
                program = base64.b64decode(str(payload["programBase64"]))
                limits = WasmSandboxLimits(
                    fuel=int(payload.get("fuel", 2_000_000)),
                    memory_bytes=int(payload.get("memoryBytes", 32 * 1024 * 1024)),
                    wall_time_ms=int(payload.get("wallTimeMillis", 1_000)),
                )
                result = run_wasm(
                    WasmJob(
                        program=program,
                        expected_sha256=str(payload["programDigest"]),
                        export=str(payload.get("export", "run")),
                        input_value=payload.get("inputValue"),
                    ),
                    limits,
                )
                self._json(200 if result.succeeded else 422, {
                    "succeeded": result.succeeded,
                    "result": result.result,
                    "errorCode": result.error_code,
                    "fuelRemaining": result.fuel_remaining,
                })
            except (KeyError, TypeError, ValueError):
                self._json(400, {"error": "invalid_sandbox_request"})
            return

        if self.path == "/files/list":
            # Reuses CONNECTORS_READ (the canonical scope for connector reads --
            # see LocalApiScope) rather than a new FILES_READ scope, so this
            # doesn't fork the scope taxonomy cli.py's --scope choices and the
            # Kotlin LocalApiContract already agree on.
            auth = self._authorize(body, "CONNECTORS_READ")
            if auth is not None:
                self._json(403, {"error": auth})
                return
            payload = body.get("payload") or {}
            mount = str(payload.get("mount", ""))
            relative_path = str(payload.get("path", ""))
            entries = STATE.mounts.list_entries(mount, relative_path)
            if entries is None:
                self._json(404, {"error": "mount_or_path_not_found"})
                return
            self._json(200, {"succeeded": True, "entries": entries})
            return

        if self.path == "/files/read":
            auth = self._authorize(body, "CONNECTORS_READ")
            if auth is not None:
                self._json(403, {"error": auth})
                return
            payload = body.get("payload") or {}
            mount = str(payload.get("mount", ""))
            relative_path = str(payload.get("path", ""))
            resolved = STATE.mounts.resolve(mount, relative_path)
            if resolved is None or not resolved.is_file():
                self._json(404, {"error": "mount_or_path_not_found"})
                return
            try:
                size = resolved.stat().st_size
                if size > MAX_FILE_READ_BYTES:
                    self._json(413, {"error": "file_too_large"})
                    return
                data = resolved.read_bytes()
            except OSError:
                self._json(502, {"succeeded": False, "errorCode": "READ_FAILURE"})
                return
            self._json(200, {"succeeded": True, "contentBase64": base64.b64encode(data).decode("ascii"), "sizeBytes": size})
            return

        self._json(404, {"error": "not_found"})

    def _authorize(self, body: dict, required_scope: str) -> str | None:
        meta = body.get("meta") or {}
        client_id = str(meta.get("clientId", ""))
        trusted = STATE.pairing.approved(client_id)
        if trusted is None:
            return "unpaired_or_revoked"
        if required_scope not in set(trusted.granted_scopes):
            return "scope_missing"
        protocol = int(meta.get("protocolVersion", 0))
        if protocol != PROTOCOL_VERSION:
            return "version_mismatch"
        nonce = str(meta.get("nonce", ""))
        timestamp = int(meta.get("timestampMillis", 0))
        if not STATE.replay.accept(nonce, timestamp):
            return "replay_or_clock_skew"
        operation = str(body.get("operation", ""))
        payload = body.get("payload", {})
        payload_json = str(body.get("payloadJson", ""))
        if not payload_json:
            return "signed_payload_missing"
        try:
            signed_payload = json.loads(payload_json)
        except (TypeError, ValueError, json.JSONDecodeError):
            return "signed_payload_invalid"
        if signed_payload != payload:
            return "signed_payload_mismatch"
        digest = digest_request(
            protocol,
            str(meta.get("requestId", "")),
            client_id,
            nonce,
            timestamp,
            operation,
            payload_json,
        )
        if not verify_device_signature(
            trusted.identity.keyAlgorithm,
            trusted.identity.publicKeyEncoding,
            trusted.identity.publicKey,
            str(meta.get("signatureBase64", "")),
            digest,
        ):
            return "bad_signature"
        return None

def serve(
    host: str = "127.0.0.1",
    port: int = 8765,
    tls_cert: str | None = None,
    tls_key: str | None = None,
) -> ThreadingHTTPServer:
    loopback = host in {"127.0.0.1", "localhost", "::1"}
    if not loopback and (not tls_cert or not tls_key):
        raise ValueError("non-loopback companion serving requires TLS certificate and key")
    server = ThreadingHTTPServer((host, port), Handler)
    if tls_cert and tls_key:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(tls_cert, tls_key)
        server.socket = context.wrap_socket(server.socket, server_side=True)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server
