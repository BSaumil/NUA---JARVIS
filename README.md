# NUA

A personal AI assistant for Android (Kotlin, Jetpack Compose, Hilt/MVVM, Claude API).

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
  ai/                        → ClaudeApiClient (streaming + prompt caching), PersonalityEngine,
                                IntentClassifier, FactExtractor, FactRelevance, TaskPlanner
  voice/                     → VoiceManager (STT/TTS wrapper), NuaLanguage (10-language
                                catalog), LanguagePreferenceStore (pinned language),
                                OwnerEnrollment/OwnerVerifier (Picovoice Eagle, scaffold)
  automation/                → AppLauncher (Tier 1), NuaIntentRouter (keyword + Claude-fallback
                                routing), NuaAccessibilityService (Tier 2 skeleton)
  weather/                   → WeatherRepository (Open-Meteo, no API key, TTL-cached)
  calendar/                  → CalendarReader (on-device CalendarContract, read + confirmed-plan
                                reminder writes, no OAuth)
  memory/                    → MemoryStore.kt (Room: messages + user_facts),
                                SecureKeyRepository (encrypted Claude API key storage)
  notifications/              → NuaNotificationListenerService, NotificationRepository,
                                NotificationPriorityScorer, NotificationStatsStore,
                                NotificationReplySender (Tier 1 quick-reply)
  media/                     → MediaControlManager (MediaSessionManager-based)
  briefing/                  → MorningBriefing, MorningBriefingWorker + BriefingScheduler
                                (WorkManager-based proactive scheduling)
  smarthome/                 → SmartHomeRepository extension point (Matter/Google Home, scaffold)
  widget/                    → NuaWidget (Jetpack Glance home-screen widget)
  services/                  → NuaForegroundService (multi-phrase wake-word listening via
                                Porcupine), WakePhrase (extensible wake-word catalog)
  di/                        → AppModule (Hilt module for Room, OkHttp, JSON)
  ui/                        → NuaScreen (Compose), NuaViewModel (@HiltViewModel), NuaTheme
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
- `OwnerEnrollment`/`OwnerVerifier` (Picovoice Eagle) were written from documentation
  recall, not against the actual SDK artifact — class/method names (`EagleProfiler`,
  `EagleRecognizer`, `EagleProfile`, `.minEnrollSamples`, `.export().bytes`, etc.) need
  verification against `ai.picovoice:eagle-android:1.0.4` once this actually builds; the
  surrounding architecture (encrypted profile storage, per-clip enrollment progress,
  Settings UI) should hold up even if some call signatures need adjusting.
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
