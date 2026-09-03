# ADR 0001: World Model storage architecture

**Status:** Accepted (retroactively formalized — the decision was made and implemented in
P1.9, before this ADR format existed; this document backfills the explicit 5-option
comparison the scale-up directive requires, against evidence from the already-shipped
code, not speculation).

**Context:** `docs/WORLD_MODEL_RFC.md` §2 already rejected two options (a graph database,
and a polymorphic single-entity table) and recommended a third (an additive relationship
table). That RFC did not frame the decision against the five architectures the scale-up
directive names explicitly. This ADR does, using the same evidence.

## Options considered

1. **Relational Room extensions** — add typed foreign-key columns/tables directly onto
   existing entities (e.g. `DecisionEntity.sourceGoalId: Long?`).
   *Rejected.* Every relationship type (`created_from`, `supports`, `references`,
   `affects`) would need its own column on every entity that could originate or receive
   it — an N×M schema explosion for a personal-assistant dataset where most entities have
   zero or one relationship. Also couples unrelated tables' schemas together, so adding a
   new relationship type means migrating every entity type it might touch.

2. **Adjacency/edge tables in Room** — a single typed edge table referencing existing
   entities by `(type, id)` pairs, independent of any one entity's schema.
   **Selected and shipped.** This is exactly `world_relationships`
   (`WorldRelationshipEntity`/`WorldRelationshipDao` in `MemoryStore.kt`, `world/
   WorldModelRepository.kt`). One new table expresses arbitrary relationship types between
   any two existing entities without touching their schemas. `(type, id)` instead of a
   Room-enforced foreign key is deliberate: a relationship whose target was deleted drops
   to zero confidence (`confidenceAfterResolutionCheck`) rather than cascading, preserving
   the audit trail the same way the Trust Ledger keeps failed-action history.

3. **Embedded graph engine** (e.g. an on-device graph database/library).
   *Rejected.* No evidence Room/SQLite's query capabilities are insufficient for the
   relationship volumes a single-user personal assistant produces — thousands, not
   billions, of edges, all readable with an indexed `WHERE fromType = ? AND fromId = ?`
   query. Adding a second persistence technology for volumes this small would be
   unjustified architectural risk with no measured need (`ROADMAP.md`'s "no speculative
   architecture" rule).

4. **Hybrid relational + vector retrieval** — pair the relational layer with an embedding
   index for semantic/fuzzy relationship discovery.
   *Rejected, for now.* `FactRelevance.rank()` and `SecondBrainSearch` already achieve
   adequate retrieval quality with lexical token-overlap + recency weighting — no
   retrieval-quality benchmark has shown lexical/structured retrieval inadequate for this
   dataset size, and the scale-up directive itself requires exactly that evidence before
   adding embeddings ("do not use embeddings by fashion"). Revisit only if a measured
   benchmark shows a real gap; not a default.

5. **Cloud graph dependency** (a managed graph database service).
   *Rejected.* Directly contradicts "local-first by default" (Absolute Engineering Law
   8) and introduces a network dependency, an operating cost, and a new class of privacy
   exposure for data that never needs to leave the device at current scale. No evidence
   of a scale or query need that a local table can't serve.

## Decision

Option 2 (adjacency/edge table), as already implemented. No schema change follows from
this ADR — it documents the reasoning behind code already shipped and CI-verified in
P1.9 (`b88fc69`).

## Consequences

- Adding a new relationship *type* is a data-only change (a new `relation` string value),
  not a migration.
- Adding a new relationship *direction* between two entity kinds that have never been
  linked before is also data-only — `fromType`/`toType` are free-form strings, not a
  fixed schema.
- Bounded traversal must stay an application-level discipline (query with explicit
  `LIMIT`s), since there's no graph engine enforcing traversal depth for us. No query
  currently unbounded traversal exists yet because no reader beyond
  `WorldModelRepository.relationshipsFor()` (single-hop) has been built.
- Revisit this ADR, not the code, if a future measured need (traversal depth, retrieval
  quality, or query volume) actually can't be served by option 2 — per "no speculative
  architecture," that evidence must come first.
