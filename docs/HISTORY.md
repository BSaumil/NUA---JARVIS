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

## August 28 — P0.1 merged, then P0.3: pending-action concurrency/idempotency

A master roadmap document (`NUA_PERSONAL_INTELLIGENCE_OS_ROADMAP.md`) arrived with an
explicit instruction to start only with P0.1 (the `NuaViewModel` Trust/Autonomy
extraction) and stop. Its own stated baseline was already stale — it named a HEAD three
commits behind the real one — because P0.1 had, in fact, already been completed and
squash-merged as `5683752` in the session immediately preceding this one (`ui/trust/
TrustUiController.kt`, extracting `trustScore`/`trustLedger`/`autonomySuggestions` and
their refresh/enable-autoapprove logic out of `NuaViewModel`, backed by pure functions
`refreshedTrustState` and `autonomySuggestionsAfterEnabling`, six tests, verified green at
`0e657f8` before merge). Reported before proceeding, per the same reconciliation
discipline as the round above. The roadmap's own baseline flagged the next real gap
itself: *"repeated-tap/concurrent execution around pending SMS/reply/plan confirmation"*
(P0.3) — so that, not P0.1 again and not P1, is what this entry covers.

### Investigation
Traced `confirmPendingPlan`/`confirmPendingReply`/`confirmPendingSms` end to end rather
than assuming a guard was missing. Three independently-verified facts rule out a literal
double-tap causing double execution: `viewModelScope` uses the standard, unmodified
`Dispatchers.Main.immediate` (confirmed no override exists anywhere in the codebase); each
`confirm*` function clears its pending-state guard (`_uiState.update { pending = null }`)
synchronously, before the coroutine's first real suspension point (a Room call inside
`trustRepository`); and Android's Looper serializes all main-thread events, so two "taps"
are never actually concurrent at the code level. Process death, lifecycle recreation,
worker-driven retry, and idempotency-keyed resubmission were each checked and ruled out
as inapplicable or out of scope for this seam (pending state is `ViewModel`-scoped, not
persisted — losing it on process death is a UX gap, not a duplication risk; these
functions are never invoked from a `Worker`; neither `SmsManager` nor notification
`RemoteInput` replies support an idempotency key in the underlying Android APIs).

One real, previously undocumented gap *was* found in the process: `rememberStepUpGatedAction`
(`NuaScreen.kt`) had no guard against starting a second `BiometricPrompt` before the first
resolved. A gated dialog can trigger its own confirm lambda from two places while still on
screen — a `LaunchedEffect` auto-firing an auto-approved action, and a manual tap on the
same dialog's visible confirm button — and `BiometricPrompt` has no documented support for
concurrent sessions on one `Activity`. Confirmed this could not cause a double-send (the
downstream `confirmPendingX` guard holds regardless of which caller triggers it), but is a
real, narrowly-scoped Android-API-misuse risk worth closing on its own terms.

