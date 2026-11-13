from __future__ import annotations
import argparse
import os
import time
from .service import STATE, serve

def main() -> None:
    parser = argparse.ArgumentParser(prog="nua-companion")
    sub = parser.add_subparsers(dest="command", required=False)
    run = sub.add_parser("run")
    run.add_argument("--host", default=os.getenv("NUA_COMPANION_HOST", "127.0.0.1"))
    run.add_argument("--port", type=int, default=int(os.getenv("NUA_COMPANION_PORT", "8765")))
    run.add_argument("--tls-cert", default=os.getenv("NUA_COMPANION_TLS_CERT"))
    run.add_argument("--tls-key", default=os.getenv("NUA_COMPANION_TLS_KEY"))
    approve = sub.add_parser("approve")
    approve.add_argument("challenge_id")
    approve.add_argument(
        "--scope",
        action="append",
        default=["HEALTH", "PRESENCE_READ"],
        choices=["HEALTH", "PRESENCE_READ", "INFERENCE", "RESEARCH", "SANDBOX", "CONNECTORS_READ", "CONNECTORS_WRITE", "AUTHORIZATION"],
    )
    revoke = sub.add_parser("revoke")
    revoke.add_argument("device_id")
    args = parser.parse_args()

    if args.command == "approve":
        item = STATE.pairing.owner_approve(args.challenge_id, granted_scopes=tuple(args.scope))
        raise SystemExit(0 if item else 2)
    if args.command == "revoke":
        raise SystemExit(0 if STATE.pairing.revoke(args.device_id) else 2)

    server = serve(
        getattr(args, "host", "127.0.0.1"),
        getattr(args, "port", 8765),
        getattr(args, "tls_cert", None),
        getattr(args, "tls_key", None),
    )
    print(f"NUA Companion listening on {server.server_address[0]}:{server.server_address[1]}")
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        server.shutdown()

if __name__ == "__main__":
    main()
