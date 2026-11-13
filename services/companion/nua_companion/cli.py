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
    approve = sub.add_parser("approve")
    approve.add_argument("challenge_id")
    revoke = sub.add_parser("revoke")
    revoke.add_argument("device_id")
    args = parser.parse_args()

    if args.command == "approve":
        item = STATE.pairing.owner_approve(args.challenge_id)
        raise SystemExit(0 if item else 2)
    if args.command == "revoke":
        raise SystemExit(0 if STATE.pairing.revoke(args.device_id) else 2)

    server = serve(getattr(args, "host", "127.0.0.1"), getattr(args, "port", 8765))
    print(f"NUA Companion listening on {server.server_address[0]}:{server.server_address[1]}")
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        server.shutdown()

if __name__ == "__main__":
    main()
