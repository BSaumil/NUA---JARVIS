"""Command-line entry point for the NUA sovereign desktop client, shared by
the Windows/macOS/Linux launcher scripts under clients/<platform>/."""
from __future__ import annotations

import argparse
import json
import sys

from .api_client import ApiClient
from .identity import ClientIdentity
from .pairing_client import PairingClient
from .transport import CompanionRequestFailed, CompanionUnreachable

DEFAULT_BASE_URL = "http://127.0.0.1:8765"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="nua-client", description="NUA Sovereign Presence Mesh desktop client")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help=f"companion node base URL (default: {DEFAULT_BASE_URL})")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("pair", help="pair this device with the companion node (requires owner approval on that node)")
    subparsers.add_parser("status", help="show this device's identity and the companion node's health/presence")

    ask_parser = subparsers.add_parser("ask", help="send a prompt to the companion's configured local model")
    ask_parser.add_argument("prompt")
    ask_parser.add_argument("--model", default="", help="model id as advertised by the companion's /presence endpoint")

    subparsers.add_parser("tray", help="run the system-tray icon (requires the 'tray' extra)")

    args = parser.parse_args(argv)
    identity = ClientIdentity()

    try:
        if args.command == "pair":
            return _cmd_pair(args.base_url, identity)
        if args.command == "status":
            return _cmd_status(args.base_url, identity)
        if args.command == "ask":
            return _cmd_ask(args.base_url, identity, args.prompt, args.model)
        if args.command == "tray":
            return _cmd_tray(args.base_url)
    except CompanionUnreachable as error:
        print(f"companion unreachable at {args.base_url}: {error}", file=sys.stderr)
        return 2
    except CompanionRequestFailed as error:
        print(f"companion refused the request: {error}", file=sys.stderr)
        return 3

    return 1


def _cmd_pair(base_url: str, identity: ClientIdentity) -> int:
    pairing = PairingClient(base_url, identity)
    result = pairing.request_pairing()
    print(f"device id:     {identity.identity.deviceId}")
    print(f"challenge id:  {result.challenge_id}")
    print(f"signature ok:  {result.signature_verified}")
    if result.signature_verified:
        print(
            "Pairing proof accepted. This device is NOT yet trusted -- ask the "
            f"companion's owner to run: nua-companion approve {result.challenge_id}"
        )
    else:
        print("Pairing proof was rejected by the companion.", file=sys.stderr)
        return 1
    return 0


def _cmd_status(base_url: str, identity: ClientIdentity) -> int:
    api = ApiClient(base_url, identity)
    print(f"this device id: {identity.identity.deviceId}")
    print(f"this device key: {identity.public_key_base64()}")
    print("companion /health:", json.dumps(api.health()))
    print("companion /presence:", json.dumps(api.presence()))
    return 0


def _cmd_ask(base_url: str, identity: ClientIdentity, prompt: str, model: str) -> int:
    api = ApiClient(base_url, identity)
    result = api.inference(prompt, model_id=model)
    if not result.succeeded:
        print(f"inference failed: {result.error_code}", file=sys.stderr)
        return 1
    print(result.text)
    return 0


def _cmd_tray(base_url: str) -> int:
    try:
        from .tray import run_tray
    except ImportError:
        print("the system tray needs the 'tray' extra: pip install nua-sovereign-client[tray]", file=sys.stderr)
        return 1
    run_tray(base_url)
    return 0


if __name__ == "__main__":
    sys.exit(main())
