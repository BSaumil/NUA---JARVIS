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
See the commit log for the exact SHA this entry closes on — job-level CI (not just
overall conclusion) was confirmed green at that SHA before this entry was closed.

### Status
VERIFIED at its exact CI-green SHA. This completes every P0 item in the roadmap's
priority order (P0.1–P0.8). P1 (Intelligence Core) is next, not yet started.

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
