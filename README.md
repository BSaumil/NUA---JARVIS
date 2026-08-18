# NUA — Neural Understanding Ally

This repo holds two independent implementations of NUA that were built in parallel and
haven't been reconciled into one:

- **`app/` — native Android app** (Kotlin, Jetpack Compose, Hilt/MVVM, Claude API
  direct from the client). This is the actively developed one and everything below
  documents it.
- **`backend/` + `frontend/` — a separate build** (FastAPI + MongoDB backend, React
  Native/Expo frontend) produced by the Emergent app-builder platform. See `backend/`
  and `frontend/` directly; it doesn't share any code or docs with the Android app and
  its own README is just a placeholder.

---

A personal AI assistant for Android (Kotlin, Jetpack Compose, Hilt/MVVM, Claude API).

## Beyond JARVIS

JARVIS is fiction's shorthand for "an AI that just handles it" — but the fictional
version never has to answer *how*: what it's allowed to do without asking, where your
data actually lives, or what happens when it's wrong. NUA is scoped to answer those
questions for real, and that's the actual differentiator, not a longer feature list:

- **A real permission model, not a vibe.** Every capability in this app is labeled
  Tier 1 (official API), Tier 2 (opt-in automation, confirmed per action, never
  scheduled or chained), or Tier 3 (not implemented, on purpose) — see the tiers below.
  JARVIS-in-fiction has no such boundary; NUA's is load-bearing in the code, not just
  in this README.
- **Local-first memory.** Facts, conversation history, and API keys live in on-device
  Room storage and `EncryptedSharedPreferences` — nothing is mirrored to a NUA-run
  server, because there isn't one. The tradeoff is real (no cross-device sync yet — see
  Known gaps) and stated plainly rather than glossed over.
- **Consent is structural, not a checkbox.** Tier 2 actions fail loudly
  (`NuaAccessibilityService.lastFailure`) rather than silently degrading, notification
  replies and calendar writes always route through an explicit confirmation dialog, and
  nothing — not a scheduled briefing, not a proactive geofence notification — speaks
  out loud or takes an irreversible action without the user in the loop at that moment.
- **Multilingual as a first-class citizen, not a bolt-on.** Ten languages across four
  script families, with an explicit mirroring directive baked into every Claude call
  (not a separate translation pass) — see Languages below.
- **Personality that actually tracks rapport.** `PersonalityEngine`'s familiarity tiers
  are driven by facts learned and turns exchanged, not calendar days since install —
  NUA gets more decisive as it actually knows you, not as a subscription ages.
- **Honest about what isn't real yet.** Every scaffold in this repo — smart home,
  email, Android Auto, Wear OS — says so directly in its own file and in Known gaps,
  instead of a demo that fakes capability it doesn't have.

NUA is scoped in three tiers of increasing risk:

- **Tier 1 — official APIs/intents.** App launching, media session control, notification
  listener, on-device calendar, weather. Ships freely, no confirmation gate beyond what
  the underlying API already requires.
- **Tier 2 — Accessibility Service automation.** Opt-in, off by default, last resort for
  actions with no official API. Every Tier 2 action requires explicit per-action
  confirmation in the moment — never scheduled, never chained, never run unattended.
  `NuaAccessibilityService` currently ships as an inert skeleton; no concrete Tier 2
  action is wired up yet.
- **Tier 3 — not currently implementable or advisable**, and out of scope unless
  explicitly revisited: direct WiFi/Bluetooth toggling, autonomous purchasing, call/
  meeting recording without explicit all-party consent, presenting health/emotional
  inferences as diagnosis.

## Codebase structure

