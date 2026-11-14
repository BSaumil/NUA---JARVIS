# NUA Sovereign Client — iOS/iPadOS

## Status: SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED

A native SwiftUI control-surface shell now lives in `clients/ios/NuaSovereign/`.

GitHub Actions generates the Xcode project with XcodeGen and builds the app for the iOS
Simulator with code signing disabled. The resulting `.app` is uploaded as the
`nua-sovereign-ios-simulator-app` workflow artifact.

### Implemented

- SwiftUI application shell.
- Local companion health and Presence views.
- Shared Presence/Local API wire models.
- Keychain-backed secure storage helper.
- Local-network usage declaration.
- Fail-closed UI when the companion is unavailable.
- Read-only health/presence surface; privileged operations are not exposed by this shell.

### Not yet claimed

- No App Store/TestFlight signing or provisioning.
- No physical iPhone/iPad device validation.
- No background execution beyond what iOS permits.
- No privileged pairing/job execution from the current SwiftUI shell.
- No claim of Android-equivalent device control on iOS.

The iOS client intentionally starts as a constrained Presence/control surface. Future
privileged operations must consume the same owner-approved pairing/scoped Local API used
by the companion service; they must not introduce a second trust protocol.
