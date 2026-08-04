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
  NuaApplication.kt        → @HiltAndroidApp entry point
  MainActivity.kt           → Compose host, permission requests, wake-word broadcast receiver
  ai/                        → ClaudeApiClient, PersonalityEngine, and Phase 3 intelligence:
                                IntentClassifier, FactExtractor, TaskPlanner
  voice/                     → VoiceManager (STT/TTS wrapper), NuaLanguage (10-language
                                catalog), LanguagePreferenceStore (pinned language)
  automation/                → AppLauncher (Tier 1), NuaIntentRouter (keyword + Claude-fallback
                                routing), NuaAccessibilityService (Tier 2 skeleton)
  weather/                   → WeatherRepository (Open-Meteo, no API key)
  calendar/                  → CalendarReader (on-device CalendarContract, read + confirmed-plan
                                reminder writes, no OAuth)
  memory/                    → MemoryStore.kt (Room: messages + user_facts),
                                SecureKeyRepository (encrypted Claude API key storage)
  notifications/              → NuaNotificationListenerService, NotificationRepository,
                                NotificationPriorityScorer, NotificationStatsStore
  media/                     → MediaControlManager (MediaSessionManager-based)
  briefing/                  → MorningBriefing (assembles facts, hands to Claude for phrasing)
  services/                  → NuaForegroundService (wake-word listening via Porcupine, "Jarvis")
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

## Known gaps

- Test coverage is limited to the pure-logic pieces that don't need a live Android
  runtime: `KeywordIntentMatcher`, `FactExtractor.shouldConsider`'s gating heuristic,
  `NotificationPriorityScorer`, and `extractJsonPayload`. Nothing exercises the Room DAO,
  `ClaudeApiClient`'s HTTP layer, or Compose UI yet — those would need Robolectric or
  instrumented tests.
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
  than silently doing nothing.
- `NuaAccessibilityService` is a structural skeleton only — the confirmation contract
  (per-action, in-the-moment, fail-loud via `NuaAccessibilityService.lastFailure`) is
  settled, but no concrete Tier 2 action is implemented.
- This environment has no Android SDK and the outbound network policy blocks
  `dl.google.com` (the only host that serves the Android Gradle Plugin and platform
  artifacts), so the build could not be verified end-to-end with `./gradlew assembleDebug`
  here. Every file was written and cross-checked by hand for import/package correctness;
  it still needs a real build on a machine with the Android SDK before shipping.

## Building

Requires the Android SDK (compileSdk 35) and a JDK 17+. Set your Claude API key at
runtime through the in-app dialog (stored via `SecureKeyRepository`, never in a build
file). Optionally pass a Picovoice access key for wake-word support:

```
./gradlew assembleDebug -PPICOVOICE_ACCESS_KEY=your_key_here
```