```
app/src/main/java/com/nua/assistant/
  NuaApplication.kt        → @HiltAndroidApp entry point, WorkManager+Hilt wiring
  MainActivity.kt           → Compose host, permission requests, wake-word broadcast receiver
  ai/                        → ClaudeApiClient (streaming + prompt caching + vision),
                                PersonalityEngine, IntentClassifier, FactExtractor,
                                FactRelevance, TaskPlanner, UsageTracker (cost dashboard)
  voice/                     → VoiceManager (STT/TTS wrapper), NuaLanguage (10-language
                                catalog), LanguagePreferenceStore (pinned language),
                                OwnerEnrollment/OwnerVerifier (Picovoice Eagle),
                                VoiceProsody (tone heuristic off SpeechRecognizer RMS)
  automation/                → AppLauncher (Tier 1), NuaIntentRouter (keyword + Claude-fallback
                                routing, dispatches through NuaSkill map), NuaSkill + one
                                file per Tier 1 action (OpenAppSkill, PlayMediaSkill, ...),
                                SkillModule (Hilt multibinding), NuaAccessibilityService
                                (Tier 2 skeleton)
  weather/                   → WeatherRepository (Open-Meteo, no API key, TTL-cached)
  calendar/                  → CalendarReader (on-device CalendarContract, read + confirmed-plan
                                reminder writes, no OAuth)
  email/                     → EmailRepository extension point (Gmail API, scaffold — see
                                Known gaps)
  geofencing/                → GeofenceManager (Play Services Geofencing), Geofence
                                BroadcastReceiver, Room-backed saved geofences
  memory/                    → MemoryStore.kt (Room: messages + user_facts + usage_logs +
                                geofences + trust_ledger + action_outcomes +
                                autonomy_preferences), MemoryType (identity/episodic/semantic/
                                behavioral/emotional/relationship), MemoryConsolidationWorker
                                (summarizes and prunes old messages), SecureKeyRepository
                                (encrypted API key storage)
  trust/                     → TrustRepository (audit trail, curated mistake ledger, adaptive
                                autonomy, rate-limited self-report), TrustScoreEngine,
                                AutonomyTier (T0-T5 firewall), TrustEventType
  network/                   → ConnectivityMonitor (offline detection before a Claude call)
  vision/                    → ImageEncoder (camera capture → base64 for Claude vision)
  notifications/              → NuaNotificationListenerService, NotificationRepository,
                                NotificationPriorityScorer, NotificationStatsStore,
                                NotificationReplySender (Tier 1 quick-reply)
  media/                     → MediaControlManager (MediaSessionManager-based)
  briefing/                  → MorningBriefing, MorningBriefingWorker + BriefingScheduler
                                (WorkManager-based proactive scheduling)
  context/                   → ContextEngine (queryable "current situation": weather,
                                today's calendar, connectivity, notifications),
                                WhatNowAdvisor ("what should I do now?" entry point)
  goals/                     → GoalRepository (durable user goals), GoalReviewWorker
                                (weekly, checks each goal against ContextEngine via Claude)
  dreams/                    → DreamRepository, DreamSynthesisWorker (weekly, one
                                cross-referenced insight across facts/goals/trust ledger
                                or nothing), DreamCategory
  smarthome/                 → SmartHomeRepository extension point (Matter/Google Home, scaffold)
  widget/                    → NuaWidget (Jetpack Glance home-screen widget)
  car/                       → NuaCarAppService (Android Auto entry point, Car App Library)
  services/                  → NuaForegroundService (multi-phrase wake-word listening via
                                Porcupine), WakePhrase (extensible wake-word catalog)
  di/                        → AppModule (Hilt module for Room, OkHttp, JSON)
  ui/                        → NuaScreen (Compose), NuaViewModel (@HiltViewModel), NuaTheme

wear/src/main/java/com/nua/assistant/wear/
  NuaTileService.kt          → Wear OS status tile (separate Gradle module, see Known gaps)
```

## Phase 3 — what's new

1. **Intent engine upgrade.** `NuaIntentRouter` tries free local keyword matching first;
   ambiguous/conversational requests ("I'm bored, put some music on") that miss the fast
   path fall back to `IntentClassifier`, which asks Claude to classify the utterance into
   a concrete action (or CHAT, if nothing fits) before a single paid API round trip.
2. **Fact extraction into memory.** `FactExtractor` flags durable facts (a preference, a
   routine, a name) from a conversation turn and returns them for `NuaViewModel` to write
   via `MemoryDao.upsertFact`. It doesn't run on every turn — `shouldConsider` gates it to
   turns that look likely to contain a fact (regex hint) plus a periodic sweep every 4th
   user turn, so slow-burn facts aren't missed without paying for every single turn.
3. **Multi-step task planning.** `TaskPlanner` gathers real weather (`WeatherRepository`)
   and calendar (`CalendarReader`) context the same way `MorningBriefing` does, asks Claude
   to propose a short plan, and returns it for UI confirmation (`PlanConfirmationDialog`).
   Reminders are only created via `CalendarReader.createReminder` after the user taps
   "Confirm plan" — never automatically.
4. **Notification prioritization.** `NotificationRepository.summary()` ranks rather than
   lists: `NotificationPriorityScorer` flags call/message-category notifications, high
   declared priority, messaging/phone apps, and apps the user has historically dismissed
   quickly (tracked in `NotificationStatsStore`), and phrases the result plainly
   ("two need attention, eight can wait") instead of reading everything with equal weight.

## Phase 4 — performance and reach

1. **Streaming replies.** `ClaudeApiClient.streamMessage` uses server-sent events
   instead of waiting for the full response; `NuaViewModel` speaks completed sentences
   as they arrive (via `VoiceManager.speak(..., flush = false)` for queued chunks) and
   renders a live-updating bubble (`NuaUiState.streamingReply`), instead of a spinner
   until the whole reply is ready.
