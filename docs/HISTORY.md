# NUA — Engineering History

A chronological record of this repository, day one to today. Sourced from `git log`,
`ROADMAP.md`, and `README.md` — not reconstructed from memory. Where a phase's own
description already exists in `ROADMAP.md`, this document summarizes rather than repeats
it; see `ROADMAP.md` for the authoritative, currently-maintained phase-by-phase detail
and `docs/ENGINEERING.md` for the verification gate this history led to.

**Scope note:** this repository holds two independent, unrelated builds. `app/` and
`wear/` are the native Android/Wear OS app this history is about. `backend/` and
`frontend/` are a separate FastAPI + MongoDB + React Native/Expo prototype produced by
the Emergent app-builder platform, sharing no code with the Android app. Commits below
tagged *(Emergent)* touch only that side and are noted for completeness, not detail.

---

## March 25 — Initial commit

Repository created (`0948ec8`).

## April 6 — Early scaffolding *(Emergent)*

Five auto-commits (`820cb83`…`4b6d415`) from the Emergent platform, building out the
`backend`/`frontend` prototype. No `app/` code yet.

## August 4 — The Android app begins

- **`31323e6`** — Scaffold NUA app and build Phase 3 intelligence features. The first
  commit of the actual native Android app: Gradle/Compose/Hilt project structure, the
  memory layer (Room + secure key storage), the `ai/` layer (Claude client, personality
  engine), Tier 1 capability modules, automation (Tier 1/2) and voice, notifications with
  prioritization, services/DI/entry points, and the Compose UI — in one pass, not
  incrementally. Phase 3 on top of that scaffold: `NuaIntentRouter`'s local-keyword-first
  dispatch, `FactExtractor`, `TaskPlanner`, and `NotificationPriorityScorer`.
- **`e7aa319`** — Unit tests, a Settings screen, battery-optimization onboarding.
- **`182927d`** — README known-gaps updated to match.
- **`b8e6bac`** — An evolving voice: wittier, familiarity-scaled action confirmations.
- **`6bf022d`** — Ten-language support (English, Hindi, Gujarati, Marathi, Italian,
  Spanish, Haryanvi, Punjabi, Vietnamese, Mandarin) as a first-class feature, not a
  bolt-on — wired through STT/TTS and the Claude-facing system prompts alike.

## August 5 — Multi-wake-word

- **`d13bcf0`** — Multiple wake words, built extensible for future additions from day one
  rather than hardcoded to a single phrase.

## August 7 — *(Emergent)*

Four commits (`309e3dc`…`10c5cdb`) on the separate prototype side.

## August 8 — CI, and the two builds converge in one repo

- **`fdf27c5`** — GitHub Actions CI added for the Android app: the first point this
  project could verify a change actually compiled, rather than trusting local state.
  This becomes the load-bearing gate the rest of the project's engineering discipline is
  built on (see `docs/ENGINEERING.md`).
- **`db13773`** — The Emergent `backend`/`frontend` prototype merged into `Main` alongside
  the Android app, as a conflict-resolution merge. From here, `Main` carries both
  unrelated builds; this history and `ROADMAP.md` track only the Android side.

## August 10 — Phase 5 and Phase 6: Beyond JARVIS begins

- **`c4450eb`** — **Phase 5: offline resilience, senses, and reach.** Streaming Claude
  replies over SSE, prompt caching, a weather TTL cache, relevance-scoped fact injection,
  continuous follow-up conversation mode, proactive scheduled briefings (WorkManager),
  notification quick-reply via `RemoteInput`, a smart-home extension point (unconfigured
  by design — no OAuth backing it yet), a home-screen widget, and voice owner
  verification scaffolding (Picovoice Eagle). This is the commit that names the "Beyond
  JARVIS" direction the rest of the roadmap follows.
