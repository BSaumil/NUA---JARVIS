# NUA Sovereign Client — macOS

A thin launcher around the shared client package at
`shared/protocol-consumers/python` (`nua_client`), same as the Linux and
Windows launchers. This directory only adds the macOS entry point.

## Status: SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED

The `nua_client` package itself is real and covered by a real integration
test against the actual companion service (see
`shared/protocol-consumers/python/tests/test_pairing_client.py`). Neither
that script nor a macOS `.app` bundle was run on real macOS hardware in this
environment — there is no macOS machine available here to verify against.

## Usage

```sh
./nua-client.sh pair
./nua-client.sh status
./nua-client.sh ask "hello"
./nua-client.sh tray    # requires: pip install -e ../../shared/protocol-consumers/python[tray]
```

Pairing requires separate owner approval on the companion node
(`nua-companion approve <challenge-id>`) — this launcher cannot approve
itself; see `services/companion/nua_companion/pairing.py`.

## DEFERRED_PLATFORM_LIMITATION: `.app` bundle / menu-bar icon

A packaged `.app` (e.g. via `py2app`) with a native macOS menu-bar icon was
not built or signed. This requires a real macOS build host (and, for
distribution outside Gatekeeper's warnings, an Apple Developer signing
identity) that is not available in this environment, and per the directive
governing this work, external signing/provisioning is never fabricated. The
shared `nua_client` package already runs under any macOS Python 3.11+
install via the script above; only the native packaging/signing step is
deferred.
