"""Authenticated client calls against a paired companion node.

Mirrors services/companion/nua_companion/service.py's Handler._authorize
exactly: every privileged call carries a `meta` envelope (protocol version,
request id, this device's id, a fresh nonce, a timestamp, and a signature
over the canonical request) plus the `scopes` the operation needs. Unscoped
reads (/health, /presence) need no signature at all, matching the companion's
own unauthenticated GET handlers.
"""
from __future__ import annotations

import base64
import uuid
from dataclasses import dataclass

from .identity import ClientIdentity
from .security import compact_json, digest_request, new_nonce, now_ms
from .transport import get_json, post_json

INFERENCE_OPERATION = "inference.start"
INFERENCE_SCOPE = "INFERENCE"
FILES_LIST_OPERATION = "files.list"
FILES_READ_OPERATION = "files.read"
# Reuses CONNECTORS_READ (the canonical scope for connector reads) rather than
# a one-off FILES_READ scope, matching cli.py's --scope choices and the
# Kotlin LocalApiScope enum.
FILES_READ_SCOPE = "CONNECTORS_READ"


@dataclass(frozen=True)
class InferenceResult:
    succeeded: bool
    text: str | None = None
    provider: str | None = None
    error_code: str | None = None


@dataclass(frozen=True)
class FileEntry:
    name: str
    is_dir: bool
    size_bytes: int


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
        body = self._build_signed_body(INFERENCE_OPERATION, [INFERENCE_SCOPE], payload)
        response = post_json(self._base_url, "/inference", body)
        if not response.get("succeeded", False):
            return InferenceResult(succeeded=False, error_code=response.get("error") or response.get("errorCode"))
        return InferenceResult(succeeded=True, text=response.get("text"), provider=response.get("provider"))

    def files_list(self, mount: str, path: str = "") -> list[FileEntry]:
        """Lists the entries directly inside `path` (relative to the named
        mount's root) on a paired, approved companion. Only ever names a
        mount by the owner's own pre-configured alias -- never a raw
        filesystem path -- so this can't be used to probe the companion's
        wider filesystem."""
        payload = {"mount": mount, "path": path}
        body = self._build_signed_body(FILES_LIST_OPERATION, [FILES_READ_SCOPE], payload)
        response = post_json(self._base_url, "/files/list", body)
        return [
            FileEntry(name=entry["name"], is_dir=entry["isDir"], size_bytes=entry["sizeBytes"])
            for entry in response.get("entries", [])
        ]

    def files_read(self, mount: str, path: str) -> bytes:
        """Reads one file's contents (size-capped on the companion side) from
        a named mount."""
        payload = {"mount": mount, "path": path}
        body = self._build_signed_body(FILES_READ_OPERATION, [FILES_READ_SCOPE], payload)
        response = post_json(self._base_url, "/files/read", body)
        return base64.b64decode(response["contentBase64"])

    def _build_signed_body(self, operation: str, scopes: list[str], payload: dict) -> dict:
        """Builds the full signed request envelope: `meta` (signed over the
        canonical request), plus the exact `payloadJson` string that was
        signed alongside the raw `payload` dict -- the companion's
        `_authorize` checks both agree, rather than trusting its own
        recomputation of compact_json(payload) to match what was signed."""
        protocol_version = self._identity.identity.protocolVersion
        request_id = str(uuid.uuid4())
        client_id = self._identity.identity.deviceId
        nonce = new_nonce()
        timestamp_ms = now_ms()
        payload_json = compact_json(payload)
        digest = digest_request(protocol_version, request_id, client_id, nonce, timestamp_ms, operation, payload_json)
        signature = self._identity.sign(digest)
        meta = {
            "protocolVersion": protocol_version,
            "requestId": request_id,
            "clientId": client_id,
            "nonce": nonce,
            "timestampMillis": timestamp_ms,
            "signatureBase64": signature,
        }
        return {
            "meta": meta,
            "scopes": scopes,
            "operation": operation,
            "payload": payload,
            "payloadJson": payload_json,
        }
