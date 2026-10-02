# Database Migration Policy

NUA's Room database (`NuaDatabase` in
`app/src/main/java/com/nua/assistant/memory/MemoryStore.kt`) is currently at
schema `version = 20` and relies entirely on
`Room.databaseBuilder(...).fallbackToDestructiveMigration()` (wired in
`app/src/main/java/com/nua/assistant/di/AppModule.kt`). This document is the
record of why that's correct today, and the rule for when it stops being
correct.

## Why versions 1-20 are destructive, and why that's acceptable

Room has never had an export of this database's schema at any of the 19
version bumps that produced today's version 20 (`exportSchema` was `false`
until this policy). Every one of those bumps happened during this project's
own pre-release development — there is no real installed base at any
intermediate version, and no recoverable schema snapshot to diff against to
write a trustworthy `Migration` even if we wanted to retroactively add one.

Writing a `Migration(1, 2)`, `Migration(2, 3)`, … `Migration(19, 20)` chain
now, from memory or from reading entity definitions after the fact, would be
fabricating migration correctness rather than verifying it — exactly what
this project's engineering discipline (see `docs/ENGINEERING.md` /
`docs/HISTORY.md`) and the personal-test deployment directive both
explicitly rule out. A wrong hand-written migration is worse than a
destructive reset: it can corrupt data or crash on upgrade instead of just
clearing it.

The personal-test deployment directive's own instruction on this point:
*"avoid unnecessary destructive migration; a one-time dev-only destructive
reset is acceptable if documented and removed before public release."*
Versions 1-20 are exactly that one-time dev-only reset, and this document is
that documentation.

## What changes starting at version 21

Two things changed as of this policy:

1. **Schema export is now on.** `NuaDatabase` sets `exportSchema = true`, and
   `app/build.gradle.kts` passes `room.schemaLocation` to the Room KSP
   processor, so every future build writes a JSON snapshot of the schema to
   `app/schemas/com.nua.assistant.memory.NuaDatabase/<version>.json`. Version
   20's snapshot is committed as the baseline. **Commit the new JSON file
   every time the version number changes** — Room's own migration-testing
   tooling (`androidx.room:room-testing`'s `MigrationTestHelper`) reads these
   snapshots to verify a `Migration` actually produces the expected schema.
2. **The user is about to install real builds on a real device for personal
   testing.** Once that happens, a schema bump that destructively wipes the
   database destroys their actual test data (chat history, facts, goals,
   dreams, decisions, autonomy contracts, recipes, etc.) for no good reason —
   there's no longer a "no real installed base" excuse.

**Rule: the first schema change after version 20 (i.e. the `version = 21`
change, whenever it happens) must ship with a real `Migration` object**,
added via `.addMigrations(MIGRATION_20_21)` in `AppModule.kt`'s
`provideNuaDatabase()`, not a continued reliance on
`fallbackToDestructiveMigration()`. `fallbackToDestructiveMigration()` can
stay as a safety net for anything *before* 20 (there's still no real data at
those versions to lose), but it must not be what silently handles 20→21.

### How to write and verify that migration, when the time comes

1. Write the `Migration(20, 21)` object with the exact `ALTER TABLE`/
   `CREATE TABLE`/`CREATE INDEX` SQL needed to take the committed version-20
   schema JSON to the new version-21 entity definitions.
2. Add `androidTestImplementation("androidx.room:room-testing:2.6.1")` (not
   yet a dependency — add it alongside this work, not speculatively now) and
   a `MigrationTestHelper`-based test that actually runs the migration
   against a database constructed from the version-20 schema JSON and
   asserts the result matches. This needs either Robolectric or a real
   instrumented-test target; Robolectric's own adoption for this project is
   tracked separately (personal-test directive item 13, "Robolectric /
   instrumented test expansion") and that work should land before or
   alongside the first real migration, not after.
3. Do not mark the migration DONE until that test has actually run and
   passed — per this project's standing rule, a claimed verification that
   wasn't run is worse than no verification at all.

## Current status (as of this policy's introduction)

- `exportSchema = true` is now set on `NuaDatabase`, and `app/build.gradle.kts`
  passes `room.schemaLocation` to KSP. This repository's own development
  sandbox cannot run a local Gradle build (confirmed repeatedly elsewhere in
  `docs/HISTORY.md` — network/proxy restrictions block Google's Gradle
  plugin repositories), so the actual `20.json` schema snapshot cannot be
  generated here. `.github/workflows/android-build.yml` now uploads
  `app/schemas/` as a CI artifact (`room-schemas`) so that file can be
  pulled from the next green CI run and committed as a real, CI-generated
  baseline rather than hand-written from reading entity definitions. **As of
  this commit, that file has not yet been pulled down and committed** — that
  is the concrete next step, not a claim already fulfilled.
- No `Migration` objects exist yet. None are needed yet — the version is
  still 20.
- `fallbackToDestructiveMigration()` remains in `AppModule.kt`, now with a
  comment pointing back to this document and the version-21 rule.
- Robolectric is not yet wired into this project (tracked separately); until
  it is, a real `MigrationTestHelper` test for a future migration will need
  either that work landing first or an instrumented test run on real
  hardware/an emulator, which this development environment does not have.