2. **Prompt caching.** Every Claude call now sends its system prompt as a single
   `cache_control: ephemeral` block (`ClaudeSystemBlock`). The static utility prompts
   (`IntentClassifier`, `FactExtractor`) hit cache most often since they're identical
   call to call; the main chat's system prompt varies with facts/tone so it hits less,
   but costs nothing extra when it misses.
3. **TTL cache on weather.** `WeatherRepository` caches the last snapshot for 10 minutes
   per ~1km location bucket — asking about the weather twice in a row doesn't re-hit
   Open-Meteo.
4. **Relevance-scoped facts.** `FactRelevance` ranks `user_facts` by keyword overlap
   with the current message plus recency and caps what goes into the system prompt
   (12 by default) instead of dumping the whole table every turn — lexical, not
   semantic/embedding search (bundling an embedding model on-device wasn't
   proportionate to what this solves), but enough to bound prompt size as it grows.
5. **Continuous conversation mode.** After a reply finishes speaking
   (`VoiceManager.markReplyComplete` → `setOnFinalSpeechDoneListener`), NUA listens
   again for a follow-up without repeating the wake word — up to `MAX_VOICE_FOLLOW_UPS`
   times, ended immediately by typed input, a listening error/timeout, or the cap, so
   the mic is never left hot indefinitely.
6. **Proactive scheduled briefings.** `MorningBriefingWorker` (WorkManager, Hilt-injected
   via `NuaApplication`) runs `MorningBriefing.generate()` at a user-chosen time and
   posts it as a notification (and saves it into the conversation) — deliberately *not*
   read aloud unprompted, since playing audio from a background worker with no user
   interaction in progress felt more surprising than helpful. Configured in
   Settings → Proactive morning briefing.
7. **Notification quick-reply.** `NotificationReplySender` replies through a
   notification's own built-in `RemoteInput` action — the same official mechanism a
   wearable uses — no Tier 2/accessibility involved. Sending a message on the user's
   behalf is sensitive, so it always goes through a confirmation dialog
   (`NuaRouteResult.ReplyProposed`) before anything sends.
8. **Smart home extension point.** `SmartHomeRepository` + `NuaActionType.SMART_HOME`
   are fully wired into the router, but the only binding shipped
   (`UnconfiguredSmartHomeRepository`) always reports not-configured. Google's Home APIs
   are limited-access and need a provisioned Google Cloud project plus real device
   commissioning outside the app — swap the Hilt binding in `smarthome/SmartHomeModule.kt`
   once that setup exists.
9. **Home-screen widget.** `NuaWidget` (Jetpack Glance) shows the next calendar event,
   current weather, and notification summary, refreshed on Android's own widget update
   cycle (`nua_widget_info.xml`, 30-minute minimum).
10. **Voice owner verification.** `OwnerEnrollment`/`OwnerVerifier` wrap Picovoice Eagle
    (speaker recognition — a separate product/entitlement from Porcupine) for a future
    Tier 2 action to gate on "is this actually the owner's voice." Nothing calls
    `OwnerVerifier` yet since there's no concrete Tier 2 action to gate; Settings →
    Voice ID lets you enroll ahead of that.

## Phase 5 — offline resilience, senses, and reach

1. **Offline fallback.** `ConnectivityMonitor` checks reachability before every chat
   round trip; offline, NUA answers immediately with a local message (including the
   current time) instead of waiting out a connect timeout. Keyword-routed Tier 1
   actions (open app, media control, read notifications) already worked offline since
   they never call Claude — this closes the gap for the conversational fallback path.
2. **Memory consolidation.** `MemoryConsolidationWorker` (daily WorkManager job) keeps
   the `messages` table from growing unbounded: once raw history passes 300 rows, the
   oldest 100 are summarized into one `user_facts` entry (category
   `conversation_summary`) via Claude Haiku, then deleted. Named facts and recent
   history are untouched.
3. **Vision input.** A camera button in the chat input row captures a photo
   (`ActivityResultContracts.TakePicture` + a `FileProvider`-scoped cache file),
   `ImageEncoder` base64-encodes it, and `ClaudeApiClient.describeImage` sends it to
   Claude's vision as a normal reply — no new model, just multimodal content blocks.
4. **Voice prosody heuristic.** `VoiceProsody` classifies each utterance's rough
   energy/variance off `SpeechRecognizer`'s existing `onRmsChanged` stream (no extra
   mic capture) into `NEUTRAL`/`LOW_ENERGY`/`HIGH_INTENSITY`, and folds a one-line
   directive into that turn's system prompt. It's a DSP heuristic on decibel samples,
   not a trained emotion model — good enough to nudge tone, not to diagnose anything.
5. **Android Auto.** `NuaCarAppService` (Car App Library) gives NUA a minimal,
   driving-safe status screen in Android Auto's app list. No car-specific voice
   trigger — the same background wake-word listening that works everywhere else covers
   it — deliberately not a chat UI while driving.
