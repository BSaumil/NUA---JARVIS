# NUA — Status Report & Scope for the Next 10 Upgrades

*As of October 2, 2026. Sources: `docs/HISTORY.md` (the dated, entry-by-entry build log),
`FINAL_5_YEAR_STANDALONE_IMPLEMENTATION_REPORT.md`, `README.md`'s "Known gaps", and
`ROADMAP.md`. Where those documents disagree, this one follows whichever is most
recently verified — `README.md` and `ROADMAP.md` predate the September 27–30 work below
and are now stale in places; that's noted inline rather than silently resolved.*

---

## 1. What's been done, day 1 to today

### March – August: scaffolding and the first 12 phases
The repository began as an unrelated prototype, then on August 4 the real Android app
(`com.nua.assistant`) started. In quick succession: multi-wake-word support, CI wired up
for real Android builds, then Phases 5–12 shipped one after another —

- **Phase 5** — offline resilience and a pluggable `NuaSkill` architecture.
- **Phase 6** — Trust & Autonomy Core: the T0–T5 autonomy-tier model, action-outcome
  logging, the Trust Score.
- **Phase 7** — Goals & the Context Engine; the daily briefing and "What Now?" entry
  point.
- **Phase 8** — Dreams 2.0 (proactive insight synthesis), Second Brain search, the
  Decision Journal, Timeline, full Memory OS controls.
- **Phase 9** — Vision-as-a-system and Document Intelligence.
- **Phase 10 (partial)** — Communication Centre: SMS send, calendar invites.
- **Phase 11** — Self-Diagnostics, biometric step-up auth, local encryption audit, the
  Prompt Injection Firewall, Agent Sandbox.
- **Phase 12** — the design token layer, dark theme, `NuaState`/the NUA Orb, Command
  Centre, five-destination navigation, reduced-motion support.

### Late August: an incident, and the discipline it produced
A command-palette regression sat broken in CI for five days before anyone checked —
caught in an architecture review, not by process. This became the project's defining
rule, restated in `docs/ENGINEERING.md` ever since: **a change is not verified until the
exact commit SHA has passed CI**, checked at the job level, not just "did it look green."
Two architecture-review rounds followed, reconciling the docs against what the code
actually did.

### Late August – September: the P0/P1 hardening passes
Concurrency/idempotency fixes, truthful action-outcome states (replacing a bare
succeeded/failed boolean with a real vocabulary — attempted/accepted/completed/failed),
adversarial prompt-injection tests plus a structural audit, worker-cancellation
hardening, CI test-gate hardening, the World Model RFC and its first relationship layer,
a full launch-readiness pass (release-build/R8 verification), Memory OS privacy
classification and fact correction, idempotency protection, and a structured "What Now?"
recommendation model. By September 9, a second round shipped: Context Engine broadened,
World Model read-side resolution, Earned Autonomy (the original unscoped 30-day
auto-approve grant), Document Intelligence citation/redaction, and Communication Centre
thread provenance.

### September 27–30: the 5-Year Standalone Master Directive
A much larger, phased build, run end-to-end this session under a standing "finish
everything, document honestly" instruction. Full detail for every item below lives in
`docs/HISTORY.md`; summarized:

| Phase | Feature | Outcome |
|---|---|---|
| A — Foundation | Repository truth audit + doc reconciliation | DONE |
| A | **Universal Action Fabric** — capability descriptors generated from the real skill registry, a dependency-DAG plan executor (`WorkflowExecutor`) with one authorization checkpoint no adapter can bypass, 2 of 7 named execution adapters real (LocalNative, NotificationRemoteInput) | DONE |
| A | **Privacy Capsules / Data Egress Gateway** — policy-enforced gate on one real integration point (main chat fact extraction) | DONE |
| A | **Flight Recorder** — hash-chained, tamper-evident lineage log of every Action Fabric step | DONE |
| A | **Guardian Lab baseline** — a third self-proving static audit (`tools/uaf_boundary_audit.py`) + 4 adversarial tests | DONE |
| B — Intelligence moat | **Sovereign Model Mesh** — 5-tier deterministic routing (`ai/mesh/`), one real migrated call site (`IntentClassifier`) | DONE |
| B | Temporal World Model 2.0 + CommitmentGraph | **SKIPPED — direct user instruction** ("Move to Phase C") |
| B | Counterfactual Decision Simulator | **SKIPPED — direct user instruction** |
| C — Autonomy moat | **Contextual Autonomy Contracts + Shadow Mode** — recipient/risk-ceiling/frequency scoping on top of the legacy grant, drift-based auto-suspend, predict-without-acting Shadow Mode | DONE |
| C | **NUA Recipes** — a natural-language automation compiler targeting the *existing* Action Fabric runtime (not a second engine); deterministic keyword-based parsing, zero-side-effect simulation | DONE |
| D — Presence moat | **Presence Mesh** (multi-device continuity) | **NOT ATTEMPTED** — investigated in full, found to have zero real infrastructure (no device identity, no transport, no way to verify a slice in this environment); documented rather than faked |
| D | **Runtime Safety Sentinel + extended Guardian Lab** — a new diagnostic component plus adversarial proof that Contracts and Recipes can't bypass the Action Fabric | DONE |
| — | Final report | `FINAL_5_YEAR_STANDALONE_IMPLEMENTATION_REPORT.md` |

