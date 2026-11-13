"""Authenticated client calls against a paired companion node.

Mirrors services/companion/nua_companion/service.py's Handler._authorize
exactly: every privileged call carries a `meta` envelope (protocol version,
request id, this device's id, a fresh nonce, a timestamp, and a signature
over the canonical request) plus the `scopes` the operation needs. Unscoped
reads (/health, /presence) need no signature at all, matching the companion's
own unauthenticated GET handlers.
"""
from __future__ import annotations

import uuid
from dataclasses import dataclass

from .identity import ClientIdentity
from .security import compact_json, digest_request, new_nonce, now_ms
from .transport import get_json, post_json

INFERENCE_OPERATION = "inference.start"
INFERENCE_SCOPE = "INFERENCE"


@dataclass(frozen=True)
class InferenceResult:
    succeeded: bool
    text: str | None = None
    provider: str | None = None
    error_code: str | None = None


class ApiClient:
    def __init__(self, base_url: str, identity: ClientIdentity) -> None:
        self._base_url = base_url
        self._identity = identity

    def health(self) -> dict:
        return get_json(self._base_url, "/health")

    def presence(self) -> dict:
        return get_json(self._base_url, "/presence")

    def inference(self, prompt: str, model_id: str = "") -> InferenceResult:
        payload = {"modelId": model_id, "prompt": prompt}
        meta = self._sign_meta(INFERENCE_OPERATION, payload)
        body = {
            "meta": meta,
            "scopes": [INFERENCE_SCOPE],
            "operation": INFERENCE_OPERATION,
            "payload": payload,
        }
        response = post_json(self._base_url, "/inference", body)
        if not response.get("succeeded", False):
            return InferenceResult(succeeded=False, error_code=response.get("error") or response.get("errorCode"))
        return InferenceResult(succeeded=True, text=response.get("text"), provider=response.get("provider"))

    def _sign_meta(self, operation: str, payload: dict) -> dict:
        protocol_version = self._identity.identity.protocolVersion
        request_id = str(uuid.uuid4())
        client_id = self._identity.identity.deviceId
        nonce = new_nonce()
        timestamp_ms = now_ms()
        payload_json = compact_json(payload)
        digest = digest_request(protocol_version, request_id, client_id, nonce, timestamp_ms, operation, payload_json)
        signature = self._identity.sign(digest)
        return {
            "protocolVersion": protocol_version,
            "requestId": request_id,
            "clientId": client_id,
            "nonce": nonce,
            "timestampMillis": timestamp_ms,
            "signatureBase64": signature,
        }
