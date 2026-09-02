# NUA World Model — RFC

**Status:** Accepted — additive relationship layer implemented and CI-verified (`b88fc69`,
P1.9). See `docs/architecture/decisions/0001-world-model-storage-architecture.md` for the
formal 5-option comparison (relational extensions / adjacency table / embedded graph /
hybrid vector / cloud graph) this RFC's §2 recommendation maps onto.
**Author's note:** Written per the master roadmap's explicit instruction — "Do NOT
automatically add a graph database. First create a World Model RFC" — before any code for
the World Model is written. The RFC was the first seam; the additive relationship table
and `WorldModelRepository` were a separate, subsequent seam, per "one architectural seam
at a time." Deliberately still unbuilt: read-side resolution into real entity objects
beyond single-hop lookup, and any writer (Dreams is the RFC's recommended first writer).

## 1. Problem statement

NUA already persists a wide range of entities about the user's world — but each lives in
its own Room table with no reference to any other. Grepped every entity in
`memory/MemoryStore.kt` and the domain-specific stores (`goals/`, `dreams/`, `decisions/`,
`documents/`, `vision/`) to confirm this rather than assume it:

| Entity | Table | Cross-references to other entities |
|---|---|---|
| `UserFactEntity` | `user_facts` | none |
| `GoalEntity` / `GoalObservationEntity` | `goals` / `goal_observations` | observation → goal only |
| `DecisionEntity` | `decisions` | none |
| `DreamEntity` | `dreams` | none (synthesized *from* other tables at generation time, but the link isn't stored) |
| `DocumentEntity` | `documents` | none |
| `VisionMonitorEntity` | `vision_monitors` | none |
| `TrustLedgerEntity` / `ActionOutcomeEntity` | `trust_ledger` / `action_outcomes` | none |

`UserFactEntity` is already close to a proper memory record — it carries `memoryType`
(identity/episodic/semantic/behavioral/emotional), `source`, `confidence`, `lastUsedAt`,
`createdAt`, `updatedAt` (see `memory/MemoryType.kt`). What's genuinely missing across the
whole schema is the *connective tissue*: nothing records that a `DecisionEntity` was
`created_from` a goal, that a `DocumentEntity` `supports` a decision, or that two facts are
about the same person. `Dreams` — the one feature explicitly designed to notice
cross-entity connections — currently reconstructs that context from scratch inside a
single Claude prompt every time (see `dreams/DreamSynthesisWorker.kt`) rather than reading
it from anywhere durable. This is the concrete gap the roadmap's "isolated memories, not a
connected representation" framing describes.

## 2. What this RFC recommends — and what it explicitly rejects

**Rejected: a graph database.** No evidence that Room/SQLite's query capabilities are
insufficient for the relationship volumes a single-user personal assistant will ever
produce (thousands, not billions, of edges). Adding a second persistence technology here
would violate rule 8 (no speculative architecture: prove the need first) and rule 38 (do
not add a graph database without an RFC — this RFC's conclusion is that the case hasn't
been made). If retrieval patterns that Room genuinely can't express emerge later, that's a
new RFC with its own evidence, not a default.

**Rejected: migrating existing entities into one polymorphic "Entity" table.** Every
existing table already has a working repository, DAO, and set of call sites. Rewriting
`GoalEntity`/`DecisionEntity`/etc. into rows of a generic `world_entities` table would be
exactly the kind of large, simultaneous refactor rule 38 forbids, for a benefit ("one
table instead of eight") that isn't itself a stated requirement anywhere in the roadmap.

**Recommended: an additive relationship layer, referencing existing entities by
`(entity_type, entity_id)`, changing nothing about how those entities are read or written
today.** Concretely, two new tables:

```text
world_relationships
  id              PK
  fromType        String   -- e.g. "DECISION", "GOAL", "DOCUMENT", "FACT", "DREAM"
  fromId          Long     -- the referenced row's existing primary key
  relation        String   -- e.g. "created_from", "supports", "references", "affects"
  toType          String
  toId            Long
  confidence      Float    -- 0-1, same scale as UserFactEntity.confidence
  source          String?  -- what produced this edge: "user stated", "dream synthesis",
                            --   "goal review", etc. — same idea as UserFactEntity.source
  createdAt       Long
```

`fromType`/`toType` are plain strings naming an existing entity table, not a foreign key
Room can enforce across tables it doesn't own — deliberately: a relationship pointing at a
since-deleted fact should be a detectable dangling edge (see §8), not a database-level
cascade silently deleting unrelated history. A thin `WorldModelRepository` would own
resolving `(type, id)` pairs back into their real entities on read, so callers never deal
with the raw pair.

This is intentionally the *smallest* thing that turns "isolated memories" into "a
connected representation" — it adds one new concept (a typed, confidence-scored,
attributed edge) rather than a new subsystem.

## 3. Temporal model

Every table in the current schema already has at least one timestamp (`createdAt`,
sometimes `updatedAt`/`decidedAt`/`timestamp`). The relationship layer needs only
`createdAt` — relationships in this design are asserted once, not amended in place; a
relationship that becomes wrong is superseded by inserting a new one and (per §8) marking
the old one's confidence toward zero rather than mutating history. No relationship carries
its own "valid from / valid until" interval in this first version — every existing entity
this design touches is itself point-in-time or append-only already (a `DecisionEntity`'s
`outcome` is filled in later but the decision itself doesn't change), so a richer temporal
model isn't evidenced yet. If a future entity is genuinely interval-valued (e.g., "was
Alex's manager from X to Y"), that's a reason to revisit this section, not a reason to
build interval support speculatively now.

## 4. Provenance

Every relationship records `source` — the same free-text provenance idea `UserFactEntity`
already uses ("said in conversation," "morning briefing pattern"). For a
Dreams-synthesized relationship, `source = "dream synthesis"` plus (in the summary text,
not a new column) which Dream produced it — Dreams already explain "what it noticed" and
"what it connected" in prose (`dreams/DreamSynthesisWorker.kt`'s system prompt), so the
relationship's provenance and the Dream's own explanation are the same information told
twice; storing the Dream's `id` in place of free text would be tighter, but isn't required
to satisfy this RFC's stated goal and can be added additively later without a migration
(it's a new nullable column, not a schema break).

## 5. Confidence

Reuses `UserFactEntity`'s existing 0–1 float convention rather than inventing a second
scale. A relationship a Dream infers starts lower-confidence than one the user stated
outright, the same distinction `UserFactEntity.confidence` already draws between an
extracted fact and a directly-stated one.

## 6. Privacy

No new privacy classification is proposed here. Every entity a relationship can point at
is already subject to whatever privacy/deletion behavior that entity has today (Settings'
per-`MemoryType` forget-by-type control, per-decision delete, etc. — see Phase 8 in
`ROADMAP.md`). The Privacy Centre item (P1.19) is the right seam to design a *unified*
privacy surface across all of these; this RFC's job is only to make sure the relationship
layer doesn't become an ungoverned side-channel that survives a deletion elsewhere — which
is exactly §8's concern.

## 7. Retention

Relationships have no independent retention policy — they're metadata about entities that
already have their own lifecycle. A relationship outlives neither entity it names existing
by design (see §8); nothing here proposes a relationship expiring on its own schedule.

## 8. Deletion

**The one real design decision this RFC has to make.** When `fromId`/`toId` names a row
that's since been deleted (the user forgot a fact, deleted a decision, etc.), the
relationship becomes a dangling reference. Two honest options, not one assumed:

- **Cascade:** deleting an entity deletes every relationship naming it. Simple, but risks
  silently discarding a Dream's explanation of *why* it noticed something, which is
  supposed to be inspectable (the Personal Memory Vault's "why it matters" requirement,
  P1.18).
- **Orphan-mark, don't cascade:** deletion leaves the relationship row in place but a
  resolution pass (part of `WorldModelRepository`, not a database trigger) treats an
  unresolvable `(type, id)` as evidence to lower, not erase — the relationship's stored
  confidence toward zero and to exclude it from active retrieval, while keeping the record
  that NUA once believed the connection, auditable the same way `TrustLedgerEntity`
  already keeps a record of past mistakes rather than deleting them.

This RFC recommends **orphan-mark**, matching the project's existing stance (per
`docs/ENGINEERING.md`'s security invariants and the Trust Ledger's own design) that an
audit trail of what NUA believed and why is itself a feature, not incidental data — the
same reasoning that keeps a rejected plan in the Trust Ledger instead of deleting it.

## 9. Indexing

Two indices cover the two real access patterns this design anticipates — "what connects to
this entity" (either direction) — nothing beyond that is justified without an observed
slow query:

```kotlin
@Index(value = ["fromType", "fromId"])
@Index(value = ["toType", "toId"])
```

## 10. Retrieval

`WorldModelRepository.relationshipsFor(type, id): List<ResolvedRelationship>` — queries
both directions (a decision's outgoing "created_from" edges and its incoming "supports"
edges from documents) and resolves each `(type, id)` back to its real entity via the
existing per-domain repository (`GoalRepository`, `DecisionRepository`, etc.), so a caller
gets real `GoalEntity`/`DocumentEntity` objects, not raw ids. No new query language, no
generic graph-traversal API — multi-hop traversal ("what connects to what this connects
to") is explicitly out of scope until a feature needs it, per rule 8.

## 11. Migration

Two new tables, additive to the existing Room schema (current version 10, per
`memory/MemoryStore.kt` — this RFC's implementation would bump it to 11). No existing
table's shape changes, so this is a pure addition — the same low-risk shape as every prior
schema bump this project has done (`fallbackToDestructiveMigration()`, already configured
in `di/AppModule.kt`, already the established precedent for every entity added this
project's whole history).

## 12. Performance

At the data volumes a single-user personal assistant produces (this schema's other tables
top out at low thousands of rows after a year of heavy use), a two-column-indexed lookup
table has no meaningfully measurable cost. Nothing here is justified beyond that
observation — no caching layer, no denormalization — until an actual slow query is
observed, per rule 8.

## 13. Offline behaviour

The relationship layer is local Room data like everything else it references — reading and
writing it works fully offline already, by construction (no network call anywhere in this
design). The only offline-relevant question is *who writes relationships*: Dreams
synthesis (`DreamSynthesisWorker`) requires Claude and is therefore online-only already;
a user directly linking two things from the UI (a future Personal Memory Vault
affordance) would be a local, offline-capable write. Nothing in this RFC requires network
access on its own.

## 14. What this RFC does not decide

- Which features actually *write* relationships first (Dreams is the obvious first writer
  given it already computes cross-entity connections and currently discards them after
  generating one sentence, but that's an implementation seam, not this RFC).
- Any UI for browsing the world model (Personal Memory Vault, P1.18, and the WORLD
  navigation destination named in the roadmap's brand section are the natural home for
  that — separate seams).
- Multi-hop graph queries, if a future feature turns out to need them.

## 15. Recommendation

Implement §2's two-table additive layer as its own seam (schema + DAO + repository +
regression tests for the orphan-mark deletion behavior specifically, since that's the one
non-obvious design decision here), with **Dreams as the first writer** — `DreamSynthesisWorker`
already computes which facts/goals/decisions/ledger entries it cross-referenced to produce
an insight; recording that as `world_relationships` rows alongside the existing
`DreamEntity` insert turns information the system already has and currently throws away
into something the Personal Memory Vault (P1.18) and a future WORLD destination can
actually show the user. This keeps the first real seam small, evidenced, and immediately
useful, rather than building the relationship layer speculatively ahead of any writer.
