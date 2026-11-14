# NUA Sovereign Client — Windows

A thin launcher around the shared client package at
`shared/protocol-consumers/python` (`nua_client`), same as the Linux and
macOS launchers. This directory only adds the Windows entry point.

## Status: SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED

The `nua_client` package itself is real and covered by a real integration
test against the actual companion service (see
`shared/protocol-consumers/python/tests/test_pairing_client.py`), run and
passing on Linux/Python in this environment. `nua-client.ps1` was not run on
real Windows — there is no Windows machine available in this environment to
verify PowerShell execution, `python`/`py` launcher resolution, or file
permission behavior against.

## Usage (PowerShell)

```powershell
.\nua-client.ps1 pair
.\nua-client.ps1 status
.\nua-client.ps1 ask "hello"
.\nua-client.ps1 tray   # requires: pip install -e ..\..\shared\protocol-consumers\python[tray]
```

Requires Python 3.11+ on `PATH` (or set `$env:NUA_CLIENT_PYTHON` to a
specific interpreter, e.g. `py`). Pairing requires separate owner approval
on the companion node (`nua-companion approve <challenge-id>`) — this
launcher cannot approve itself; see
`services/companion/nua_companion/pairing.py`.

## DEFERRED_PLATFORM_LIMITATION: packaged `.exe` / tray

A packaged, double-clickable `.exe` (e.g. via PyInstaller) with a native
Windows system-tray icon was not built. This requires a real Windows build
host to produce and smoke-test a Windows binary, which is not available in
this environment; a claim of a working `.exe` without that check would not
be honest. The script above runs today on any Windows host with Python
3.11+ installed; only the native packaging step is deferred.