Two real defects were caught by CI during this run and fixed forward rather than hidden
(a test-authoring bug in `ModelMeshTest`, and a test-fixture naming collision in the
Guardian Lab round) — both disclosed in `docs/HISTORY.md` with the same honesty as
everything that shipped clean.

### September 30: release signing infrastructure
Prompted by "what's next to deploy as an app" — `app/build.gradle.kts` had no release
`signingConfig` at all (CI's release build has always been deliberately unsigned, just to
verify R8/minification). Added a conditional signing setup: reads credentials from a
local `keystore.properties` or from four CI repository secrets, builds unsigned if
neither exists. A real signing keystore was generated and handed directly to the
repository owner (never retained by this session). Two CI-caught defects along the way —
the `secrets` context can't be used directly in a step's `if:`, and an inline
`java.util.Properties()` reference didn't resolve in Gradle's Kotlin DSL — both fixed
forward and documented. **Current state: CI-green, unsigned until the repository owner
adds the four `RELEASE_*` secrets** (see `SIGNING_SETUP.md`, sent separately).

---

## 2. What's missing right now

Consolidated from `README.md`'s "Known gaps," `docs/HISTORY.md`'s "explicitly not
attempted" sections, and this session's deployment investigation. Grouped by what kind of
gap it is, not by when it was found.

### Blocks Play Store submission specifically
- **`SEND_SMS` permission compliance.** Google Play requires apps using this permission
  to be the device's registered default SMS or Assistant handler — NUA is neither. No
  declaration-form exception exists for a non-default app. **This is a real product
  decision, not paperwork** — see upgrade #1 below.
- **No Privacy Policy, no Data Safety form.** Required for a permission set this broad
  (SMS, calendar, contacts, background location, microphone, notifications).
- **`ACCESS_BACKGROUND_LOCATION`** needs its own separate Play Console declaration form,
  not yet filed.
- **`fallbackToDestructiveMigration()`** on the Room database is fine pre-release (no
  installed base yet) but would silently wipe every user's data on the first post-launch
  schema change — needs real `Migration` objects from the first public release version
  onward.
- **No store listing assets** — a launcher icon exists but is a placeholder glyph, no
  screenshots/feature graphic/description.

### Never verified against real hardware or a live runtime
- Test coverage only reaches pure business logic (`KeywordIntentMatcher`,
  `FactExtractor`'s gating heuristic, `NotificationPriorityScorer`, `FactRelevance`,
  `extractJsonPayload`, and this session's many pure `trust`/`recipes`/`ai/mesh`
  functions). **Nothing exercises the Room DAO layer, `ClaudeApiClient`'s HTTP/streaming
  client, WorkManager scheduling, the Glance widget, or Compose UI directly** — all would
  need Robolectric or instrumented (on-device) tests.
- Android Auto and the Wear OS tile compile and use real official libraries but have
  never run on a head unit, the Desktop Head Unit emulator, a Wear device, or the Wear
  emulator.
- `OwnerEnrollment`/`OwnerVerifier` (Picovoice Eagle speaker ID) is verified only against
  the SDK's published source, never against a real device or the live Picovoice service.
- `AppLauncher`'s package-name map is best-effort, never checked against a real device's
  installed app set.

### Structurally real but functionally incomplete
- **Universal Action Fabric**: only 2 of 7 named execution adapter types are implemented
  (App Functions, MCP, Android Intent, External API, Accessibility are declared, not
  built). `NuaIntentRouter` itself still runs its own separate dispatch path — never
  migrated to go through the fabric.
