# NUA Sovereign Client — Windows

## Status: SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED

The Windows surface now has two layers:

1. a Tauri 2 native shell under `clients/windows/src-tauri/`;
2. the shared Python companion client/CLI under `shared/protocol-consumers/python`.

GitHub Actions runs on `windows-latest`, performs `cargo check`, executes the shell
policy tests, builds the release executable, and uploads
`nua-sovereign-windows-exe`.

### Current Tauri security envelope

The native shell is deliberately read-only and localhost-only:

- accepts only `http://127.0.0.1:8765` / localhost;
- refuses remote HTTP endpoints;
- refuses unexpected ports;
- exposes only `health` and `presence`;
- does not proxy arbitrary URLs or arbitrary companion paths;
- CSP restricts network connections to self.

Privileged inference, connectors, sandbox jobs and cross-device actions remain behind the
paired/scoped companion APIs rather than the thin desktop shell.

### Shared CLI

With Python 3.11+ installed:

```powershell
.\nua-client.ps1 pair
.\nua-client.ps1 status
.\nua-client.ps1 ask "hello"
.\nua-client.ps1 tray
```

Pairing requires separate owner approval on the companion node; the Windows client cannot
approve itself.

### Not yet claimed

- The CI-produced executable is not a signed MSIX/MSI installer.
- No Windows code-signing certificate is configured.
- No physical POS/desktop hardware validation is claimed.
- Native tray/updater packaging remains a later release-hardening step.