- **`4e86f22`** — **Phase 6: Trust & Autonomy Core.** `TrustScoreEngine` (a 0–100 score
  from logged outcomes, rejections weighted at half a failure), the `AutonomyTier` T0–T5
  enum replacing an README-only concept with a real per-skill mapping, the action audit
  trail, adaptive autonomy preferences, the rate-limited Trust Ledger self-report, and
  `MemoryType` typing on facts. The highest-leverage slice, deliberately first: stays in
  the data layer, and gives every later phase something to build on.

## August 10–11 — Phase 7: Goals & Context

- **`83cf9b3`** — Context Engine (`ContextEngine.currentSnapshot()`) and NUA Goals
  (durable, editable, weekly-reviewed).
- **`8c332e5`** — Daily briefing rewritten onto the Context Engine; the "what should I do
  now?" entry point.

## August 11–16 — Phase 8: Dreams, Second Brain, Decision Journal, Timeline, Memory OS

- **`7bbd078`** — NUA Dreams 2.0: a weekly worker synthesizes at most one real insight
  from facts/goals/trust data together, explicitly instructed to say nothing rather than
  force a connection.
- **`3758c0e`** — Second Brain search: lexical token-overlap search across facts, dreams,
  and decisions.
- **`b5016db`** — Decision Journal: log a decision and why, record the outcome later —
  written to on purpose, never inferred.
- **`096c539`** — A milestone note ("7 Intelligence Layers Complete") committed directly
  to docs.
- **`ac044f5`** — Timeline: a chronological feed merging every memory surface by
  timestamp, no new storage.
- **`9f324be`** — Full Memory OS controls: per-fact drill-down (why NUA remembers this),
  type filtering, bulk "forget all of this type."

## August 16–17 — Phase 9: Vision & Document Intelligence

- **`e469beb`** — NUA Vision restructured into see → understand → remember → act, plus a
  permission-gated "Monitor this" that compares a later photo against a stored baseline.
- **`8530362`** — Document Intelligence: dependency-free PDF/`.docx`/image ingestion
  (Android's built-in `PdfRenderer` + Claude vision; `.docx` read directly as zipped XML,
  no Office library), summarization with expiry detection, targeted Q&A across multiple
  documents, and a daily expiry-reminder worker.

## August 17 — Phase 10 (partial) and the start of Phase 11

- **`3269d56`** — Communication Centre, partial: SMS send (`SmsManager`, contact
  resolution via `ContactsContract`, always confirmed) and calendar invitations (attendee
  resolution, executes-then-reports). Gmail and WhatsApp explicitly left blocked — no
  OAuth/compliant API path exists yet, documented as blocked rather than silently skipped.
- **`0775623`** → PR #13 (**`d98c407`**) — **Self-Diagnostics**: real signals (permission
  checks, an actual message-count query, owner-enrollment state, injected email-repo
  check, accessibility-service state) turned into one specific status per area by the
  pure, unit-tested `evaluateDiagnostics` — two areas (Wear, Auto) honestly reported as
  unverifiable rather than faked.
- Four more commits land on `Main` before the next security phase — three tagged
  "Auto-generated changes," one an explicit merge — interleaved with the Self-Diagnostics
  PR.
- **`39ed292`** — **Biometric step-up + local-encryption audit**: `BiometricGate` wraps
  `androidx.biometric.BiometricPrompt`; `MainActivity` becomes a `FragmentActivity` since
  step-up requires one; `StepUpPolicy.requiresStepUpAuth` gates every T3+ confirmation
  dialog behind it when hardware exists, falling back honestly when it doesn't;
  `EncryptionAudit` lists what's actually encrypted (API key, voiceprint) versus what
  isn't (the rest of Room).
- **`6017031`** → PR #15 (**`df20f43`**) — **Prompt Injection Firewall**: `UserUtterance`
  as a type-level dispatch boundary (only the user's own literal turn can reach
  `route`/`classify`) plus `wrapUntrusted` delimiting for document/vision text reaching a
  Claude prompt, with the wrapping content unable to forge its own closing tag.
- **`5aca317`** — PR #16, a further Emergent-side conflict-resolution merge into `Main`.

## August 18 — Phase 11 completes; Phase 12 begins