- **Sovereign Model Mesh**: only `IntentClassifier` is migrated. Every other Claude call
  site (fact extraction, planning, briefings, document/vision analysis, workers, main
  chat) still calls `ClaudeApiClient` directly. `LOCAL_MODEL`/`PRIVATE_OS_MODEL` tiers are
  declared but always honestly report unavailable — no on-device model exists.
- **NUA Recipes**: parsing is deterministic keyword-matching only (no LLM fallback for
  clauses it misses), no scheduling/triggers (a recipe only runs when called directly, so
  "every morning, …" phrasing isn't actually wired to anything time-based yet), no step
  dependencies, no creation/review UI.
- **Contextual Autonomy Contracts**: no Settings UI to create, view, or revoke a
  contract, or to review Shadow Mode's prediction accuracy — the engine is real and
  tested, nothing surfaces it yet.
- **`RuntimeSafetySentinel`**: diagnostic-only, not called from any live repository or UI
  path yet.
- **Tier 2 (Accessibility Service) automation**: the confirmation contract is fully
  designed; zero concrete actions are implemented on top of it.
- **The Wear OS tile** shows static text — no connection to the phone app's real data
  (calendar, weather, notifications). Needs the Wearable Data Layer API
  (`DataClient`/`MessageClient`), not built.
- **Multi-device Presence Mesh**: investigated and found to need a transport decision, a
  real paired device to verify against, and only then a device-identity/presence model —
  none of the three exist.
- Six of sixteen originally-named Guardian Lab adversarial scenario classes are covered
  (permission escalation, side-effect-boundary bypass, cross-step authorization leakage,
  fallback-adapter safety, replay resistance, autonomy-contract-bypass, recipe-compiler
  ambiguity); ten remain deferred (multilingual/code-switching, adversarial Unicode,
  context corruption, a trended results dashboard, and others needing features not yet
  built).
- **Temporal World Model 2.0 / CommitmentGraph and the Counterfactual Decision
  Simulator** were skipped this session on direct user instruction, not investigated for
  feasibility at all.

### Smaller, named, low-risk items
- Usage/cost dashboard estimates spend from hardcoded published pricing — drifts if
  Anthropic's pricing changes, no account-specific discount reflected.
- Trust Score formula and its "5 approvals" adaptive-autonomy threshold are sensible
  starting values, never tuned against real long-term usage.
- No in-app way to adjust fact-extraction cadence or rotate the Claude API key (Settings
  only covers viewing/forgetting facts and Tier 2/battery status).
- Only one of six wake-word phrases ("Jarvis") actually fires without extra setup — the
  other five are wired up end to end but each needs a trained `.ppn` voice model file
  produced externally (not generatable in this codebase).
- `README.md` and `ROADMAP.md` themselves are now stale in places — written before the
  September 27–30 work, they don't yet reflect the Action Fabric, Model Mesh, Contracts,
  Recipes, or signing infrastructure. Worth a reconciliation pass (not included as its
  own upgrade below since it's documentation, not product work, but flagged here so it
  isn't mistaken for a design decision).

---

## 3. Scope for the next 10 upgrades

Ordered roughly by what unblocks the most other work, not strictly by size. Each is
real scope, not a one-line wish — the kind of slice this project's own discipline asks
for: investigate first, ship the smallest complete honest piece, document what's
explicitly not attempted.

### 1. Resolve the `SEND_SMS` Play Store blocker and finish deployment readiness
The single gating decision for public release. Three real options, not a formality:
**(a)** drop `SEND_SMS`, switch to `Intent.ACTION_SENDTO` (`smsto:`) so NUA fills in the
message and the user's own SMS app sends it with one tap — fully compliant, small change,
arguably *more* consistent with this app's existing "always confirm before executing"
philosophy; **(b)** build the Android Assistant role (`VoiceInteractionService` etc.) so
NUA can legitimately hold `SEND_SMS` as the registered default Assistant — a real feature
in its own right, not a deployment step, and a much bigger lift; **(c)** stay
sideload-only indefinitely. Whichever is chosen, bundle it with: a Privacy Policy, the
Play Data Safety form, filing the `ACCESS_BACKGROUND_LOCATION` declaration, replacing
`fallbackToDestructiveMigration()` with real migrations from the release version onward,
and store listing assets (real icon, screenshots, description).

### 2. Finish the Sovereign Model Mesh migration
Migrate the remaining Claude call sites (`FactExtractor`, `TaskPlanner`,
`MorningBriefing`, `WhatNowAdvisor`, `SelfDiagnosticsRepository`, `DocumentAnalyzer`,
`DreamSynthesisWorker`, `GoalReviewWorker`, `MemoryConsolidationWorker`,
`VisionAnalyzer`, and — the biggest, most security-sensitive one — `NuaIntentRouter`
itself) onto `ModelMesh`, one deliberately separate, carefully verified pass per site
rather than one giant rewrite. `NuaIntentRouter`'s migration specifically deserves its
own adversarial test pass given how load-bearing it is.

### 3. Complete NUA Recipes end to end
Three real sub-pieces: **LLM-assisted parsing** for clauses the deterministic
keyword-matcher misses (the same local-rules-then-cloud shape `ModelMesh.classifyIntent`
already formalizes); **scheduling/triggers** via WorkManager so "every morning, …"
phrasing actually fires on its own, not just on manual `runRecipe` calls; and a
**creation/review UI** surfacing the compiler's own simulation output (what would run,
what would pause for confirmation, what wasn't understood) before a recipe is activated.

### 4. Device/emulator-backed test coverage
Close the single most-repeated gap in this project's own "Known gaps" list: add
Robolectric tests for the Room DAO layer and WorkManager scheduling, and instrumented
(on-device/emulator) tests for `ClaudeApiClient`'s HTTP/streaming client, the Glance
widget, and Compose UI screens. Pure-logic coverage is already strong; this is the
remaining, structurally different half.

### 5. A real first Presence Mesh slice
Only after a transport decision and access to a real paired Wear OS device/emulator to
verify against (both named as missing in this session's own investigation). Build the
Wearable Data Layer API (`DataClient`/`MessageClient`) integration, a device-identity
primitive, and a presence data model — then, as a direct follow-on, wire the Wear tile to
real phone data (calendar, weather, notifications) instead of its current static text.

### 6. Real-hardware verification pass: Android Auto, Wear OS, Tier 2 Accessibility
Three structurally-real-but-unverified surfaces in one pass: confirm Android Auto against
a head unit or the Desktop Head Unit emulator, confirm the Wear tile against a real Wear
device/emulator, and implement the Tier 2 Accessibility Service's first concrete action
(the confirmation contract already exists; nothing uses it yet). Revisit
`HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` and the `IOT` category declared for Android
Auto while in there, per the project's own existing note.

### 7. Surface the Autonomy/Safety layer in Settings
Three real backends with zero UI today: a screen to create/view/revoke Contextual
Autonomy Contracts (recipient scope, risk ceiling, frequency cap, expiry, Shadow Mode
toggle), a view of Shadow Mode's prediction-accuracy history, and a health-check surface
for `RuntimeSafetySentinel`'s contract/recipe anomaly detection — today a fully tested
function nothing calls from a live path.

### 8. Extend Guardian Lab's remaining adversarial coverage
Close as many of the ten still-deferred adversarial scenario classes as are concretely
testable without new features: multilingual/code-switching attacks against
`KeywordIntentMatcher`/`IntentClassifier`, adversarial Unicode handling, and a trended
results view so Guardian Lab's own pass/fail history is visible over time, not just
per-run. (Presence Mesh revocation and a few others stay blocked on features that don't
exist yet.)

### 9. Investigate, then build, Temporal World Model 2.0 + the Counterfactual Decision
### Simulator
These were skipped on direct instruction this session, not found infeasible — they
deserve the same investigation-first treatment every other feature here got. The
directive's own framing notes the Counterfactual Simulator benefits from the Temporal
World Model existing first, so sequence matters.

### 10. Voice-first depth and personality (`ROADMAP.md` Phase 14)
Barge-in/interruption handling (natural mid-utterance topic switches, true barge-in,
whisper mode, driving mode), six tunable personality axes replacing the current single
familiarity-tier tone model, and deeper mid-sentence multilingual code-switching beyond
today's per-message language pinning. Personality must never gain the power to override
a safety or confirmation decision — tone is expression, not permission, the same rule
this project has held throughout.

---

## Beyond the next 10 (longer horizon, for completeness)

`ROADMAP.md`'s Phase 15 (Skills Marketplace, Automation Builder — note NUA Recipes above
is a real step toward this already, Family/Shared Intelligence, Emergency Intelligence)
and Phase 16 (a dedicated NUA Agent Test Lab beyond Guardian Lab, deeper offline-first
capability, a Learning Engine that surfaces past-decision patterns as suggestions never
silent auto-choices) remain the project's own stated long-term direction, unstarted.
None of them are blocked by anything above — they're simply further out.