6. **Wear OS companion.** A new `:wear` Gradle module (`NuaTileService`) adds a status
   tile to the watch face carousel using the classic Wear Tiles API. Static text for
   now — no Wearable Data Layer connection to the phone app yet (see Known gaps).
7. **API usage/cost dashboard.** Every Claude call (streamed and non-streamed) logs its
   token usage (`UsageTracker` → `usage_logs` table); Settings shows an estimated
   monthly cost against published list pricing — not your actual bill, a budgeting
   signal.
8. **Geofenced proactive suggestions.** `GeofenceManager` (Play Services Geofencing)
   lets you save a named location + radius + message in Settings; NUA notifies you on
   arrival. Real Tier 1 API usage, gated on `ACCESS_FINE_LOCATION` +
   `ACCESS_BACKGROUND_LOCATION`, neither requested until you actually add one.
9. **Calendar & email.** Calendar was already real (Phase 3's `CalendarReader`, no
   OAuth). Email gets the same `NuaActionType`/router treatment as smart home:
   `EmailRepository` is fully wired in, but the shipped binding
   (`UnconfiguredEmailRepository`) always reports not-configured — email has no
   on-device content-provider equivalent to calendar, so it genuinely needs a Gmail API
   project + OAuth consent outside the app before a real binding can exist.
10. **Pluggable skill architecture.** Every Tier 1 action used to live as a branch in
    `NuaIntentRouter`'s `when`. It's now a `NuaSkill` implementation
    (`automation/*Skill.kt`) registered into a `Map<NuaActionType, NuaSkill>` via Hilt
    multibinding (`SkillModule.kt`); the router just does a map lookup and dispatches.
    Adding a new capability is a new skill class and one new `@Binds` line, not another
    router branch.

## Phase 6 — Trust & Autonomy Core

The start of NUA's "AI operating system" layer — see `ROADMAP.md` for the full 45-point
spec this and every future phase is scoped against, and why the phases are sequenced the
way they are.

1. **Autonomy Firewall (T0–T5).** `AutonomyTier` replaces the old README-only Tier 1/2/3
   description with a real per-action mapping (`autonomyTierFor(NuaActionType)`), and
   every dispatch is tagged with it in the audit trail.
2. **Action Audit Trail.** Every Tier action `NuaIntentRouter` dispatches, and every
   plan/reply the user confirms or declines, is logged to `ActionOutcomeEntity` — what,
   which tier, whether it worked, when. Visible in Settings.
3. **Trust Score.** `TrustScoreEngine` computes a 0-100 score from the audit trail alone
   (a declined proposal counts as half a failure, an execution failure as a full one) —
   nothing here is asserted, only computed from what NUA actually logged.
4. **Adaptive autonomy.** `AutonomyPreferenceEntity` tracks how many times you've
   approved a given action type; once it crosses a threshold, Settings offers a one-tap
   "always allow automatically" toggle instead of NUA proposing it mid-conversation.
5. **Trust Ledger self-report.** At the `ESTABLISHED` familiarity tier, rate-limited to
   at most once every 14 days and only when there's something to say, NUA now reports
   unprompted on what it's gotten wrong since the last report — the inverse of how most
   assistants behave.
6. **Memory OS typing.** `UserFactEntity` gained a `MemoryType` (identity /
   episodic / semantic / behavioral / emotional / relationship), a `source` ("why do you
   remember this"), and `lastUsedAt` (now actually touched on every relevant-fact
   lookup, not just schema). The control UI over this typing shipped in Phase 8.

## Phase 7 — Goals & Context Engine

1. **Context Engine.** `ContextEngine.currentSnapshot()` formalizes signals
   `MorningBriefing` and geofencing already gathered independently (weather, today's full
   calendar, connectivity, notification summary) into one queryable `ContextSnapshot`,
   with a `describe()` renderer meant to drop straight into a Claude prompt.
2. **NUA Goals.** Durable goals ("get my mornings under control"), distinct from a
   one-shot `TaskPlanner` request — CRUD via `GoalRepository`, editable in Settings.
   `GoalReviewWorker` (weekly WorkManager job) checks each active goal against a fresh
   `ContextSnapshot` and asks Claude for at most one concrete, non-generic observation —
   explicitly instructed to say nothing rather than force a proposal, same discipline as
   `MemoryConsolidationWorker`'s summarization prompt.
3. **Daily briefing, rewritten.** `MorningBriefing` now reads from `ContextEngine`
   instead of independently querying `WeatherRepository`/`CalendarReader` — one source of
   truth for "the current situation" instead of two features maintaining their own copy.
4. **"What should I do now?"** A lightbulb icon in the top bar
   (`NuaViewModel.whatShouldIDoNow()`) asks `WhatNowAdvisor` for exactly one concrete next
   action given the current `ContextSnapshot` and active goals, shown as a normal chat
   reply — the zero-typing entry point into everything this phase built.

