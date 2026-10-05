# Presence Mesh — RFC

**Status:** Accepted — transport decision made, first real slice implemented and
CI-verified for compilation on both modules; **not verified against a real paired Wear
OS device or emulator**, since none exists in this execution environment (see §4).
**Author's note:** The prior session investigated this feature (Feature 8) thoroughly
and recorded a deliberate NOT ATTEMPTED decision (`docs/HISTORY.md`, "September 28
(continued) — Presence Mesh (Feature 8) investigated, deliberately not built this
round"), reasoning that a device-identity primitive with no real transport "isn't a
smaller honest version of multi-device continuity, it's a different, much less
meaningful feature wearing its name." That reasoning is correct and this RFC doesn't
relitigate it — it still rejects shipping a presence *model* with no real transport
underneath it. What changed this round is narrower: this RFC names the one established
pattern already used throughout this codebase (`ai/mesh/ModelMesh.kt`'s
`CloudCompletionProvider`, `automation/uaf/ActionAdapter.kt`'s `ActionAdapter`,
`trust/lineage/LineageRecorder`) for exactly this situation — a real external-system
integration whose pure logic is fully unit-testable and whose actual OS-level call
cannot be, verified instead by matching Google's documented API contract and by this
project's own CI compiling both the phone and watch modules against it.

## 1. What hasn't changed

Re-checked the prior investigation's blocking finding before writing anything: there is
still no `adb`, `avdmanager`, `emulator`, or any Android SDK tooling in this execution
environment, and no real Wear OS device or emulator pairing is possible here. Nothing
about that constraint has changed. This RFC does not claim otherwise, and nothing it
recommends depends on that changing.

## 2. The transport decision (the prior investigation's named step 1)

The Wearable Data Layer API (`com.google.android.gms:play-services-wearable`,
`DataClient`) — the standard, officially documented mechanism for a phone app to push
data to its companion Wear OS app, and the one the existing `:wear` module's own code
comments already named as the obvious next step. No alternative was seriously
considered: Bluetooth/Nearby Connections/a custom WebSocket would all be reinventing a
mechanism Google already provides and Wear OS already expects apps to use, with none of
this project's own entities (`memory/MemoryStore.kt`) needing anything DataClient can't
express (a small, infrequently-updated key-value-shaped payload is exactly what a
`DataItem` is for).

## 3. What this round builds, and why each piece is honest

**Pure, fully unit-tested (`app/src/main/java/com/nua/assistant/presence/PresenceSnapshot.kt`):**
`PresenceSnapshot` (`deviceId`, `deviceType`: PHONE/WATCH, `lastActiveAt`) and
`mostRecentlyActive(snapshots)` — the simplest honest merge rule: whichever device most
recently reported activity is the one presence currently favors. No interval/expiry
model yet (a snapshot doesn't "go stale" on its own) — that's a real next slice once a
UI actually displays "last seen," not invented speculatively now.

**A real interface + real implementation, same inversion every other external-system
integration in this codebase already uses
(`presence/PresenceTransport.kt`/`presence/WearableDataClientPresenceTransport.kt`):**
`PresenceTransport.publish(snapshot)` is backed by a real `Wearable.getDataClient(context)`
`putDataItem` call — the actual documented mechanism, not a stand-in. Unit tests exercise
`PresenceRepository`'s logic (device-id persistence, snapshot construction) against a
fake `PresenceTransport`, exactly how `ModelMeshTest`-style tests fake
`CloudCompletionProvider` rather than hitting a real network call. What those tests
*cannot* verify — and this RFC states so explicitly rather than implying otherwise — is
that `WearableDataClientPresenceTransport` correctly round-trips through a real paired
watch. That one piece is verified only by matching Google's documented `DataClient` API
contract and by this project's CI compiling it, same honesty standard this session
already applied to `NuaAccessibilityService.performSystemNavigation`'s connected-service
path.

**The `:wear` module's `NuaTileService` becomes the first real reader
(`wear/src/main/java/com/nua/assistant/wear/NuaTileService.kt`):** reads the phone's last
published presence via the same `DataClient`, rendering it instead of the fixed text its
own doc comment already named as the missing piece — falling back to the original
static text when no data item exists yet (first install, phone never opened, or — the
case that matters most in this environment — no real transport round trip ever actually
occurred). Never crashes either way.

## 4. What remains genuinely unverified, named plainly

- Whether `putDataItem`/the watch-side `DataClient` read actually round-trip on real
  paired hardware. This is the one thing a future owner with access to a real device (or
  the Wear OS emulator, which needs a local Android Studio install this sandboxed
  environment doesn't have) must confirm before this is called DONE rather than
  IMPLEMENTED-BUT-UNVERIFIED.
- A continuous/periodic presence heartbeat (a WorkManager job republishing presence on
  an interval). This round publishes only once, when `NuaViewModel` initializes (the
  phone app being opened) — a real event, not a fabricated one, but a single point-in-
  time signal rather than live presence tracking. A real next slice, not attempted here
  to keep this round reviewable.
- Device revocation / multi-watch pairing / presence expiry — all named in the prior
  investigation's Guardian Lab pass as requiring this subsystem to exist first; it does
  now, in this limited form, but none of those are built.