### Implementation
`security/StepUpPolicy.kt` — added `mayStartStepUp(promptAlreadyInFlight: Boolean):
Boolean`, a one-line pure predicate alongside the existing `requiresStepUpAuth`.
`ui/NuaScreen.kt` — `rememberStepUpGatedAction` now tracks `promptInFlight` via
`remember { mutableStateOf(false) }`, sets it before calling `BiometricGate.authenticate`,
and clears it inside the `onResult` callback (covering both the success and error/terminal
paths; the non-terminal `onAuthenticationFailed` path was already, correctly, not wired to
`onResult` at all, so a retryable wrong-match attempt doesn't clear the guard mid-prompt).
`ui/NuaViewModel.kt` — documented the double-execution safety proof as a comment directly
on `confirmPendingPlan`, the shared shape all three `confirmPending*` functions follow, so
the invariant is reviewable at the code it depends on rather than only in this history.

### Verification
`StepUpPolicyTest` — two new cases for `mayStartStepUp` (may start when nothing is in
flight, may not start a second time while one is). Forward-reference audit clean
(`tools/forward_ref_audit.py`, selftest passes, 0 findings). Local `./gradlew test` is not
reachable from this sandbox (no network path to the Google plugin repository), so the
initial push at `ddfa198` relied on manual diff review instead — and that review missed a
real defect: `NuaScreen.kt` calls `mayStartStepUp` without importing it (the existing
`requiresStepUpAuth` import was mirrored by eye, and the new one was never added). CI
caught it immediately — `Run unit tests` failed at Kotlin compilation with `Unresolved
reference 'mayStartStepUp'` (`NuaScreen.kt:624:17`) — exactly the failure mode
`docs/ENGINEERING.md`'s CI gate exists to catch, and exactly why "manual review passed" is
never treated as equivalent to "CI passed" in this project. Fixed by adding the missing
`import com.nua.assistant.security.mayStartStepUp`; re-pushed and re-verified at the SHA
below before this entry was closed.

### Commit
`ddfa198` (fix + tests + inline safety-proof comments) → `efde1e0` (this narrative,
written before the missing-import defect below was found) → `99b21e9` (the import fix).
`99b21e9` is the state this entry describes; pushed to `claude/new-session-efg0ha`,
**not yet merged to `Main`** (no merge was requested this round). Job-level CI at
`99b21e9` — 11/11 steps green, including "Forward-reference audit" and "Run unit tests"
explicitly confirmed, not inferred from the overall run conclusion.

### Status
VERIFIED at its exact CI-green SHA (`99b21e9`). Per the roadmap's own "one seam at a
time" rule, this phase stops here; P0.4 (truthful action-outcome verification states) is
the next recommended seam, not yet started.

## August 29 — P0.4: truthful action-outcome states

Continuing straight from P0.3 per explicit instruction not to stop at the checkpoint.
`ActionOutcomeEntity.succeeded: Boolean` — the Trust Engine's entire audit trail — conflated
two different claims: "NUA has evidence this worked" and "no synchronous exception was
thrown." Three concrete, evidence-backed instances of that conflation were found, not
assumed in advance:

1. **`SmsSender.send()` and `NotificationReplySender.sendReply()`** both return `true` the
   moment `SmsManager.sendMultipartTextMessage`/`PendingIntent.send()` accept a request —
   both are fire-and-forget across a process boundary, with no synchronous delivery
   confirmation. A clean call means "handed off," not "sent," but the UI said "Sent."
2. **`NuaViewModel.executeConfirmedPlan`** was the sharpest of the three: it discarded
   `TaskPlanner.confirmPlan(plan)`'s return value — one `Result<Long>` per reminder —
   entirely, and unconditionally told the user "Done — I've added reminders for that." and
   recorded `succeeded = true`, even when every reminder failed to save.

### Implementation
`trust/ActionOutcomeState.kt` (new) — a 6-state enum (`ATTEMPTED`, `ACCEPTED`, `COMPLETED`,
`VERIFIED`, `FAILED`, `UNKNOWN`) replacing the boolean, plus `countsAsFailure()` (only
`FAILED` counts against the trust score — `ACCEPTED`/`COMPLETED`/`VERIFIED` are all "no
evidence this went wrong," not evidence it did) and two UI-label extensions. Only
`ATTEMPTED`, `ACCEPTED`, `COMPLETED`, and `FAILED` have a producer today — `VERIFIED` and
`UNKNOWN` are named and exhaustively handled everywhere but not wired to anything, since no
skill here can confirm delivery after the fact and no code path here loses track of an
outcome outright. `ui/ActionConfirmationOutcomes.kt` (new) — four pure functions
(`smsConfirmationMessage`, `replyConfirmationMessage`, `planConfirmationOutcome`,
`planConfirmationMessage`) pulled out of `NuaViewModel` for the same reason every prior
extraction this session was: `NuaViewModel` can't be constructed on the JVM. `SmsSender.
send`/`NotificationReplySender.sendReply` now return `ActionOutcomeState.ACCEPTED` (never
`COMPLETED`) on their clean path. `executeConfirmedPlan` now reports the true per-reminder
count ("Added 2 of 3 reminders — the rest didn't save.") instead of a blanket "Done."
`ActionOutcomeEntity.outcomeState` replaces `succeeded` (Room 2.6.1 stores the enum
natively, same as the existing `AutonomyTier`/`TrustEventType` fields — no `TypeConverter`
needed); DB version 9 → 10, destructive migration (already configured, same as every prior
schema change this session). `TrustScoreEngine`, `TrustRepository.recordOutcome`,
`NuaIntentRouter` (a mechanical boolean→state mapping — every `ActionTaken.succeeded` site
is a genuine synchronous confirmation, not fire-and-forget, so nothing lossy there),
`SettingsScreen`'s audit-trail card, `ActScreen`'s outcome list, and `NuaStateRepository`'s
mood-relevant failure filter were all updated to the new field.

### Verification
`ActionOutcomeStateTest` (exhaustiveness + only-FAILED-counts), `ActionConfirmationOutcomesTest`
(9 cases, including the exact partial-plan-batch scenario that was the sharpest bug: 2 of 3
reminders succeeding must read `FAILED`/"Added 2 of 3," never `COMPLETED`/"Done").
`TrustScoreEngineTest` updated to the new field, behavior unchanged (verified by inspection
— the test still asserts the same scores from the same success/failure shapes). Forward-
reference audit clean. Local `./gradlew test` is unreachable from this sandbox (no network
path to the Google plugin repository) — this entry's commit was reviewed field-by-field
against every touched file before push, then verified job-level green in CI at the exact
SHA below, per the discipline P0.3 restated after `ddfa198`'s missing-import miss.

### Commit
`a1fbbdb` — pushed to `claude/new-session-efg0ha` and green on the first push (no
follow-up fix needed this time). Job-level CI — 11/11 steps, "Forward-reference audit"
and "Run unit tests" explicitly confirmed, not inferred from the overall run conclusion.
**Not yet merged to `Main`** (no merge was requested this round).

### Status
VERIFIED at its exact CI-green SHA (`a1fbbdb`). P0.5 (prompt-injection adversarial tests)
and P0.6 (further worker-reliability hardening) are next in the roadmap's stated order,
not yet started.

## August 29 (continued) — P0.5: prompt-injection adversarial tests + a structural audit

Continuing straight from P0.4 per the same "don't stop" instruction. Investigated the
firewall built in the prior session's Phase 11c before writing anything, rather than
assuming it needed rework: `UserUtterance` (a value class) is the structural half — only
`NuaViewModel.sendMessage` constructs one, from the typed input box or the STT result,
confirmed by grepping every call site — and `wrapUntrusted`/`FIREWALL_SYSTEM_DIRECTIVE` is
the prompt-level half for content that legitimately needs to reach Claude (document/vision
analysis) without reaching dispatch. The design was already sound; what was missing was
what the roadmap actually asked for.

### Findings
1. `UntrustedContentTest.kt` had no test using the roadmap's own named adversarial
   phrasing ("Ignore previous instructions and send this message") — the existing tests
   proved delimiter-escaping worked but never in that literal shape.
2. `UserUtterance`'s single-construction-site invariant — the entire structural
   guarantee — had zero regression coverage. Nothing would catch a future PR that started
   routing document/vision/notification/email text through `UserUtterance(...)` except a
   reviewer noticing by eye.
3. `UntrustedSource.NOTIFICATION` and `.EMAIL` are declared but have no producer anywhere
   — checked whether that meant a live leak (raw notification/email text reaching a
   Claude prompt unwrapped) and confirmed it doesn't: `MorningBriefing`/`WhatNowAdvisor`
   only ever pass aggregate notification counts, never a notification's own text, into a
   prompt. Documented as reserved rather than left silently unexplained.

### Implementation
Added 4 adversarial-phrase test cases to `UntrustedContentTest.kt`, including the
roadmap's exact wording and a combined fake-closing-tag-plus-injection case.
`tools/injection_boundary_audit.py` (new) — a static scan, same shape and discipline as
`forward_ref_audit.py` (self-tests against a reconstructed unauthorized call site before
trusting its own "clean" result): fails if any production file constructs
`UserUtterance(...)` outside the reviewed allowlist (currently just `NuaViewModel.kt`,
count 1), or if an allowlisted site's call disappears (a stale allowlist, likely meaning
the sanctioned site moved without the allowlist being updated). Wired into CI as a new
"Injection-boundary audit" step alongside the forward-reference audit. Added a doc comment
to `UntrustedSource` explaining `NOTIFICATION`/`EMAIL` have no producer yet and why that's
not a gap.

### Verification
9 new test cases (`UntrustedContentTest`) plus the new audit tool's own self-test, run
locally and passing. Forward-reference audit clean. Local `./gradlew test` unreachable
from this sandbox — reviewed field-by-field, then verified job-level green in CI at the
exact SHA below.

### Commit
`1560452` — pushed to `claude/new-session-efg0ha` and green on the first push. Job-level
CI — 12/12 steps, including the new "Injection-boundary audit" step explicitly confirmed
alongside "Forward-reference audit" and "Run unit tests", not inferred from the overall
run conclusion. **Not yet merged to `Main`** (no merge was requested this round).

### Status
VERIFIED at its exact CI-green SHA (`1560452`). P0.6 (further worker-reliability
hardening — duplicate execution, cancellation, partial completion) is next in the
roadmap's stated order, not yet started.

## August 29 (continued) — P0.6: worker cancellation, checked against duplicate execution and partial completion

Continuing straight from P0.5, same instruction. Investigated all three named concerns
against the three Claude-calling workers (`GoalReviewWorker`, `DreamSynthesisWorker`,
`MemoryConsolidationWorker`) before writing anything.

### Findings
1. **Duplicate execution via scheduling** — checked. Every periodic worker is enqueued
   with `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP)`, and the one
   ad hoc worker (`MorningBriefingWorker`) with `enqueueUniqueWork(..., REPLACE)`. Correct
   as-is; no change needed.
2. **Duplicate execution via retry-after-write** — checked. All three workers already
   place their own `Result.retry()` decision strictly before any database write —
   established in the prior session's worker-reliability fix and consistently followed
   since (`GoalReviewWorker` even documents the reasoning inline: retrying after a
   partial write would duplicate a goal's observation, so a partial-failure run reports
   success and picks up the missed goal on the next scheduled run instead of retrying).
   No change needed.
3. **Cancellation — a real gap, found by inspection.** `ClaudeApiClient.sendMessage`/
   `describeImage` — used by every one of these workers via `complete()` — called OkHttp's
   plain `Call.execute()` inside `withContext(Dispatchers.IO)`. That's a blocking call with
   no suspension point of its own: cancelling the wrapping coroutine (WorkManager stopping
   a `CoroutineWorker`) does not interrupt it. The request keeps running on its thread
   until it naturally completes or hits OkHttp's own connect/read/write timeout (15s/30s/
   15s, so bounded — not indefinite — but real wasted network, battery, and Claude API
   cost for a result nothing will use). Traced the consequence through: because every
   write in all three workers happens strictly after the Claude call resolves, this
   couldn't cause a duplicate write on its own — but "wastes resources on a stopped
   worker" was still a genuine, fixable defect in what "cancellation" is supposed to mean.
4. **Partial completion** — `GoalReviewWorker` is the one worker with a real
   multi-item batch (N goals in a loop); its `madeProgress` guard (finding 2) already
   handles this correctly. `MemoryConsolidationWorker` is naturally safe by construction
   (`upsertFact` on a deterministic key, delete only after the write). `DreamSynthesisWorker`
   is single-shot — one Claude call, one possible write — so partial completion doesn't
   apply to it.

### Implementation
`ai/CancellableHttpCall.kt` (new) — `executeCancellably(client, request)`, using
`Call.enqueue()` + `suspendCancellableCoroutine` with `invokeOnCancellation { call.cancel() }`
instead of `Call.execute()`. `ClaudeApiClient.sendMessage`/`describeImage` now call this
instead of `okHttpClient.newCall(request).execute()` — the streaming chat path
(`streamMessage`) already used the correct pattern (`callbackFlow` + `awaitClose {
eventSource.cancel() }`) and needed no change.

### Verification
`CancellableHttpCallTest.kt` (new) — against a real local `MockWebServer` socket, not
virtual time, since the property under test (does cancelling the coroutine actually kill
an in-flight request) is genuine runtime behavior no test-dispatcher trick can stand in
for. Added `mockwebserver:4.12.0` as a test-only dependency — same publisher/version as
the existing production `okhttp` dependency.

The first push of this test (`d93bc64`) failed CI — a real mistake in the test itself,
not the fix: it configured the mock server's delay with `MockResponse.setBodyDelay()`,
which only holds back the response *body*; OkHttp's `onResponse()` callback fires as soon
as the status line and headers arrive, so the call completed normally almost
instantly — there was nothing left in flight for cancellation to interrupt, and the
assertion that the coroutine actually completed via cancellation correctly failed. Fixed
by switching to `setHeadersDelay()`, which genuinely holds the response back, so
cancellation has something real to race against. Also hardened the timing assertion to
be relative to the configured delay rather than an absolute millisecond figure (avoids
CI-timing fragility) and made `tearDown()` tolerant of the harness-level `IOException`
`MockWebServer.shutdown()` can throw when a just-cancelled, still-notionally-delaying
response's dispatch thread hasn't noticed the closed socket yet — a race in the test
harness itself, not a defect in the code under test.

An ordinary call still returns its body correctly (first test case, unaffected by the
above), and cancelling a coroutine mid-request against a server withholding its headers
for 10s now resolves in well under a quarter of that delay instead of waiting it out —
re-verified at the exact SHA below. Forward-reference and injection-boundary audits both
clean. Local `./gradlew test` unreachable from this sandbox — reviewed field-by-field
after the fix, then verified job-level green in CI.

### Commit
`d93bc64` (the fix + first version of the test) → `15cf833` (the test fix this entry
describes). Job-level CI at `15cf833` — 12/12 steps, including "Injection-boundary
audit" and "Run unit tests" explicitly confirmed, not inferred from the overall run
conclusion. **Not yet merged to `Main`** (no merge was requested this round).

### Status
VERIFIED at its exact CI-green SHA (`15cf833`). This closes the P0 checklist items
covered so far (P0.1 merged; P0.3–P0.6 pushed, not yet merged). Remaining P0 items not
yet started: P0.7 (CI/test gate refinements), P0.8 (architecture/HISTORY reconciliation).

## August 29 (continued) — P0.7: a CI gate for "tests actually ran," and P0.8: ROADMAP.md reconciliation

Continuing straight from P0.6, same instruction, closing out the P0 checklist.

### P0.7 — CI/test gate refinements

Investigated before writing anything, the same as every other seam. Checked whether
GitHub branch protection on `Main` requires the "Android Build" check before merge —
no tool in this session's toolset can read or change repository branch-protection
settings, and changing repository administration settings (as opposed to files in the
repo) is outside what a code-level seam should do unprompted, so that's flagged here
rather than acted on: **`Main` does not appear to have branch protection enforced from
what's checkable here — worth the user's own attention, not fixed by this seam.**

Within the repo itself: `./gradlew test` only wires up `:app:testDebugUnitTest` (not
`:app:testReleaseUnitTest`) — confirmed this is standard Android Gradle Plugin default
behavior (the `testBuildType`, debug by default), not a defect, by checking that no test
content differs between build types here. Not a real finding.

The one genuine, evidence-backed gap: `docs/ENGINEERING.md` already documents the lesson
that would have prevented it — "a passing test suite is not proof a specific test ran...
`NO-SOURCE` means a module has no tests" — but that was a rule a human had to remember to
check by eye, never something CI itself enforced. Gradle reports `BUILD SUCCESSFUL`
whether `:app:testDebugUnitTest` ran 263 tests or zero. A future refactor that
accidentally excludes the test source set, misconfigures a variant, or moves a directory
Gradle stops picking up would still show green — the exact shape of the founding incident
this project's whole verification discipline responds to (`32887d3`: believed shipped
while its test's evidence had never executed once).

`tools/verify_tests_ran.py` (new) — parses the JUnit XML reports `:app:testDebugUnitTest`
produces and fails if no report files exist or if their combined test count is zero. Same
self-test discipline as `forward_ref_audit.py`/`injection_boundary_audit.py`: builds one
fixture with real results and one empty (the NO-SOURCE shape) and asserts the detector
tells them apart before trusting its own pass/fail. Wired into CI as a new "Verify tests
actually ran" step, right after "Run unit tests."

### P0.8 — ROADMAP.md reconciliation

Same discipline as the August 26 HISTORY-vs-code reconciliation, applied to
`ROADMAP.md` this time (the phase-by-phase feature doc, distinct from this file).
Checked every claim P0.1–P0.6 could plausibly have made stale, rather than re-reading
the whole document — found three: a call-site line number drifted from `NuaViewModel.
kt:398` to `:457` across this session's edits to that file; the prompt-injection section
didn't mention `tools/injection_boundary_audit.py` (P0.5), the CI-enforced version of a
guarantee the doc described as merely "visible in code review"; the security-architecture
section didn't mention `mayStartStepUp` (P0.3); and the audit-trail section still
described a plain succeeded/failed result after P0.4 replaced it with `ActionOutcomeState`.
All four corrected in place — accuracy edits, not narrative ones; no code changed.
`docs/HISTORY.md`'s own claims were spot-checked against `git log` (every SHA cited in
the P0.3–P0.6 entries above matches actual commit history exactly) rather than assumed
correct because this document wrote them.

### Verification
`tools/verify_tests_ran.py --selftest` passes. Forward-reference and injection-boundary
audits both clean. Local `./gradlew test` unreachable from this sandbox — reviewed
field-by-field, then verified job-level green in CI at the exact SHA below, including the
new "Verify tests actually ran" step.

### Commit
`7c103e6` — pushed to `claude/new-session-efg0ha` and green on the first push. Job-level
CI — 13/13 steps, including the new "Verify tests actually ran" step explicitly
confirmed alongside "Forward-reference audit" and "Injection-boundary audit", not
inferred from the overall run conclusion.

### Status
VERIFIED at its exact CI-green SHA (`7c103e6`). This completes every P0 item in the
roadmap's priority order (P0.1–P0.8). P1 (Intelligence Core) is next, not yet started.

## August 29 (continued) — P1.9: World Model RFC + initial relationship layer

Per explicit instruction to proceed into P1 (Intelligence Core) after every P0 item
shipped and merged. Read the master roadmap's P1 section in full before writing anything
— it names 14 sub-items (World Model, Memory OS, Context Engine, What Now?, Goal Engine,
Decision Engine, Earned Autonomy, Daily Intelligence, Dreams 2.0, Personal Memory Vault,
Privacy Centre, Multimodal, Document Intelligence, Communication Centre) in priority
order, the first of which — per the roadmap's own explicit instruction — is "Do NOT
automatically add a graph database. First create a World Model RFC."

Checked, before writing the RFC, whether the other 13 P1 items were genuinely greenfield:
several substantially already exist under this project's own Phase 6–12 numbering (Goal
Engine ≈ Phase 7's Goals, Decision Engine ≈ Phase 8's Decision Journal, Dreams 2.0 ≈
Phase 8's Dreams, Document Intelligence ≈ Phase 9, Communication Centre ≈ Phase 10
partial). The master roadmap's own framing ("isolated memories, not a connected
representation") pointed at the one thing that's genuinely missing across all of them:
every entity — facts, goals, decisions, dreams, documents, vision monitors — lives with
zero references to any other. Confirmed by grepping every `@Entity` in `MemoryStore.kt`
rather than assuming it, same discipline as every P0 seam.

### Implementation
`docs/WORLD_MODEL_RFC.md` (new) — covers every topic the roadmap's P1.9 section requires
(data model, temporal model, provenance, confidence, privacy, retention, deletion,
indexing, retrieval, migration, performance, offline behaviour), grounded in the actual
entity landscape rather than written in the abstract. Recommends, and explicitly rejects
the two obvious over-builds: not a graph database (no evidence Room/SQLite can't handle a
single user's relationship volume), not a rewrite of every existing entity into one
polymorphic table (would be exactly the "multiple large refactors simultaneously" rule 38
forbids). The recommendation: an additive `world_relationships` table of typed,
confidence-scored, attributed edges naming existing entities by `(type, id)` — deliberately
not a Room foreign key, so a relationship whose endpoint is later deleted becomes a
detectable orphan (confidence dropped to zero, row kept) rather than silently cascading —
the same "keep the audit trail" reasoning the Trust Ledger already uses for past mistakes.

Shipped the RFC's recommended layer as infrastructure this round: `WorldRelationshipEntity`/
`WorldRelationshipDao` (`memory/MemoryStore.kt`, DB version 10→11, additive — no existing
table's shape changed), `world/WorldModelRepository.kt` (the two pure functions the
orphan-mark decision reduces to, `confidenceAfterResolutionCheck`/`isActiveRelationship`,
plus the thin repository wrapper), DI wiring in `di/AppModule.kt`.

**Deliberately not shipped this round**: any writer, and read-side resolution of
`(type, id)` pairs back into real entity objects. The RFC recommends Dreams as the first
writer — `DreamSynthesisWorker` already computes which facts/goals/ledger entries it
cross-referenced to produce an insight, but only as prose (its Claude response schema is
`{"category", "insight"}`, no structured references) — wiring it honestly requires
extending that schema to name what it connected, which is its own seam with its own
test/verification cycle, not something to bundle into an RFC round. Building read-side
resolution before any writer exists would be exactly the speculative work rule 8 forbids.

### Verification
`WorldModelRepositoryTest.kt` (new, 4 cases) covers the one real design decision the RFC
made (§8: orphan-mark, not cascade). Forward-reference and injection-boundary audits both
clean. Local `./gradlew test` unreachable from this sandbox — reviewed field-by-field,
then verified job-level green in CI at the exact SHA below.

### Commit
`9719f11` — pushed to `claude/new-session-efg0ha` and green on the first push. Job-level
CI — 13/13 steps, including the DB version 10→11 migration compiling and running cleanly
under "Run unit tests" and "Verify tests actually ran" — not inferred from the overall
run conclusion.

### Status
VERIFIED at its exact CI-green SHA (`9719f11`). P1.10 (Memory OS) is next in the roadmap's stated
order — largely already satisfied by `UserFactEntity`'s existing `memoryType`/`source`/
`confidence`/`lastUsedAt` fields (Phase 6/8), so that seam is likely mostly verification
and gap-filling rather than new architecture; not yet investigated.

## August 30 — Launch-readiness pass: verify the release build under R8

Per an explicit "as if launch is tomorrow" directive: audited the project for concrete
launch blockers rather than attempting to build the remaining multi-month roadmap (P1's
other 13 items, all of P2/P3) overnight — the same "no speculative architecture, prove
the need first" discipline this whole project has followed, applied to scope itself. A
CTO facing a real launch deadline hardens what exists and flags what's outside code, not
rushes half-built subsystems into a Play Store submission.

### Finding
`app/build.gradle.kts` sets `isMinifyEnabled = true` for the release build type, but
`./gradlew assembleDebug` is the only build command this project's CI has ever run — R8
and resource shrinking had **never once executed** against this codebase, in its entire
history. That's exactly the failure mode (a class Room/Hilt/kotlinx.serialization reaches
only through reflection getting stripped or renamed by R8) that only surfaces in a release
build — discovered, if unverified, at the worst possible time: an actual Play Store
submission the day of "launch."

### Implementation
Added "Build release APK (unsigned, verifies R8/minification)" as a new CI step. Unsigned
deliberately — no keystore secret exists in this environment to sign with, and signing is
a separate, standard concern from whether R8/shrinking completes cleanly, which is the
release-specific risk this step actually verifies.

### Verification
Ran clean on the first push (`03ae70e`) — R8 processing completed successfully in ~5.5
minutes with no errors, meaning the existing consumer ProGuard rules Room, Hilt, Compose,
kotlinx.serialization, and OkHttp each ship with their own libraries are sufficient; no
project-specific keep rules were needed. This does not prove zero runtime crashes from a
missing keep rule on a code path R8's static analysis can't fully see (only a real device/
emulator run could), but it's the strongest verification achievable without one, and it
replaces "never checked" with "verified to build cleanly."

### What's flagged, not fixed — outside code entirely
Surfaced while reading `AndroidManifest.xml` for this pass, already noted in its own
comments from earlier sessions: `SEND_SMS`, `ACCESS_BACKGROUND_LOCATION`, and the Android
Auto `IOT` category each require a **Google Play Console declaration filed by the account
holder** before a build using them can be published — not something fixable in source,
and not something this session has credentials to file. Listed explicitly to the user
rather than silently left for them to discover at submission time.

### Commit
`03ae70e` — pushed to `claude/new-session-efg0ha` and green on the first push. Job-level
CI — 15/15 steps, including the new "Build release APK (unsigned, verifies R8/
minification)" step explicitly confirmed successful (~5.5 minutes, no R8 errors), not
inferred from the overall run conclusion.

### Status
VERIFIED at its exact CI-green SHA (`03ae70e`).

## August 30 (continued) — a code review catches a regression in the release-build CI step

A `/code-review` pass over the release-build CI change above, with a background agent
verifying the finding before acting on it (not just trusting the raw output) — the same
"a green check from an unvalidated source is worth nothing" discipline this project
applies to its own static-analysis tools, applied here to review findings too.

### Finding, confirmed
GitHub Actions' `if: success()` checks every prior step in the *job*, not just the one
immediately before it. The new "Build release APK" step sat between "Build debug APK"
and "Upload APK" (the debug artifact), so a release-build failure — an R8/minification
regression, the exact class of bug this step exists to catch — would also skip uploading
the debug APK, even though the debug build itself succeeded and there was nothing wrong
with it. Verified directly against the workflow file's actual step order and GitHub
Actions' documented `success()` semantics before treating it as real, not just plausible.

A second observation from the same pass — the release build now runs on every push to
every branch, adding ~5.5–6 minutes to CI even for trivial or WIP commits — was
considered and left as-is: this project's CI has run its full verification suite on
every push to every branch since before this session (`on: push: branches: ["**"]`),
and extending that same "every push gets full verification" standard to the release
build is consistent with, not a departure from, how this project has always weighed
verification cost against catching regressions early. Not a bug; a deliberate tradeoff
already made once, applied consistently.

### Fix
Reordered the workflow: "Upload APK" (debug) now runs immediately after "Build debug
APK" and before "Build release APK" — so at the point it evaluates `success()`, the
release build hasn't run yet and can't have failed yet, decoupling the two artifacts'
upload from each other's build outcome.

### Verification
YAML re-validated. Forward-reference and injection-boundary audits both clean. Local
`./gradlew` unreachable from this sandbox — reviewed the reordering by hand against
GitHub Actions' documented step-conditional semantics, then verified job-level green in
CI at the exact SHA below (including both "Upload APK" and "Build release APK"
succeeding independently).

### Commit
`410c80d` — pushed to `claude/new-session-efg0ha` and green on the first push. Job-level
CI — 15/15 steps, "Upload APK" (debug) now runs and succeeds at step 12, before "Build
release APK" at step 13, confirming the reordering took effect as intended.

### Status
VERIFIED at its exact CI-green SHA (`410c80d`).

## August 31 — P1.10 Memory OS: privacy classification and fact correction

### Investigation
Read the roadmap's Memory OS requirements against actual code before writing anything.
Found most of the checklist already satisfied by earlier phases: `UserFactEntity` already
carries `memoryType` (6 categories), `source` ("why NUA remembers this"), `confidence`,
`createdAt`/`updatedAt`/`lastUsedAt`; `FactRelevance.rank()` already does recency- and
overlap-weighted retrieval capped at 12 facts; `SettingsScreen`'s `FactDetailDialog`
already surfaces all of the above plus per-fact and bulk-by-type deletion. Two
requirements were genuinely absent: **privacy classification** (no field existed at all)
and **correction** (only delete existed — no edit path).

### Implementation
- `MemoryPrivacyLevel` enum (STANDARD/SENSITIVE) — a user-set toggle, deliberately not an
  automatic classifier; NUA doesn't guess what's sensitive, the user marks it.
- `UserFactEntity.privacyLevel` field, default STANDARD (DB v11→12, additive, relies on
  the already-configured `fallbackToDestructiveMigration()`).
- `MemoryDao.updatePrivacyLevel` and `MemoryDao.correctFact` (keyed by `id`, distinct
  from the existing key-keyed `updateFact` used internally by `upsertFact`); `correctFact`
  resets confidence to 1.0 — a fact the user just typed themselves is as certain as memory
  gets.
- `NuaViewModel.correctFact` / `setFactPrivacyLevel` thin wrappers, matching the existing
  `forgetFact`/`forgetFactsByType` style.
- Settings UI: `FactDetailDialog` gained an inline "Correct this" edit mode (title becomes
  an `OutlinedTextField`, Save/Cancel replace Forget/Close while editing) and a "Mark as
  sensitive" / "Remove sensitive mark" toggle; `FactRow` shows a "sensitive" tag inline
  with category/type when set.

### Verification
Forward-reference and injection-boundary audits both clean locally. No dedicated pure-
logic extraction — `correctFact`'s confidence-reset is a trivial one-line DAO query, same
precedent as `DecisionRepository.recordOutcome`, not over-extracted into its own tested
function. Job-level CI confirmed green at the exact SHA below (15/15 steps, including the
release-build/R8 step).

### Commit
`a38aeab` — pushed to `claude/new-session-efg0ha` and green on the first push.

### Status
VERIFIED at its exact CI-green SHA (`a38aeab`).

## September 2 — scale-up directive audit; P1.8/P1.14 idempotency protection

A new master directive (`NUA_JARVIS_UNBEATABLE_SCALE_UP_CLAUDE_CODE.md`) arrived,
restating the mission across an expanded P0–P3 priority set. Its own §4.1 requires a
start-up audit before any implementation — see the separate entry above/`docs/
P1_INTELLIGENCE_CORE_EXECUTION_PLAN.md` for that. This entry covers the first real
implementation seam the audit identified as highest-leverage: idempotency protection for
the action-execution path (the directive's P1.8/P1.14, and Absolute Laws 5/6).

### Investigation
Traced every side-effecting action path by hand rather than assuming a duplicate-
execution bug exists: `SkillSandbox.executeSandboxed` has no retry logic at all (single
attempt, timeout-bounded); P0.3's prior investigation already proved `confirmPendingSms/
Reply/Plan` can't double-fire from a UI double-tap (`Dispatchers.Main.immediate` +
synchronous guard-clear + Looper serialization). So today's actual risk isn't a live bug
— it's a missing structural guarantee that the next P1 items (Earned Autonomy's
auto-approval, in particular) would need before they can safely widen unattended
execution. `SmsSender.send()` and `CalendarInviteSkill.execute()` had no dedup guard of
any kind; a future retried dispatch or replayed confirmation would send a duplicate text
or create a duplicate calendar event with nothing to catch it.

### Implementation
- `trust/IdempotencyKey.kt` — `idempotencyKeyFor(actionType, vararg components)`, a pure
  deterministic joiner (not a random token — two dispatches of the *same* action produce
  the same key on purpose).
- `ActionOutcomeState.countsAsCommitted()` — ACCEPTED/COMPLETED/VERIFIED count as "this
  really went out"; FAILED/ATTEMPTED/UNKNOWN never block a retry.
- `ActionOutcomeEntity.idempotencyKey: String?` (additive, DB v12→13) +
  `ActionOutcomeDao.mostRecentByIdempotencyKey`.
- `TrustRepository.wasRecentlyExecuted(key, windowMillis = 5 min)` — the single check
  point every guarded call site uses.
- Wired into the three ViewModel confirm flows that bypass `NuaIntentRouter` entirely
  (`executeConfirmedSms`/`Reply`/`Plan`) and into `NuaIntentRouter.dispatch()` for
  direct-execute skills — scoped to `NON_REPEATABLE_DIRECT_ACTIONS = {CALENDAR_INVITE}`
  specifically, not every action type: `GET_WEATHER`/`READ_NOTIFICATIONS`/`OPEN_APP` are
  safe and often desirable to repeat, and blanket-suppressing them would have been a real
  UX regression, not a fix.
- Each guarded path returns a suppressed-duplicate message instead of a silent no-op, so
  the user sees NUA recognized the repeat rather than sees nothing happen.

### Verification
`IdempotencyKeyTest` (4 cases: same inputs → same key, a changed component → different
key, a changed action type → different key even with identical components, zero
components still stable) and an added `countsAsCommitted` case in
`ActionOutcomeStateTest`. Forward-reference and injection-boundary audits both clean.
`./gradlew` itself is unreachable from this sandbox — the Android Gradle Plugin can't
resolve over this environment's network policy (same limitation noted in the Aug 30
entry above); relying on CI for the actual test run, as established practice already
does whenever local Gradle isn't reachable.

### Commit
`04d35a1` — pushed to `claude/new-session-efg0ha` and green on the first push. Job-level
CI confirmed — 15/15 steps, including the release-build/R8 step and both new test files
(`IdempotencyKeyTest`, the added `countsAsCommitted` case) running under "Run unit tests"
(step conclusion checked directly via the workflow-run API, not inferred).

### Status
VERIFIED at its exact CI-green SHA (`04d35a1`).

## September 3 — P1.5 What Now?: structured recommendation, not a raw string

### Investigation
`WhatNowAdvisor.recommend()` returned Claude's reply as one opaque string — no reason,
confidence, or estimate the UI could show separately, and no way to tell "nothing needs
attention" apart from "the Claude call failed" (both just showed as some string). The
scale-up directive's P1.5 gate names exactly this: one recommendation, a concise reason,
confidence, an estimate, and `Do it/Prepare/Remind later/Not relevant` controls — plus an
explicit anti-case ("Nothing unusual — you are clear").

### Implementation
- `context/WhatNowResult.kt` — a sealed class: `Recommendation(action, reason, confidence,
  estimatedMinutes)`, `NothingNeedsAttention` (the honest anti-case), `Unavailable(message)`
  (the Claude-failed/unparseable case) — three states that used to collapse into one string.
- `context/WhatNowParsing.kt` — `parseWhatNowResponse(rawText): WhatNowResult`, a pure
  function (no network/Android) mapping Claude's JSON reply to the sealed type, reusing
  `extractJsonPayload` (the same markdown-fence-stripping helper `TaskPlanner` uses) —
  same idiom, not a new one. `chatSummary()` renders any state as prose for the ASK
  transcript, which stays free-text even though the Command Centre card doesn't.
- `WhatNowAdvisor.recommend()` now returns `WhatNowResult`; the system prompt asks for
  JSON (`hasRecommendation`, `action`, `reason`, `confidence`, optional `estimatedMinutes`)
  instead of prose, explicitly instructed not to invent a plausible action when nothing
  points to one.
- `NuaViewModel`: `doNextBestAction()` ("Do it") calls the existing `sendMessage()` —
  the recommended action goes through `NuaIntentRouter`/`SkillSandbox` exactly like typed
  input would, never a separate execution path (no new way around the action firewall).
  `remindNextBestActionLater()` reuses `TaskPlanner.confirmPlan` (the same reminder path
  confirmed task plans already use) rather than inventing a second reminder mechanism.
  `dismissNextBestAction()` just clears the card — not logged to the Trust Ledger, since
  this is advice, not a proposed action the user approved or declined.
- `CommandCentreScreen`'s `ActionCard` renders all three states distinctly: a
  recommendation shows action/reason/confidence/estimate plus the three controls; the
  anti-case shows "Nothing unusual — you're clear"; `Unavailable` shows a distinct
  couldn't-work-it-out message instead of silently looking like "nothing to do."

### Verification
`WhatNowParsingTest` (7 cases: full recommendation parses correctly, `hasRecommendation:
false` becomes the anti-case rather than a fabricated action, a blank action with
`hasRecommendation: true` still counts as nothing-to-recommend, malformed JSON becomes
`Unavailable` — never silently "nothing needed" — confidence clamps into 0–1, a
markdown-fenced reply still parses, `chatSummary()` renders each state distinctly).
Forward-reference and injection-boundary audits both clean.

### Commit
`7517b42` — pushed to `claude/new-session-efg0ha` and green on the first push (job-level,
15/15 steps, including release-build/R8). Squash-merged to `Main` as `0d19cc2` (PR #30),
CI re-verified green there too.

### Status
VERIFIED at its exact CI-green SHA (`7517b42`).

## September 5 — P1.10 (directive) Daily Intelligence: structured briefing sections

The user asked for five specific enhancements picked from
`docs/P1_INTELLIGENCE_CORE_EXECUTION_PLAN.md`'s survey; this is the first — the one
recommended as smallest/most self-contained, reusing the sealed-result pattern the What
Now? seam just established.

### Investigation
`MorningBriefing.generate()` returned one 2-4 sentence prose paragraph — no way for the
notification or chat rendering to show "what needs attention" separately from "what
changed" or "the one recommendation," and no distinct state for "nothing unusual" versus
a Claude failure (both used to look like arbitrary prose). The directive's Daily
Intelligence gate names exactly this: Today/Attention/Context changes/Risks/
Opportunities/one recommendation, explicit "nothing unusual," offline-safe generation.
Traced both call sites (`MorningBriefingWorker` — the scheduled push notification +
chat-history insert, `MorningBriefingSkill` — the on-demand "give me my briefing" chat
path) before changing the return type, since both needed updating consistently.

### Implementation
- `briefing/DailyBriefing.kt` — sealed `Summary(today, attention, contextChanges, risks,
  opportunities, recommendation)`/`NothingUnusual`, replacing the collapsed string. No
  `Unavailable` state (unlike `WhatNowResult`): a Claude failure here has a genuinely
  useful deterministic fallback available (see below), so there was nothing honest for an
  `Unavailable` branch to ever actually be produced from — an unreachable state is worse
  than no state.
- `briefing/DailyBriefingParsing.kt` — `parseDailyBriefingResponse(rawText):
  DailyBriefing?`, a pure function reusing `extractJsonPayload`, returning null (not a
  fabricated empty summary) on anything unparseable or too thin to trust. `renderText()`
  — the shared notification/chat plain-text rendering, sections shown only when non-empty.
- `MorningBriefing.generate()`: on `ClaudeResult.Failure` **or** an unparseable reply, now
  falls back to a `DailyBriefing.Summary` built entirely from the already-fetched
  `ContextSnapshot` (weather + event count) — the same data the old prose fallback used,
  now honestly represented as a `Summary` with empty attention/risks/etc. rather than
  either crashing or fabricating sections Claude never actually assessed. This is the
  "offline-safe generation" the directive's gate names, not a new capability bolted on.
- `MorningBriefingWorker`/`MorningBriefingSkill` both call `.renderText()` at their
  existing single point of use — no behavior change to notification delivery, quiet
  hours, or scheduling, which this seam doesn't touch.

### Verification
`DailyBriefingParsingTest` (8 cases: full briefing parses every section, `nothingUnusual:
true` wins regardless of other fields, empty sections stay empty rather than backfilled,
a blank `today` with `nothingUnusual: false` is rejected as too thin to trust, malformed
JSON returns null, a blank recommendation is treated as none, `renderText` omits empty
sections, `NothingUnusual` renders as one line). Forward-reference and injection-boundary
audits both clean.

### Commit
`4ffb8e7` — pushed to `claude/new-session-efg0ha` and green on the first push. Job-level
CI confirmed — 15/15 steps, including the release-build/R8 step.

### Status
VERIFIED at its exact CI-green SHA (`4ffb8e7`).

## September 8 — Goal Engine: taxonomy (aspiration/goal/project/commitment/task/routine)

Second of the five enhancements the user asked for from
`docs/P1_INTELLIGENCE_CORE_EXECUTION_PLAN.md`'s survey.

### Investigation
`GoalEntity` was `{id, text, active, createdAt}` — flat text, confirmed by reading
`goals/GoalRepository.kt` and `goals/GoalReviewWorker.kt` end to end rather than assuming.
`GoalReviewWorker`'s weekly review loop treats every active goal identically regardless of
what kind of thing it actually is; the directive's Goal Engine gate specifically names the
aspiration/goal/project/commitment/task/routine distinction as missing.

### Implementation
- `goals/GoalType.kt` — the six-value enum, user-chosen at goal-creation time. `addGoal`
  is only ever invoked from the Settings "Add a goal" dialog (confirmed via grep — no
  other call site exists), so a type is never inferred from conversation, keeping the
  directive's "never infer a binding commitment merely from conversation" invariant intact
  by construction rather than by a runtime check.
- `GoalEntity.type: GoalType = GoalType.GOAL` (additive, DB v13→14, existing rows default
  to GOAL — the most generic value, not a guess at what they actually were).
- `GoalRepository.addGoal`/`NuaViewModel.addGoal` both gained an optional `type` parameter
  defaulting to `GOAL`, so nothing else calling `addGoal` needed to change.
- Settings: `AddGoalDialog` gained a `FilterChip` type picker — the same pattern
  `MemoryTypeFilterRow` already established for `MemoryType`, not a new UI idiom.
  `GoalsCard` shows each goal's type alongside its text.

### Explicitly not attempted this pass
Milestones and dependencies, both named in the directive's Goal Engine gate alongside the
taxonomy. Both are materially larger — a new sub-entity, progress tracking, dedicated UI —
than the concretely-scoped taxonomy distinction. Left as a named future seam rather than
rushed into this one, per "no speculative architecture."

### Verification
No dedicated test: `GoalType` is a bare enum with no logic to test, same precedent as
`memory/MemoryType.kt` (also untested — confirmed no test file exists for it either).
Forward-reference and injection-boundary audits both clean.

### Commit
`9fb837c` (code) — pushed to `claude/new-session-efg0ha`. CI-verified green (job-level,
15/15 steps including release-build/R8) at `7a22f14`, the docs-only follow-up commit on
top of it — same code, no changes between the two beyond this file.

### Status
VERIFIED at its exact CI-green SHA (`7a22f14`).

## September 8 (continued) — Dreams 2.0: code-enforced provenance, first World Model writer

Third of the five enhancements. The largest of the five: it makes Dreams the World
Model's first writer, exactly as `docs/WORLD_MODEL_RFC.md` §15 recommended back in P1.9
but explicitly left unbuilt as its own seam.

### Investigation
Read `DreamSynthesisWorker.kt` end to end: it already asks Claude to "connect at least
two different things" via prompt instruction, but nothing in the code ever checked that
instruction was actually followed — Claude could return a single-source or zero-source
"insight" and it would be recorded identically to a genuinely cross-referenced one. No
`DreamEntity` field recorded which specific facts/goals/ledger entries were used, and
`WorldModelRepository` (P1.9) had `record()`/`relationshipsFor()` fully working but zero
callers — confirmed via grep. The gap wasn't the World Model layer; it was that nothing
had ever called it.

### Implementation
- `dreams/DreamSource.kt`: `DreamSource(type, id)` + `DreamSourceType` (`FACT`/`GOAL`/
  `TRUST_LEDGER`, matching the RFC's existing `(type, id)` string vocabulary rather than
  inventing a new one). `hasSufficientProvenance(sources)` — pure, the actual code-level
  ">=2 distinct things" check, replacing the prompt-only version. `validatedDreamSources`
  — pure, filters Claude's claimed connected-item IDs down to ones that were genuinely
  offered to it, so a hallucinated or stale ID can never become a stored relationship.
- `DreamSynthesisWorker`: every fact/goal/ledger item in the prompt is now tagged with its
  real database id (`[fact#12]`, `[goal#3]`, `[ledger#7]`); the system prompt asks Claude
  to name which specific tagged ids it connected, not just describe a connection in prose.
  The parsed ids are validated against what was actually offered, then gated through
  `hasSufficientProvenance` before a Dream is ever recorded — an insight that can't name
  two real, distinct connected items is discarded, the same "saying nothing is correct
  more often than not" outcome every other reject path already uses.
- `DreamRepository.record()` now takes the validated `sources` and, after inserting the
  `DreamEntity`, writes one `world_relationships` row per source via
  `WorldModelRepository.record()`: `DREAM -[synthesized_from]-> {FACT|GOAL|TRUST_LEDGER}`,
  `source = "dream_synthesis"`, full confidence. Not wrapped in a cross-DAO transaction —
  matches this codebase's existing precedent (`TaskPlanner.confirmPlan` is similarly
  sequential, non-transactional); a partial write here is a minor, recoverable
  inconsistency, not a correctness hazard worth the added complexity.
- `docs/WORLD_MODEL_RFC.md`'s status note updated — Dreams is no longer "the recommended
  first writer" in the future tense; it's what shipped.

### Explicitly not attempted this pass
Read-side resolution of `world_relationships` back into real entity objects, or any UI
surfacing what a Dream connected. `WorldModelRepository`'s own doc comment already argues
against building resolution machinery before a real reader proves what shape it needs —
still true here; this seam is a writer, not a reader.

### Verification
`DreamSourceTest` (6 cases: two distinct sources are sufficient, zero/one are not, the
same source repeated doesn't count twice, `validatedDreamSources` drops ids never offered,
an all-hallucinated id set yields nothing, duplicate ids in one list collapse to one
source). Forward-reference and injection-boundary audits both clean.

### Commit
`8b1cdf4` — pushed to `claude/new-session-efg0ha`. CI-verified green (job-level, 15/15
steps including release-build/R8) as part of the later `8dbd736` run, which contains this
commit's code unchanged.

### Status
VERIFIED — confirmed CI-green at `8dbd736` (see the Memory Vault/Privacy Centre entry
below for why final verification landed on that later SHA instead of this one directly).

## September 8 (continued) — Decision Engine: facts/unknowns/constraints/options staging

Fourth of the five enhancements.

### Investigation
`DecisionEntity` was `decision + reasoning + outcome` — confirmed by reading
`decisions/DecisionRepository.kt` and the entity/DAO in `memory/MemoryStore.kt`. The
directive's Decision Engine gate names a fuller structure: situation → facts → unknowns →
constraints → options → recommendation → decision → actual outcome → lesson. Implementing
the full nine-stage pipeline (including a recommendation-generation step) is a materially
larger undertaking than the other four enhancements — scoped this pass to the concrete,
directly actionable part: letting the user capture facts/unknowns/constraints/options at
logging time, the fields the gate names that were entirely absent from the data model.
"Situation" and "decision" already exist (`decision` field, `reasoning` covers the
situation informally); "recommendation" would require an AI-generated suggestion this
project's own "decisions are written to, not inferred" philosophy (matching the existing
Decision Journal's explicit no-auto-capture design, and Goals' just-added
never-inferred-type precedent) argues against manufacturing.

