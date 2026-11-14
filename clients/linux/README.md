# NUA Sovereign Client — Linux

A thin launcher around the shared client package at
`shared/protocol-consumers/python` (`nua_client`). All pairing, signing, and
HTTP logic lives there and is shared with the Windows and macOS launchers;
this directory only adds the Linux entry point.

## Status: SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED

The underlying `nua_client` package is real, dependency-light (stdlib HTTP +
`cryptography` for Ed25519), and verified in this repository by a real
integration test (`shared/protocol-consumers/python/tests/test_pairing_client.py`)
that spins up the actual companion service and performs a real pairing +
signed-request round trip — see that package's test suite for what is
actually proven. Running `nua-client.sh` itself was not exercised on a real
Linux desktop in this environment.

## Usage

```sh
./nua-client.sh pair               # request pairing with a companion node
./nua-client.sh status             # show this device's identity + companion health/presence
./nua-client.sh ask "hello"         # send a prompt to the companion's configured local model
./nua-client.sh tray                # run the system-tray icon (requires: pip install -e ../../shared/protocol-consumers/python[tray])
```

By default this talks to a companion at `http://127.0.0.1:8765`; pass
`--base-url` to point at a companion on another host on your network.

Pairing is two-step and **not** self-approving: `pair` proves this device
controls its own key, but the companion's owner must separately run
`nua-companion approve <challenge-id>` on the companion node before this
device's signed requests are authorized. That is intentional — see
`services/companion/nua_companion/pairing.py`.

## DEFERRED_PLATFORM_LIMITATION: packaged binary

A single-file AppImage/packaged binary (e.g. via PyInstaller) was not built:
this sandbox has no desktop Linux session to build or smoke-test a packaged
GUI binary against, and a fabricated "it works" claim without that check
would be worse than shipping the script directly. The script above runs
today on any Linux host with Python 3.11+ and `pip install -e
shared/protocol-consumers/python[tray]`.