## Phase 8 — NUA Dreams 2.0, Decision Journal, Second Brain search, Timeline & Memory OS controls

**NUA Dreams.** `DreamSynthesisWorker` runs at most once a week and looks across known
facts, recent goal observations, and the trust ledger together for exactly one insight
that connects at least two of them — explicitly instructed to reject anything that's
just a repackaged summary, and to say nothing at all rather than force an insight into
existence. Gated on a minimum amount of logged history so it can't hallucinate
connections out of a near-empty database. Ten categories (`DreamCategory`): opportunity,
pattern, reminder, concern, optimization, relationship, finance, productivity, learning,
business. Surfaced once per app open the same way the Trust self-report is ("Something
occurred to me...") and kept visible as a history in Settings.

**Decision Journal.** A `DecisionsCard` in Settings for logging a decision and (optionally)
the reasoning behind it, then coming back later to record how it turned out. Unlike Dreams
and Goal observations, nothing here is inferred by NUA — it's a place you write to
deliberately, backed by `DecisionEntity`/`DecisionRepository`.

**Second Brain search.** A dedicated search screen (search icon in the top bar) over
everything NUA remembers, has noticed, or has logged — facts, dreams, and decisions
together, ranked by the same lexical token-overlap approach as `FactRelevance`
(`SecondBrainSearch`). Distinct from chat, where `FactRelevance` quietly injects relevant
facts into replies — this is an explicit, standalone lookup.

**Timeline.** A chronological feed (history icon in the top bar) merging facts, dreams,
decisions, and goal observations by timestamp into one scrollable view — `TimelineBuilder`
reads back the existing memory surfaces in the order things actually happened rather than
introducing new storage of its own.

**Memory OS controls.** Tapping a fact in Settings now opens a drill-down
(`FactDetailDialog`) answering "why do you remember this" per fact — category, memory
type, source, confidence, when it was learned, last updated, and last used in
conversation. A filter row lets you narrow the list to one `MemoryType` at a time, with
a "forget all N of this type" bulk action once a type is selected
(`MemoryDao.deleteFactsByType`). This closes out Phase 8's full scope.

## Phase 9 — NUA Vision as a system & Document Intelligence

The camera → Claude vision pipeline is now structured as see / understand / remember /
act instead of one opaque "describe this photo" call:

- **See + understand.** `VisionAnalyzer` classifies the photo (document, receipt, food,
  product, screen, sign, whiteboard, object, clothing, plant, or vehicle) and describes
  it in one structured Claude call. The user's photo turn is now actually saved to
  conversation history too — previously only the assistant's description was persisted,
  which meant a photo exchange had no matching user turn in the stored history.
- **Remember.** A "Remember" action on the result (shown right above the input row after
  NUA looks at a photo) turns it into a durable fact — deliberate, same as the Decision
  Journal, not automatic fact extraction on every photo.
- **Act.** No new action-type plumbing was needed: because the photo turn is now real
  conversation history, a normal follow-up message like "add these to my list" already
  has that context available through the existing conversational flow.
- **Monitor, permission-gated.** "Monitor this" records a baseline (`VisionMonitorRepository`
  — description plus a durably-copied photo, since captures otherwise live in a
  clearable cache dir). NUA cannot take photos on its own; capture is always delegated to
  the system camera app. So `VisionMonitorWorker` runs daily and, once a monitor's
  interval has elapsed, posts a notification asking the user for a fresh photo — the user
  supplies it, and `VisionAnalyzer.compareAgainstBaseline` reports what's changed. A
  `Vision monitors` card in Settings lists active monitors with a recheck-now camera
  button and a way to stop watching. The permission gate here is the explicit "Monitor
  this" action itself, not an OS-level runtime permission.

**Document Intelligence.** A Documents screen (document icon in the top bar) for reading
PDFs, Word documents, and images:

- **Ingestion, no new dependency.** `PdfTextExtractor` rasterizes PDF pages with
  Android's built-in `PdfRenderer` and transcribes each via Claude vision — PdfRenderer
  exposes pixels, not a text layer, and plenty of real-world PDFs are scans with no text
  layer anyway. `DocxTextExtractor` reads a `.docx`'s `word/document.xml` directly with
  `java.util.zip` and a SAX parser, both already part of the platform — a `.docx` is just
  a zip of XML, so no Office document library was needed. Images reuse the same
  `DocumentAnalyzer.transcribePage` PDF pages go through.
- **Summarization + expiry detection.** `DocumentAnalyzer.summarize` asks Claude for a
  short summary — obligations, deadlines, money — plus a single ISO expiry/renewal date
  when the document genuinely has one.
- **Targeted Q&A and comparison.** `DocumentAnalyzer.answer` takes one or more documents'
  text as context. The Documents screen's "Ask" dialog lets you check additional
  documents to include, so comparing two contracts is just asking a question with both
  checked — no separate compare mode needed.
- **Expiry reminders.** `DocumentExpiryWorker` runs daily and notifies once a document's
  expiry date is within 30 days (or already past) and hasn't been reminded about yet.

## Phase 10 — Communication Centre (partial)

Extends the `NuaActionType`/`NuaSkill` router pattern already used for smart home and
email to two more capabilities — both built on official, on-device APIs, no OAuth:

- **SMS send.** `SmsSendSkill` resolves a spoken name to a phone number via
  `ContactResolver` (`ContactsContract`, or used directly if it already looks like a
  number), then proposes the exact recipient and text for confirmation — never sends
  without it, the same "ask first" treatment as replying to a notification. `SmsSender`
  wraps Android's built-in `SmsManager`. `SEND_SMS` is requested lazily, right in the
  confirmation dialog, rather than upfront at launch, since it's one of Google Play's
  restricted permissions and most users will never trigger it.
- **Calendar invitations.** `CalendarInviteSkill` extends `CalendarReader` (previously
  read-plus-personal-reminders only) with `createInvitation`, which inserts a
  `CalendarContract.Attendees` row alongside the event once `ContactResolver` finds an
  email for the named attendee. Executes immediately and reports back — the same "do it,
  then say so" tier as smart-home actions — since the underlying calendar write already
  proved itself safe via `createReminder`. If no email resolves, the event is still
  created as a personal note, and NUA says so rather than claiming an invitation that
  didn't reach anyone.
- **Gmail — genuinely blocked, not a gap.** The `email/` scaffold
  (`EmailRepository`/`UnconfiguredEmailRepository`) has zero OAuth code behind it; unlike
  calendar/contacts there's no on-device content-provider equivalent for email. A real
  integration needs a Gmail API project and OAuth consent set up outside this codebase.
- **WhatsApp** wasn't built — no compliant on-device API path was found, and this project
  won't build against anything that risks the user's account.

## Phase 11 — Security, Audit, and Self-Diagnostics (partial)

- **Self-Diagnostics.** Settings → Self-diagnostics shows one row per area (API, memory,
  voice, location, calendar, email, automation, Wear, Auto), each with a real, specific
  status — never a generic "something went wrong." `diagnostics/SelfDiagnosticsRepository`
  gathers the actual signals (permission checks, a real `MemoryDao.countMessages()` query,
  `OwnerVerifier.isEnrolled()`, the real `EmailRepository.checkInbox()` result,
  `NuaAccessibilityService.isEnabled()`); the pure `evaluateDiagnostics` function turns
  those into statuses and is unit tested independently of Android. Wear and Auto are
  always reported as informational rather than OK/error, honestly: there's no Wearable
  Data Layer hookup or car-session hook to check a live connection against yet. "Test API
  connection" is a separate, manual, opt-in real round trip to Claude — key *presence* and
  key *actually working* are never conflated.
- **Biometric step-up authentication.** Every T3+ confirmation dialog — SMS send, reply
  to a notification, plan confirmation — is gated behind `security/BiometricGate`
  (fingerprint, face, or device PIN via `androidx.biometric.BiometricPrompt`) whenever the
  device actually has one enrolled; `security/StepUpPolicy.requiresStepUpAuth` (unit
  tested) decides which tiers qualify. `MainActivity` is now a `FragmentActivity` since
  `BiometricPrompt` requires one. On a device with no biometric/PIN set up, step-up is
  skipped and the existing confirm-only flow still works — gating on hardware that
  doesn't exist would lock users out, not add security — and Settings → Security says so
  plainly.
- **Local encryption audit.** Settings → Security also lists what's actually encrypted
  today via `security/EncryptionAudit.encryptionAuditEntries` (unit tested): the Claude
  API key and owner voice profile are real AES-256-GCM `EncryptedSharedPreferences`
  backed by the Android Keystore, while conversation history, facts, documents,
  decisions, and goals sit in a plain Room database protected only by Android's per-app
  sandbox — stated honestly, not glossed over.
- **Prompt Injection Firewall.** A structural boundary, not just prompt wording, between
  data NUA reads and instructions it follows. `security/UserUtterance` is a value class
  wrapping only the user's own literal chat/voice turn; `NuaIntentRouter.route` and
  `IntentClassifier.classify` — the one audited entry point into action dispatch — now
  require it instead of a bare `String`, so a future feature can't silently pipe
  document/vision/notification/email text into dispatch without an explicit, reviewable
  wrap. Separately, `security/UntrustedContent.wrapUntrusted` (unit tested) wraps external
  text in an explicit `<untrusted_...>` tag before it reaches a Claude prompt and
  neutralizes any such tag already inside the text, so content can't forge a closing tag
  to escape the block; it's wired into `DocumentAnalyzer.summarize`/`answer` and
  `VisionAnalyzer.compareAgainstBaseline`, the two places raw string concatenation of
  external text was found. Even if the accompanying system-prompt directive is ignored,
  wrapped content still has no path into `route`/`classify` — an injected instruction can
  produce a wrong reply, never an unauthorized action.
- **Agent Sandbox.** Every skill declares a `SkillManifest` — its inputs, hard permission
  preconditions, and time budget — and the property is abstract on `NuaSkill`, so a skill
  can't be added without declaring one. Dispatch never calls a skill directly; it goes
  through `SkillSandbox`, which checks required parameters are present, **strips any
  parameter the skill didn't declare** (so a classifier inventing an extra key can't
  smuggle it into a tool call), preflights permissions with a specific message instead of
  a vague downstream failure, and runs the skill under a declared timeout with exception
  containment — a hanging or throwing skill becomes a reported failure, not a stuck turn.
  Risk tier (`autonomyTierFor`) and the audit trail (`TrustRepository`) already existed
  and are reused rather than duplicated, so sandbox refusals, timeouts, and crashes all
  land in the same log as ordinary outcomes. It's a policy envelope, not OS-level
  isolation — skills still run in-process with NUA's permissions — but the dispatch path,
  which is where untrusted classifier output flows, is fully constrained.