- **`4fa71f2`** — **Agent Sandbox**: every skill declares a `SkillManifest` (abstract on
  the interface, so a new skill can't ship undeclared); every dispatch routes through
  `SkillSandbox`, enforcing declared parameters (undeclared ones stripped before the
  skill sees them), permission preconditions, and a declared timeout with exception
  containment. Closes Phase 11 — security, audit, and self-diagnostics — as
  "partially shipped" (Gmail/WhatsApp/unified triage remain genuinely blocked, not built).
- **`a02bebb`** — **Phase 12 begins: design token layer.** `NuaPalette` (every brand value
  as a plain ARGB long, unit-testable), `NuaColors`/`NuaTheme` mapping onto Material3,
  `NuaGradients`, shapes/type. Dark is the only theme, deliberately. `NuaPaletteTest`
  asserts every token against its literal spec value and checks WCAG contrast across all
  three surfaces.
- **`32887d3`** — **Command palette**, the day's final commit — pushed without its CI
  result being checked. This is where the story pauses: see *August 18–23* below.

## August 19 — Phase 12 continues

- **`b438afd`** (PR #19) — `NuaState` (all eight inner-life dimensions, derived from real
  Phase 6–9 signals by the pure `deriveNuaState`), the eight-state `NuaOrb`, and
  `CommandCentreScreen` as the new default surface.
- **`ddd1297`** (PR #20) — `NuaDestination`, a five-way Home/Ask/Memory/Act/You
  navigation replacing the boolean-flag navigation the UI had used until then; the Act
  destination generated from the same closed skill registry the router dispatches
  through, so it cannot drift from what NUA can actually do.
- **`f527624`** (PR #21) — `NuaMotion`'s three named timing bands, and
  `rememberMotionEnabled` generalized out of the Orb into `ui/theme` — until this commit
  it was private to one component, so every *other* animated surface silently ignored the
  system "reduce motion" setting.

## August 18–23 — The command palette build breaks, and stays broken for five days

`32887d3` (command palette) was pushed on August 18 without its CI result being checked,
and its very next scheduled follow-up moved straight on to the next phase rather than
confirming it. It sat red on `Main`'s branch for five days before anyone looked:

- **`bcb4515`** (Aug 23) — **Root cause found and fixed.** `NuaViewModel.paletteMemories`
  read `facts` and `dreams` — two properties declared *below* it in the class body.
  Kotlin compiles property initializers in declaration order, so this failed
  `compileDebugKotlin`/`compileReleaseKotlin` outright: `Variable 'facts' must be
  initialized`. The tell: `timeline`, thirty lines later, does the identical
  `combine(facts, dreams, …)` and compiles fine, purely because it's declared after them.
  Because the build never got past `compileDebugKotlin`, `testDebugUnitTest` never ran
  either — including `CommandPaletteTest`, the file written specifically to assert the
  palette's security property. That property had never actually been exercised once.
- **`aaa5fe2`** (Aug 23) — With the build green, a direct probe of the ranking logic (not
  just re-reading it) surfaced four real defects the compiler couldn't have caught:
  `take(limit - 1)` threw on a non-positive limit; the empty-query branch ignored `limit`
  entirely; memory scoring counted raw token occurrences, so a padded string like
  `"coffee coffee coffee"` outranked a memory that actually answered the query; and
  identical memories rendered as duplicate rows. Fixed, with `matchScore` and the memory
  path unified onto one tokenizer along the way (they'd used different regexes, so
  `"smart-home"` scored a word-start match on one path and a mere substring on the
  other). 20 tests added and verified — 14 pre-existing behaviors re-checked under the
  new logic, not assumed unchanged.

## August 26 (today) — Architecture review, driven by an explicit directive

A structured engineering directive requested: close the incident properly (verify the
*exact* commit SHA is green, not the branch), then run a security verification pass on
the palette, a static audit for the class of bug that caused the break, and a broader
architecture review before any new feature work.

- **`c8339d2`** — `bcb4515`/`aaa5fe2` confirmed green at their exact SHAs (CI run
  `32675234038`, all 11 steps, `testDebugUnitTest`/`testReleaseUnitTest`/`assembleDebug`
  all present and passing — not inferred from "the branch is green"). A T0–T5 security
  suite added: every tier probed explicitly, the T2 boundary pinned as an ordinal
  comparison (so reordering `AutonomyTier` would silently invert every gate rather than
  fail to compile), and the palette's gate cross-checked against `requiresStepUpAuth`
  directly so the two can't drift apart unnoticed. A forward-reference static audit
  (`tools/forward_ref_audit.py`) was written, wired into CI, and — critically —
  **required to reproduce the known `bcb4515` defect against itself before it's trusted
  to report a clean tree**; its first draft reported "0 findings" while being
  structurally unable to see the file the real bug lived in, because it never registered
  a class body when the constructor header spanned multiple lines.
- **`716cbcf`** — The architecture review's first finding: **step-up was bypassable.**
  `requiresStepUpAuth` was consulted in exactly one place — inside the Compose
  confirmation dialogs. `NuaViewModel.sendMessage`'s auto-approve branch called
  `executeConfirmedSms/Reply/Plan` directly, skipping `pendingSms`/`pendingReply`/
  `pendingPlan` — and with them, the only place biometric step-up runs — entirely. A T3
  SMS or T4 plan, once its action type was auto-approved in Settings, executed with no
  identity check at all. "Always allow" was silently answering two different questions
  ("ask me every time?" and "is this actually you?") when it should only have answered
  the first. Fixed so every proposal, auto-approved or not, always populates its pending
  field and always runs through the same gated confirm; auto-approval now only tells the
  UI to trigger that gate immediately instead of waiting for a tap. The decision itself
  was extracted into a pure `pendingEffectFor`, tested directly. The same commit unified
  three divergent tokenizers (`SecondBrainSearch`, `FactRelevance`, and the palette had
  each defined their own — two were byte-identical, the third genuinely different) into
  one shared `text/Tokenizer.kt`. **This commit's own CI run failed** — two assertions in
  the new `TokenizerTest` had incomplete expected sets (words the author's own hand-trace
  missed the first time), caught immediately by CI rather than assumed passing.
- **`398c0dd`** — Two more files the review flagged with zero coverage —
  `SkillSandbox` (the single enforcement point every skill execution passes through) and
  `TrustRepository` (owner of the exact auto-approve state `716cbcf` had to fix) — given
  the same pure-core-extraction treatment already used throughout this codebase, since
  neither is directly testable as written (`Context`, Room DAOs, `SharedPreferences`,
  `android.util.Log`). `TrustScoreEngine`, already pure, turned out to simply have no
  test at all despite computing the app's headline trust number.
- **`2d3fd2d`** — The `716cbcf` CI failure fixed: two `TokenizerTest` expectations
  corrected against a hand-traced, cross-checked-in-Python reproduction of the actual
  split/filter logic, and every other new test from the same commit re-verified line by
  line against its implementation before trusting it again.

---

## August 26 (continued) — Architecture review, round two: HISTORY reconciled against code

A second directive asked for the same discipline applied one level up: don't trust
`docs/HISTORY.md`'s own claims either — reconstruct real repository state, reconcile
every major claim against actual source, and only then decide what's next. Its stated
baseline (`aaa5fe2` as "latest") was itself three commits and a merge stale — `Main` was
already at `e9b2d6d`, PR #22 merged and green. Reported and corrected before proceeding,
per the standing rule that a HISTORY/repository disagreement gets reported before any
broad change is made.

### Reconciliation

A background sweep (Explore agent) plus direct verification of the highest-risk claims —
`NuaIntentRouter.route` (exactly one call site, `NuaViewModel.kt:452`), `SkillModule`'s
Hilt multibinding (12 skills, one per non-`CHAT` `NuaActionType`, no dynamic registration
path), and `autonomyTierFor` (an exhaustive `when` over all 13 `NuaActionType` values,
no `else` — the compiler forbids an unmapped action type, not a runtime check) —
confirmed the action-authorization boundary is genuinely what `ROADMAP.md` claims: one
path, closed set, no bypass found. No case was found anywhere of documentation language
("shipped ✅") overstating something that turned out absent — `email`/`WhatsApp`/Wear/Auto/
owner-voice-verification are all self-disclosed as scaffold in the same breath as the
claim, and the code matches.

One real, previously undocumented defect *was* found: **every WorkManager worker that
calls Claude returns `Result.success()` unconditionally**, never `Result.retry()`,
regardless of whether the call actually succeeded. `MemoryConsolidationWorker` compounded
this into data loss — on any Claude failure it still fell through to
`memoryDao.deleteMessagesByIds(batch)`, discarding up to 100 messages with no summary
ever written for them.

### Feature/Fix
Bounded retry for `DreamSynthesisWorker`, `GoalReviewWorker`, and
`MemoryConsolidationWorker` on Claude API failure; `MemoryConsolidationWorker` no longer
deletes a message batch it never got to summarize.

### Root Cause
All three workers used `(result as? ClaudeResult.Success)?.text ?: <fallback>` or
equivalent, collapsing "Claude said nothing worth keeping" and "the API call failed" into
the same code path. `GoalReviewWorker` calls Claude once per active goal with no
per-item checkpointing, and `GoalRepository.recordObservation` has no idempotency check —
a naive blanket retry would have duplicated any goal that already succeeded.

### Implementation
`ai/WorkerRetryPolicy.kt` — a single pure function, `outcomeForWorkerRun(madeProgress,
attempt, maxAttempts = 3)`, deciding `RETRY` vs `DONE` without any Android/WorkManager
type in its signature (`androidx.work.ListenableWorker.Result` needs the Android runtime
to reference at all, and this project has no Robolectric). Each worker computes its own
`madeProgress` and maps the result onto `Result.retry()`/`Result.success()` in one line.
`GoalReviewWorker`'s `madeProgress` is specifically `!anyCallFailed ||
anyObservationRecorded` — retry only a run that wrote nothing at all, never a run where
something already succeeded, so retrying can't duplicate a goal's observation.
`MemoryConsolidationWorker`'s delete is now reached only when `result is
ClaudeResult.Success`, on any path — including after retries are exhausted.

### Verification
`WorkerRetryPolicyTest` (7 cases: progress-made-is-always-done regardless of attempt
count, retry-while-attempts-remain, give-up-once-exhausted, the bound itself, and the
three `GoalReviewWorker` shapes — all-succeed-nothing-recorded, fully-failed,
partial-failure-with-something-written). Forward-reference audit clean.
`tools/forward_ref_audit.py` and `./gradlew test`/`assembleDebug` both green in CI at the
exact commit below — 11/11 steps, including the forward-reference audit and unit tests
explicitly confirmed as having run (not inferred from the overall conclusion).

### Commit
`8569458` — pushed to `claude/new-session-efg0ha`, **not yet merged to `Main`** as of
this entry (no merge was requested this round).

### Status
VERIFIED (fix itself, at its exact SHA) / the underlying `NuaViewModel` decomposition
this review recommends as the next architectural priority is PLANNED, not started.

## What this history is for

Two failures repeat in the record above, and both became process, not just fixes:

1. **A commit was pushed without its CI result being checked** (`32887d3` → five days
   red). `docs/ENGINEERING.md` now states plainly: a change is not verified until the
   *exact commit SHA* has passed CI, and that gate has held for every commit since.
2. **A verification tool reported a clean result it hadn't earned** (the forward-reference
   audit's first draft, and this document's own author misjudging two `TokenizerTest`
   expectations by hand). The audit now self-tests against a known defect before it's
   trusted; the tokenizer fix was re-verified against an independent Python reproduction
   rather than re-asserted from memory. "It looks right" and "it's been shown to be
   right" are treated as different claims throughout this project, on principle — not
   because either failure was catastrophic, but because the whole premise of
   `ROADMAP.md`'s ground rule (nothing is done until it's real, wired, and green) only
   holds if verification itself is held to the same standard as the code it checks.