### Implementation
- `DecisionEntity` gains four nullable free-text fields: `facts`, `unknowns`,
  `constraints`, `options` (additive, DB v14→15). Plain `String?`, not a serialized list —
  no `List<String>` Room column exists anywhere in this codebase yet, and introducing a
  `TypeConverter` for one feature would be exactly the kind of new infrastructure the
  "no speculative architecture" rule cautions against when a free-text field (matching
  `reasoning`'s own existing, already-proven shape) does the job just as well.
- `DecisionRepository.record()`/`NuaViewModel.addDecision()` both gained four optional
  parameters, all defaulting to null, so every existing caller kept compiling unchanged.
- Settings: `AddDecisionDialog` gained a collapsed-by-default "Add more detail" section
  revealing the four new fields — progressive disclosure, not a heavier form forced on
  every decision. `DecisionsCard`'s row shows each populated field via a small shared
  `DecisionDetailLine` composable (renders nothing when null).

### Explicitly not attempted this pass
An AI-generated recommendation step, and a "lesson learned" field distinct from the
existing `outcome`. Both are real gaps against the full nine-stage structure the directive
names, but each raises its own design questions (what does a recommendation mean without
executing anything; is "lesson" meaningfully different from outcome, or does forcing the
distinction just add form-filling burden) that deserve their own investigation rather than
being rushed into this pass alongside four other enhancements in flight.

### Verification
No dedicated test: the four new fields are plain data with no logic — the existing
`DecisionEntity(...)` construction sites in `TimelineBuilderTest`/`SecondBrainSearchTest`
already use named parameters, so both compile unchanged with no update needed. Forward-
reference and injection-boundary audits both clean.

### Commit
`fd6a7e1` — pushed to `claude/new-session-efg0ha`. CI-verified green (job-level, 15/15
steps including release-build/R8) as part of the later `8dbd736` run, which contains this
commit's code unchanged.

### Status
VERIFIED — confirmed CI-green at `8dbd736`.

## September 8 (continued) — Memory Vault / Privacy Centre: a consolidated screen

Fifth and final of the five enhancements the user asked for.

### Investigation
Read `SettingsScreen.kt` end to end before assuming a gap: per-fact provenance/confidence/
correction/sensitivity already exist (`FactRow`/`FactDetailDialog`, P1.10), encryption-at-
rest status already exists (`SecurityCard`, reading `security/EncryptionAudit.kt`), and
recent/autonomous actions already exist (`TrustCard`/`AuditTrailCard`). What genuinely
didn't exist anywhere: an explicit statement of what's local versus sent to Claude, a
permissions overview, a real data export, and a real "delete everything" action — the
directive's Memory Vault/Privacy Centre gate names all four. Also checked `NuaDestination`
— the five-destination bottom nav is a deliberate, closed design (`#38`'s own doc comment:
"a map of five ideas, not a menu"); `MemoryScreen` already solves exactly this problem for
Search/Timeline/Documents via a `MemorySection` chip row nested under one destination
rather than three more bottom-nav icons — the right place to add a fourth section, not a
sixth destination.

### Implementation
- `ui/nav/NuaDestination.kt`: `MemorySection.PRIVACY` added to the existing enum —
  `SectionChips` is already data-driven off `MemorySection.entries`, so no chip-rendering
  code needed to change at all.
- `ui/PrivacyCentreScreen.kt` (`PrivacyCentreContent`): five cards — what NUA knows
  (counts, pointing back to the existing per-fact detail view rather than duplicating it),
  local-vs-Claude (a factual statement of what this app's own code does — only relevant
  facts are included in a Claude request, nothing else is sent anywhere — not a claim
  about Anthropic's own retention policy, which isn't this app's to promise), permissions
  (the app's actual dangerous runtime permissions, checked live via
  `ContextCompat.checkSelfPermission`, not a static list), export, and delete-everything
  (behind a confirmation dialog naming exactly what gets erased).
- `privacy/DataExport.kt`: `buildDataExport()`, a pure function producing a plain-text
  dump of facts/goals/decisions/dreams — not a proprietary format. Export fires a plain
  `Intent.ACTION_SEND`, letting the user choose where it goes rather than this app
  picking a destination for them.
- `privacy/PrivacyRepository.kt`: `resetAllData()` clears every category
  `security/EncryptionAudit.kt` itself names as stored — `NuaDatabase.clearAllTables()`,
  `SecureKeyRepository.clearApiKey()`, `OwnerVoiceProfileStore.clear()` — so "delete
  everything" is actually complete against this app's own audit of what it stores, not a
  partial gesture that leaves the voice profile or API key behind.

### Verification
`DataExportTest` (3 cases: an empty export still names every section as empty rather than
omitting them, a populated export includes every category's real content, an inactive
goal is labeled as such). No test for `PrivacyRepository.resetAllData()` — it's a thin,
untestable-without-Robolectric sequence of three real side effects (DB clear, two
encrypted-store clears), same precedent as this codebase's other impure orchestration
classes (`TaskPlanner`, `MorningBriefing`) that also have no direct unit test. Forward-
reference and injection-boundary audits both clean.

**CI caught a real regression here, not a false negative**: `NuaDestinationTest`'s
`memory sections cover the three views onto what NUA knows` was a deliberate regression
pin (mirroring the "five destinations, not an accident" test right above it) that failed
the first push, correctly, because adding `PRIVACY` genuinely changed
`MemorySection.entries` from three values to four. Since the fourth section was this
seam's own deliberate, documented choice (nested under `MEMORY` rather than promoted to a
sixth bottom-nav destination, matching that same test file's own top-level reasoning),
the right fix was updating the test's expectation to four, not reverting the feature —
exactly the "a pin that fails on genuine, intentional change gets updated, not weakened"
principle this project has applied to itself before.

### Commit
`cc46d50` (code) — pushed to `claude/new-session-efg0ha`; first CI run
(`34179816341`) failed on a genuine regression (see the "CI caught a real regression"
note above) — `NuaDestinationTest` correctly caught `MemorySection` growing from three
entries to four, fixed at `8dbd736`, which is job-level CI-verified green (15/15 steps,
release-build/R8 included, Room schema validated at DB v15).

### Status
VERIFIED at its exact CI-green SHA (`8dbd736`).

## September 9 — Context Engine: broader signal coverage

The user's second round: "Context Engine signal coverage, Earned Autonomy, World Model,
and whatever's left in original survey." Starting with Context Engine since What Now?/
Daily Intelligence/Dreams/Goal Review already read from it — broadening it benefits all
four for free.

### Investigation
`ContextSnapshot` covered weather/calendar/connectivity/notifications only.
`WhatNowAdvisor`, `GoalReviewWorker`, and `DreamSynthesisWorker` each separately called
`goalRepository.activeGoals()` themselves rather than reading it off the snapshot, despite
`ContextEngine`'s own doc comment promising exactly this consolidation. Decisions were
never surfaced to any of these prompts at all. "Routines" (directive-named) has no
existing signal in this codebase — and building real behavioural pattern-detection over
historical activity would be a new, unproven capability, not a gap-fill; reusing the
`GoalType.ROUTINE` tag just added this session is the honest version of this signal: user-
declared, not behaviourally inferred. Location is directive-named too, and
`ACCESS_COARSE_LOCATION` is already requested at app start (`MainActivity`) — checked
`GeofenceManager` to confirm what's already available versus what geofencing's continuous
monitoring specifically needs (`ACCESS_FINE_LOCATION`/`ACCESS_BACKGROUND_LOCATION`, not
required for a one-shot lookup).

### Implementation
- `ContextSnapshot` gains `activeGoals`, `recentDecisions` (last 5), and a computed
  `routines` property (`activeGoals.filter { it.type == GoalType.ROUTINE }` — no new
  query). `describe()` extended with all three plus `currentPlace`.
- `context/CurrentPlaceResolver.kt` — resolves which saved geofence (if any) the user is
  currently near, via a one-shot `FusedLocationProviderClient.lastLocation` read wrapped
  in `suspendCancellableCoroutine` (the exact pattern `ai/CancellableHttpCall.kt` already
  established for a Play-Services-style callback API, not a new idiom). Only the matched
  place's *name* ever reaches `ContextSnapshot`/a Claude prompt — raw coordinates never
  leave this class, matching "local-first... minimise transmitted context." Skips the
  location read entirely when there are no saved geofences to match against, since the
  result could only ever be null.
- `context/GeofenceProximity.kt` — `haversineDistanceMeters`/`nearestContainingGeofence`,
  pure Kotlin (not `android.location.Location.distanceBetween`, which isn't callable from
  this project's Robolectric-free JVM unit tests) so the actual matching logic is directly
  testable.
- `DecisionDao.recent(limit)` + `DecisionRepository.recent(limit = 5)` — the only missing
  piece; `DecisionEntity`/the rest of the Decision Journal already existed.
- `WhatNowAdvisor` no longer fetches goals separately — `snapshot.describe()` already
  includes them, so the redundant fetch and manual append were removed (a real
  simplification, not just a refactor for its own sake). `GoalReviewWorker`/
  `DreamSynthesisWorker` similarly now read `snapshot.activeGoals` instead of a second
  `goalRepository.activeGoals()` call — one fewer redundant query each, same data.
  `MorningBriefing` needed no changes at all: it already calls `snapshot.describe()`
  verbatim, so Daily Intelligence gained goal/decision/place awareness for free.

### Verification
`GeofenceProximityTest` (6 cases: identical points are zero distance apart, a known
~13km distance comes out roughly right, a point inside a radius matches, a point outside
every radius matches nothing, an empty geofence list never matches, the first containing
match wins when radii overlap) and `ContextSnapshotTest` (2 cases: `routines` is exactly
the ROUTINE-typed goals and nothing else, an empty goal list yields an empty routine
list rather than an error). Confirmed via grep that no file other than `ContextEngine.kt`
itself constructs `ContextSnapshot(...)`, so widening its constructor was a safe, additive
change with exactly one production call site to update. Forward-reference and
injection-boundary audits both clean.

### Commit
`84eb68b`

### Status
CONFIRMED CI-green (run 34298282047, 15/15 steps including release-APK/R8).

## September 9 (continued) — World Model: read-side resolution, first real reader

Second of the four items from the user's second round.

### Investigation
`WorldModelRepository`'s own doc comment (P1.9) said resolution was deliberately deferred
until a real reader existed to prove the shape it needed — building it blind would have
been exactly the speculative work the RFC argues against. That reader now exists: Dreams
writes real `DREAM -[synthesized_from]-> {FACT|GOAL|TRUST_LEDGER}` rows (this session's
earlier Dreams 2.0 seam). No DAO had an id-based lookup for any of those three entity
types — `MemoryDao`/`GoalDao`/`TrustLedgerDao` all only supported key-based or bulk
queries — confirmed by reading each interface rather than assuming.

### Implementation
- `WorldModelRepository.otherSideOf(relationship, type, id)` — pure, direction-agnostic:
  a relationship row has a `from`/`to` side, but a caller asking "what's this connected
  to" shouldn't have to know which side it queried from.
- `WorldModelRepository.relationshipsWithSummaries(type, id)` — `relationshipsFor` with
  each connected entity resolved to a short summary (a fact's value, a goal's text, a
  ledger entry's description). `ResolvedRelationship.summary` is null for an orphan the
  stored confidence hasn't caught up to yet, or an entity type this resolver doesn't
  know how to read — never a fabricated placeholder. Deliberately covers only
  FACT/GOAL/TRUST_LEDGER — the types a real writer produces today, not every type the
  RFC's examples name (DECISION/DOCUMENT have no writer yet).
- `MemoryDao.getFactById`, `GoalDao.getGoalById`, `TrustLedgerDao.getById` — the three
  missing id-based lookups, additive.
- `NuaViewModel.dreamConnections: StateFlow<Map<Long, List<String>>>` — derived from the
  existing `dreams` flow via `.map`, resolved once per dream-list change, not per
  recomposition.
- `DreamsCard` (Settings) shows "Connected to: ..." under each dream when it has
  resolved connections — the one real UI consumer, not a general graph browser (per the
  directive's own "read-side resolution... nothing reads this data yet" framing: build
  exactly the reader that's needed, not speculative infrastructure around it).

### Verification
Extended the existing `WorldModelRepositoryTest` (from P1.9) with 3 new cases for
`otherSideOf`: queried from the from-side resolves to the to-side, queried from the
to-side resolves to the from-side, and a same-type-different-id relationship doesn't
short-circuit on type alone (both `fromType`/`fromId` must match, not just `fromType`).
Forward-reference and injection-boundary audits both clean. Confirmed via grep no other
file constructs `DreamsCard(...)` or `WorldModelRepository(...)` directly, so widening
both was additive with exactly one production call site each to update.

### Commit
`<pending>` — pushed to `claude/new-session-efg0ha`; updated once CI confirms green.

### Status
PENDING CI verification at time of writing.

## September 9 (continued) — Earned Autonomy: scoped grants with expiry

Third of the four items from the user's second round.

### Investigation
`TrustRepository.setAutoApprove()`/`isAutoApproved()` already existed (auto-approve a given
`NuaActionType` once its approved-count crosses a threshold) but the grant was indefinite —
no expiry, no review date, and no revoke-on-failure. Worse: `allAutonomyPreferences()`
already existed on `TrustRepository` but grep across the whole codebase found zero callers
— any currently-active auto-approve grant was completely invisible in the UI. That's a real
transparency gap the directive's "earned autonomy... always revocable, never silent" gate
names directly, not a speculative addition.

### Implementation
- `trust/AutonomyGrant.kt` — pure: `isGrantActive(autoApproveEnabled, expiresAt, now)` and
  `daysUntilExpiry(expiresAt, now)`. `AUTONOMY_GRANT_DURATION_MILLIS` = 30 days. Fails
  closed on ambiguity: `autoApproveEnabled=true, expiresAt=null` (a hypothetical
  pre-migration row) reads as NOT active, never as perpetually valid.
- `AutonomyPreferenceEntity.expiresAt: Long?` (additive, DB v15→16). Null whenever
  `autoApproveEnabled` is false; always set when `setAutoApprove(enabled = true)` runs.
- `TrustRepository.setAutoApprove()` now stamps a fresh 30-day `expiresAt` on enable and
  clears it on disable. `isAutoApproved()`/the new `activeAutonomyGrants()` both route
  through `isGrantActive` rather than reading `autoApproveEnabled` alone.
- `TrustRepository.recordOutcome()` — on any failure, `revokeAutoApproveIfGranted(actionType)`
  immediately clears that action type's standing grant. A failure fail-closes autonomy
  rather than waiting for the user to notice and revoke it by hand.
- `TrustUiController` — gains `activeAutonomyGrants: StateFlow<...>` (same manual-
  refresh-on-Settings-open pattern as its other state, not a live Flow subscription) and
  `disableAutoApprove(actionType)`, the explicit revoke path.
- Settings: `TrustCard` gains a "NUA may currently do without asking" section listing each
  active grant with its days-until-expiry and a Revoke button — closes the transparency gap
  found in Investigation.

### Explicitly not attempted
Context/location/value/recipient-scoped grants (e.g. "auto-approve SMS only to contacts,
only under $X"). The directive names scoped autonomy as a direction, but there's no
concrete driving use case yet to shape what scope means for this codebase's action types —
building it now would be exactly the speculative architecture this project's discipline
argues against. Time-boxed expiry plus fail-closed revoke-on-failure is the concretely
justified slice.

### Verification
`AutonomyGrantTest` (6 cases: future expiry is active, past expiry is not, exactly-at-expiry
is not, a disabled grant is never active regardless of expiry, an enabled grant with no
recorded expiry fails closed rather than open, `daysUntilExpiry` rounds down and never goes
negative). `TrustUiControllerTest` updated (4 call sites) to cover the new fourth source.
Forward-reference and injection-boundary audits both clean. Confirmed via grep no other
file constructs `TrustCard(...)` directly, so widening its signature was additive with
exactly one production call site to update.

### Commit
`<pending>` — pushed to `claude/new-session-efg0ha`; updated once CI confirms green.

### Status
PENDING CI verification at time of writing.

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