- **Not yet built:** attestation-based device trust (no server component exists to attest
  to yet) — see `ROADMAP.md`'s Phase 11 section.

## Languages

NUA understands and replies in ten languages: English, Hindi, Gujarati, Marathi,
Italian, Spanish, Haryanvi, Punjabi, Vietnamese, and Chinese (Mandarin) — the catalog
lives in `voice/NuaLanguage.kt`.

- **Text chat** auto-mirrors whichever of these the user's message is in, turn by turn,
  no setting required — `PersonalityEngine`, `IntentClassifier`, `TaskPlanner`, and
  `MorningBriefing` all carry the same mirroring directive. Explicitly asking NUA to
  switch language works the same way (it's just an instruction Claude follows from
  conversation history).
- **Pinning a language** in Settings overrides mirroring — NUA replies in it regardless
  of what language the user writes in — and also sets the concrete locale used for
  speech input/output.
- **Voice (STT/TTS)** can't auto-detect language the way text chat does — Android's
  `SpeechRecognizer` needs one language hint per listening session, and `TextToSpeech`
  needs one locale per utterance. Voice defaults to English until the user pins a
  language in Settings. Haryanvi has no Android speech locale of its own, so it shares
  Hindi's for voice — NUA still understands and replies to Haryanvi as text on its own
  terms.
