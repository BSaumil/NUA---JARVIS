"""Minimal stdlib-only HTTP transport to the companion's local HTTP API.
No third-party HTTP dependency is needed for this small, fixed set of calls.
"""
from __future__ import annotations

import json
import urllib.error
import urllib.request


class CompanionUnreachable(Exception):
    """The companion node could not be reached at all (connection refused/timeout)."""


class CompanionRequestFailed(Exception):
    """The companion node responded, but with a non-2xx status."""

    def __init__(self, status: int, body: dict) -> None:
        super().__init__(f"companion returned HTTP {status}: {body}")
        self.status = status
        self.body = body


def get_json(base_url: str, path: str, timeout: float = 10.0) -> dict:
    return _request(base_url, path, method="GET", body=None, timeout=timeout)


def post_json(base_url: str, path: str, body: dict, timeout: float = 10.0) -> dict:
    return _request(base_url, path, method="POST", body=body, timeout=timeout)


def _request(base_url: str, path: str, method: str, body: dict | None, timeout: float) -> dict:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=data,
        method=method,
        headers={"content-type": "application/json"} if data is not None else {},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read()
        parsed = json.loads(raw) if raw else {}
        raise CompanionRequestFailed(error.code, parsed) from error
    except (urllib.error.URLError, ConnectionError, TimeoutError) as error:
        raise CompanionUnreachable(str(error)) from error
