# Temporal World Model 2.0 + CommitmentGraph — RFC

**Status:** Partially implemented — pure temporal/commitment logic landed and
CI-verified; schema/persistence explicitly gated (see §6).
**Author's note:** Written for the same reason `docs/WORLD_MODEL_RFC.md` was —
"do not add a graph database / new persisted concept without an RFC first."
Feature 3 of the 5-Year Standalone Master Directive ("Temporal World Model
2.0 + CommitmentGraph") was deliberately deferred in an earlier session on
direct user instruction ("Move to Phase C," `docs/HISTORY.md`, September 27)
and never investigated for feasibility. This RFC is that investigation.

## 1. Problem statement

Two separate gaps, both real, both named by something that already exists:

1. **No interval-valid relationships.** `docs/WORLD_MODEL_RFC.md` §3 ("Temporal
   model") explicitly scoped this out of version 1: "No relationship carries
   its own 'valid from / valid until' interval in this first version... If a
   future entity is genuinely interval-valued..., that's a reason to revisit
   this section." `world_relationships` rows are asserted-once, point-in-time
   facts today — there's no way to represent "this was true from X until Y."
2. **No commitment concept.** Grepped the whole codebase for "commitment" —
   the only hits are `FactExtractor.kt`'s prompt text and `GoalType.kt`'s enum
   name, neither a real tracked entity. `GoalEntity` tracks open-ended
   aspirations ("learn Spanish"), not a specific promise with a deadline
   ("text Sam back by Friday," "I'll follow up on this Tuesday") and a
   fulfilled/expired outcome. The directive's own "CommitmentGraph" name
   implies exactly that: a graph of promises (NUA's or the user's), each
   connected to what they're about via the existing relationship layer, each
   with a real temporal state.

## 2. What this recommends

**Reuse, don't duplicate, the existing relationship layer.** A commitment is
itself an entity other things can relate to ("this commitment references that
goal," "this commitment was made during that conversation") — so it should be
a new `(type, id)` participant in `world_relationships`
(`TYPE_COMMITMENT = "COMMITMENT"`), not a parallel graph structure. This is
the same "additive, not a new subsystem" principle `WORLD_MODEL_RFC.md` §2
already established.

Concretely, two additive schema changes (not implemented this round — see
§6):

```text
world_relationships  (existing table, two new nullable columns)
  validFrom   Long?   -- null = valid since the relationship was created
  validUntil  Long?   -- null = still valid; set once superseded/ended

commitments  (new table)
  id            PK
  kind          String   -- "MADE_BY_USER" | "MADE_BY_NUA"
  description   String
  dueAt         Long?    -- null = no deadline ("I'll always text you back")
  fulfilledAt   Long?    -- null until fulfilled
  createdAt     Long
```

A commitment's *state* (pending/fulfilled/expired) is never stored — it's
always derived from `dueAt`/`fulfilledAt`/now, the same "derive, don't
duplicate" reasoning `trust/AutonomyContract.kt`'s `contractShouldSuspend`
and `ActionPlan.kt`'s `nextRunnableStep` already apply to their own state.
What a commitment is *about* (a goal, a person implied by an SMS recipient, a
decision) is a `world_relationships` edge from `("COMMITMENT", id)` to the
existing entity — no new link columns on `commitments` itself.

## 3. Temporal model (the actual "2.0")

`isRelationshipValidAt(validFrom, validUntil, at)`: a relationship is valid
at a given instant when `at >= (validFrom ?: Long.MIN_VALUE)` and
`at < (validUntil ?: Long.MAX_VALUE)`. Both bounds default to "unbounded on
that side," so every relationship written under the version-1 World Model
(no columns, i.e. both null) stays valid at every instant it's queried —
this is a strict additive extension of the existing data, not a
reinterpretation of it.

Superseding a relationship (the "becomes wrong" case §8 of the original RFC
already names) now has a real mechanism: write the new relationship, then set
the old one's `validUntil` to the supersession instant — rather than only the
existing confidence-toward-zero orphan-marking, which was designed for a
*deleted* endpoint, not a relationship that was simply true for a while and
then stopped being true.

## 4. Commitment state

Pure, three states — deliberately not a richer model (no "broken," no
"cancelled") until a real writer proves one is needed, per rule 8:

- **PENDING** — not yet fulfilled, and (no due date, or due date hasn't
  passed).
- **FULFILLED** — `fulfilledAt` is set. This always wins over every other
  condition, including a due date that already passed — fulfilling something
  late is still fulfilling it, not a different state.
- **EXPIRED** — not fulfilled, has a due date, and that due date has passed.

No "BROKEN" state: distinguishing "quietly never happened" from "explicitly
abandoned" needs a real signal (the user saying "never mind") that nothing
today produces — EXPIRED covers the observable fact (`now > dueAt`,
unfulfilled) honestly without fabricating an intent NUA can't actually know.

## 5. Provenance, confidence, privacy, retention, indexing, deletion

Identical to `WORLD_MODEL_RFC.md` §4–§9 for the `validFrom`/`validUntil`
columns — they're columns on the same table. For `commitments`: no separate
privacy classification is introduced (same reasoning as §6 of the original
RFC — it's subject to whatever deletion surface a future writer's UI gives
it); orphan-marking for commitment-naming relationships works identically
since `("COMMITMENT", id)` is just another `(type, id)` pair to
`WorldModelRepository`'s existing resolution logic.

## 6. Migration — why this is gated, not implemented yet

Both schema changes (`world_relationships`'s two new columns, the new
`commitments` table) would bump `NuaDatabase` past `version = 20`.
`docs/DATABASE_MIGRATION_POLICY.md` is explicit: **the first bump past 20
must ship with a real `Migration` object and a `MigrationTestHelper` test**,
not `fallbackToDestructiveMigration()` — and that test needs Robolectric,
which this project hasn't adopted yet (personal-test directive item 13,
tracked separately, not yet done).

This is the exact same constraint that already deferred `RecipeEntity`'s
enable/disable flag and WorkManager scheduling columns earlier this session
(`recipes/RecipeCompiler.kt`'s own commit history) — forcing a schema bump
now, before the migration-testing infrastructure exists to verify it safely,
would be the "fabricating migration correctness rather than verifying it"
mistake the policy document explicitly warns against. **Schema and
persistence for both the interval-validity columns and `commitments` are
deferred until item 13 lands**, not dropped.

## 7. What this round implements instead

The one thing that needs no schema at all and is still genuinely real: the
pure temporal/state logic itself, fully unit-tested now so it's ready to back
real persistence the moment the migration exists —
`world/CommitmentGraph.kt`'s `CommitmentState` / `commitmentStateFor` and
`isRelationshipValidAt`. Nothing reads or writes a database in this file;
every edge case (fulfilled-but-overdue, no-due-date, boundary instants) is
exercised directly against the pure functions.

## 8. What this RFC does not decide

- Which feature writes the first real commitment (a natural candidate:
  `GoalReviewWorker` or a Recipes step that implies a promise — "text Sam
  tomorrow" — but that's a writer seam, not this RFC's job, same stance
  `WORLD_MODEL_RFC.md` §14 already took for Dreams).
- Any UI for viewing commitments.
- The Counterfactual Decision Simulator (Feature 4) — the directive itself
  notes it depends on this feature existing first; it remains not started.