- Gujarati, Haryanvi, and some regional locales may not have installed TTS voice data on
  a given device; `VoiceManager.speak` falls back to English automatically when that
  happens rather than staying silent.
- `FactExtractor`'s local "does this look like a fact" regex hint is English-only (a
  reliable one across Devanagari/Gujarati/Gurmukhi scripts isn't something to fake).
  Non-English fact hints still get caught by the periodic every-4th-turn sweep, just
  less eagerly than English ones.

## Wake words

NUA can wake up to more than one phrase — the catalog lives in `services/WakePhrase.kt`
(`WakePhrases.ALL`). "Jarvis" works immediately (a Porcupine built-in keyword, no extra
files needed). The rest — "Hey NUA", "Hello NUA", "NUA", "Daddy's Home", "Wake up Sleepy
Head" — are custom phrases that each need a Porcupine model trained on [Picovoice
Console](https://console.picovoice.ai/) and dropped into `app/src/main/assets/` (see
`app/src/main/assets/README.md` for the exact steps and expected file names).

`NuaForegroundService` only activates phrases whose model is actually present
(`availableWakePhrases()`) — a declared-but-missing phrase is skipped, not a crash — and
Settings → Wake words shows each one as Active or Needs setup. Adding a new phrase later
is one line in `WakePhrases.ALL` plus dropping in the trained `.ppn` file; nothing else
needs to change, including the multi-keyword detection code in `NuaForegroundService`.

## Known gaps

- Test coverage is limited to the pure-logic pieces that don't need a live Android
  runtime: `KeywordIntentMatcher`, `FactExtractor.shouldConsider`'s gating heuristic,
  `NotificationPriorityScorer`, `FactRelevance`, and `extractJsonPayload`. Nothing
  exercises the Room DAO, `ClaudeApiClient`'s HTTP/streaming layer, WorkManager
  scheduling, the Glance widget, or Compose UI yet — those would need Robolectric or
  instrumented tests.
