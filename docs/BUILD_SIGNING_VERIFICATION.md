# Build & Signing Gate Verification

Task 3 of the 2026-10-02 personal-test deployment directive (§5): verify the
release build/signing gate before producing a personal-test APK. This is a
verification record, not a setup guide — the one-time keystore/secrets setup
itself was delivered to the repository owner out-of-band in an earlier turn
(a generated keystore plus setup instructions, sent directly and never
retained in this session or committed to the repo, per this project's
"never commit secrets/keystore/passwords" rule) and isn't repeated here.

## What's actually verified, with exact evidence

| Claim | Status | Evidence |
|---|---|---|
| Debug APK builds | VERIFIED | CI run `36971938630` (commit `53d221c`), step "Build debug APK": success. Artifact `nua-debug-apk` uploaded. |
| Release APK builds (unsigned) | VERIFIED | Same run, step "Build release APK": success — this is the step that actually exercises R8/resource shrinking (`isMinifyEnabled = true`), the thing CI never checked before the signing-infrastructure work landed. Artifact `nua-release-apk` uploaded. |
| Signing gracefully no-ops without secrets | VERIFIED | Same run, step "Decode release keystore": `skipped` — the `env.HAS_RELEASE_SIGNING == 'true'` gate correctly read `secrets.RELEASE_STORE_BASE64` as empty and skipped rather than failing, exactly as `android-build.yml`'s own comment documents. |
| A **signed** release APK | NOT YET PRODUCED — BLOCKED EXTERNALLY | No `RELEASE_*` secrets exist in this repository yet. Only the repository owner can add them (this session cannot create repository secrets). Once added, the next CI run's "Decode release keystore" step stops skipping and `assembleRelease` signs the APK with `app/build.gradle.kts`'s `signingConfigs.release` block — no further code change needed, this path already exists and is exercised by the unsigned case above. |
| Exact-SHA CI gates | VERIFIED, as a standing practice | Every commit in this session (`45d6c11`, `c8961f9`, `8583cf5`, `4b3f9de`, `83df2c6`, `53d221c`, `80b1cc6`, …) has been checked green at CI job level, by its exact SHA, before being treated as done — the rule `docs/ENGINEERING.md` added after the one historical lapse (`32887d3`, five days red) this project's own history record documents. |
| Installable APK's own SHA-256 | NOT COMPUTED BY THIS SESSION | See below — a real sandbox limitation, not an oversight. |

## Why the APK's own SHA-256 isn't in this document

GitHub's Actions API reports a `digest` field for each uploaded artifact —
for example, run `36971938630`'s `nua-release-apk` artifact reports
`sha256:32f4f326fb2e6017259984a7cfe55f213e95b33ec7f96313bfafdb27a96b4f0e`.
That digest is **not presented here as the APK file's own hash**: GitHub
documents `actions/upload-artifact@v4` as wrapping the matched file(s) in a
ZIP container before computing and storing that digest, so it's a hash of
the artifact container, not necessarily byte-identical to running
`sha256sum app-release.apk` on the extracted file. Treating it as
interchangeable with the installable file's own hash would be exactly the
kind of fabricated verification evidence this project's engineering
discipline (and the personal-test directive's explicit "must not fabricate
... delivery receipts" rule) rules out.

Computing the real file hash requires downloading the artifact and running
`sha256sum` on the extracted `.apk`. This session's sandbox cannot do that:
`download_workflow_run_artifact` resolves to a
`productionresultssa*.blob.core.windows.net` URL, and the sandbox's outbound
network policy rejects that host (`CONNECT` refused with 403 — confirmed via
the proxy's own `/__agentproxy/status` endpoint, the same way the identical
limitation was confirmed and documented for the Room schema artifact in
`docs/DATABASE_MIGRATION_POLICY.md`). This is an environment boundary, not
something to route around.

**Owner action:** download `nua-debug-apk` and/or `nua-release-apk` from any
green CI run's Artifacts tab, run `sha256sum` on the extracted `.apk` file,
and record the result here (or in `docs/PERSONAL_TEST_SMOKE_TEST.md` once
that document exists) as the authoritative hash for the build being
installed on a personal device.

## What this means for personal testing (directive §3)

Per the directive, a signed release build is **not** required for personal
testing — a debug build, or an unsigned release build sideloaded with
`adb install -r`, is sufficient for the owner's own device, since Android
only requires consistent signing across upgrades of the *same* package, not
a particular signing identity, for a sideloaded install. The signing gate
above is real and already working; it simply hasn't been exercised in its
signed branch yet because the owner hasn't added the secrets. That is not a
blocker to producing a personal-test APK (Task 19 in the directive's
execution order) — it only matters once this moves toward Play Store
distribution (Task 20), where Google's own signing-key continuity
requirements make the signed path mandatory.
