from __future__ import annotations
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from .models import configured_models, complete
from .pairing import PairingStore
from .protocol import DeviceIdentity, PROTOCOL_VERSION, to_json_dict
from .security import ReplayWindow, compact_json, digest_request, now_ms, verify_ed25519

class CompanionState:
    def __init__(self) -> None:
        state_path = Path(os.getenv("NUA_COMPANION_STATE", "~/.nua/companion-pairing.json")).expanduser()
        self.pairing = PairingStore(state_path)
        self.replay = ReplayWindow()
        self.device = DeviceIdentity(
            deviceId=os.getenv("NUA_COMPANION_DEVICE_ID", "companion-local"),
            publicKey="",
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
                "connectors": [],
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
            models = configured_models()
            requested = str(body.get("modelId", ""))
            endpoint = next((m for m in models if m.id == requested), models[0] if models else None)
            if endpoint is None:
                self._json(503, {"error": "no_local_model_configured"})
                return
            try:
                text = complete(endpoint, str(body.get("prompt", "")))
                self._json(200, {"succeeded": True, "text": text, "provider": endpoint.provider})
            except Exception:
                self._json(502, {"succeeded": False, "errorCode": "MODEL_FAILURE"})
            return

        self._json(404, {"error": "not_found"})

    def _authorize(self, body: dict, required_scope: str) -> str | None:
        meta = body.get("meta") or {}
        client_id = str(meta.get("clientId", ""))
        trusted = STATE.pairing.approved(client_id)
        if trusted is None:
            return "unpaired_or_revoked"
        if required_scope not in set(body.get("scopes", [])):
            return "scope_missing"
        protocol = int(meta.get("protocolVersion", 0))
        if protocol != PROTOCOL_VERSION:
            return "version_mismatch"
        nonce = str(meta.get("nonce", ""))
        timestamp = int(meta.get("timestampMillis", 0))
        if not STATE.replay.accept(nonce, timestamp):
            return "replay_or_clock_skew"
        operation = str(body.get("operation", ""))
        payload_json = compact_json(body.get("payload", {}))
        digest = digest_request(protocol, str(meta.get("requestId", "")), client_id, nonce, timestamp, operation, payload_json)
        if not verify_ed25519(trusted.identity.publicKey, str(meta.get("signatureBase64", "")), digest):
            return "bad_signature"
        return None

def serve(host: str = "127.0.0.1", port: int = 8765) -> ThreadingHTTPServer:
    server = ThreadingHTTPServer((host, port), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server