- `OwnerEnrollment`/`OwnerVerifier` (Picovoice Eagle, `ai.picovoice:eagle-android:3.0.2`)
  were verified against the SDK's own demo app source after the first version (written
  from recall, pinned to a nonexistent 1.0.4 artifact) failed CI — the real API turned
  out to use a plain `Eagle` class for recognition (not `EagleRecognizer`) and requires
  `EagleProfiler` to stay alive across enrollment calls to keep its progress. Still not
  exercised against a real device or the actual Picovoice service, just the SDK's
  published source.
- Android Auto (`car/NuaCarAppService.kt`) and the Wear OS tile (`:wear` module) are
  both real usage of their respective official libraries (Car App Library, classic Wear
  Tiles API) but neither has been tested on a head unit, the Desktop Head Unit
  emulator, a Wear OS device, or the Wear emulator — CI only confirms they compile.
  `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` and `IOT` as the declared category are both
  things to revisit before any real Play Store submission.
- The Wear tile has no connection to the phone app's data (calendar, weather,
  notifications) — it's static text. Wiring that up needs the Wearable Data Layer API
  (`DataClient`/`MessageClient`), which isn't built yet.
- Geofencing needs `ACCESS_BACKGROUND_LOCATION`, which Google Play gates behind a
  separate declaration form before a build using it can be published — filing that is
  out of scope for this repo's current pre-release state.
- The usage/cost dashboard (`UsageTracker`) estimates cost from published list pricing
  hardcoded in `UsageTracker.kt` — it will drift if Anthropic's pricing changes and
  doesn't reflect any organization-specific discount.
- The Trust Score (`TrustScoreEngine`) is a straightforward, documented formula over
  logged outcomes — it hasn't been validated against real usage patterns over time, and
  the "5 approvals" adaptive-autonomy threshold is a starting guess, not a tuned value.
  The database version bump this phase needed (3 → 4) uses the same destructive-migration
  fallback as prior phases — fine pre-release, not something to carry into a real release.
- The WorkManager + Hilt wiring (`NuaApplication.Configuration.Provider`, the disabled
  `WorkManagerInitializer` in the manifest, `@HiltWorker` on `MorningBriefingWorker`)
  follows the documented Google pattern for this combination but is, like everything
  else in this list, unverified against a real build until CI (or a real device) confirms it.
- The settings screen covers viewing/forgetting facts and Tier 2 / battery-optimization
  status, but there's still no way to adjust the fact-extraction cadence or rotate the
  Claude API key from within the app (Tier 2 itself stays toggle-only via Android
  Settings > Accessibility, by design — the app only deep-links there).
- `AppLauncher`'s package-name map is best-effort; it falls back to searching installed
  launcher activities by label, but hasn't been verified against a real device's installed
  app set.
- No launcher icon design — `ic_launcher_foreground`/`ic_launcher_background` are
  placeholders (a plain glyph on a solid field), not real branding.
- Wake-word listening (`NuaForegroundService`) requires a Picovoice console access key
  (`-PPICOVOICE_ACCESS_KEY=...`); without one it logs a warning and stops itself rather
  than silently doing nothing. Right now only "Jarvis" will actually fire on a fresh
  checkout — the other five phrases are wired up end to end but need their trained
  `.ppn` models added to `app/src/main/assets/` (none are bundled in this repo since
  they have to be trained per-phrase on Picovoice Console, not something generatable
  here). See the Wake words section above.
- `NuaAccessibilityService` is a structural skeleton only — the confirmation contract
  (per-action, in-the-moment, fail-loud via `NuaAccessibilityService.lastFailure`) is
  settled, but no concrete Tier 2 action is implemented.
- The development sandbox this was built in has no Android SDK, and its outbound network
  policy blocks `dl.google.com` (the only host that serves the Android Gradle Plugin and
  platform artifacts — confirmed by direct probing, not a timeout/misconfiguration), so
  `./gradlew assembleDebug` could never be run there. See **Continuous Integration**
  below for how this is actually verified instead.

## Continuous Integration

`.github/workflows/android-build.yml` builds the app on GitHub's own runners (which have
normal internet access, unlike the sandbox above) on every push and PR: it sets up the
Android SDK, runs `./gradlew test`, then `./gradlew assembleDebug`, and uploads the debug
APK and test reports as workflow artifacts. This is the real, end-to-end build
verification for this project — check the Actions tab for current status rather than
assuming a hand-reviewed diff compiles.

## Building

Requires the Android SDK (compileSdk 35) and a JDK 17+. Set your Claude API key at
runtime through the in-app dialog (stored via `SecureKeyRepository`, never in a build
file). Optionally pass a Picovoice access key for wake-word support — the same key is
reused for Eagle voice enrollment (Settings → Voice ID), though Eagle may need its own
product enablement in the Picovoice console depending on your plan:

```
./gradlew assembleDebug -PPICOVOICE_ACCESS_KEY=your_key_here
```
