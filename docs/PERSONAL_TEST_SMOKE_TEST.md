# Personal Test Smoke Test

Directive item 18. A structured, manual, on-device checklist for whoever installs a real
NUA build for personal testing — this document exists because nothing in this repository
can actually execute it. CI (`.github/workflows/android-build.yml`) proves every build
compiles, every unit test passes, and all four static boundary audits hold; it cannot
press a button, say a wake word into a real microphone, or pair a real Wear OS watch.
This checklist is exactly the remaining gap between "CI green" and "actually works for a
person," organized by the app's real navigation structure
(`ui/nav/NuaDestination.kt`: Home / Ask / Memory / Act / You) plus the cross-cutting
systems (voice, background workers, companion surfaces) that don't live under one tab.

**How to use this:** work top to bottom on a real device with a fresh install. Check
each box, and where a step names an expected result, confirm it matches before moving
on. A step that can't be exercised without something this environment doesn't have
(a second device, a real phone number, a paired watch, a car head unit) is marked
**[external]** — do those last, and it's fine to stop before them if that hardware
genuinely isn't available; everything above the `---` before "External-hardware-only
steps" exercises the app on the phone alone.

## 0. Pre-flight

- [ ] Install the debug (or signed release) APK from a green CI run on a real Android
      device, API 26+ (`minSdk`). A fresh install, not an upgrade over a dev build with
      stale schema — first install on this device, or uninstall first.
- [ ] Launch the app. It should not crash on cold start.
- [ ] Settings → Diagnostics → confirm a Claude API key is configured (`SecureKeyRepository`);
      if not, Settings has an entry point to add one. Without a key, every AI-backed
      feature below (chat, fact extraction, dreams, goal review, document Q&A, the
      counterfactual simulator) degrades to an honest error, not a crash — confirm that
      degradation reads as an error message, never a silent no-op, if you're testing
      without a key.

## 1. Permissions and special access

Android groups these two ways — confirm both paths work:

- [ ] Normal runtime permission prompts appear on first launch or first use (not all at
      once): `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `READ_CALENDAR`/`WRITE_CALENDAR`,
      `READ_CONTACTS`, `SEND_SMS`, `ACCESS_COARSE_LOCATION`/`ACCESS_FINE_LOCATION`/
      `ACCESS_BACKGROUND_LOCATION`. Denying one should degrade that one feature
      gracefully (an honest "I don't have permission" message), not crash the app or
      block unrelated features.
- [ ] Settings → notification digest → a button takes you to Android's own notification
      access screen; granting it there (not a normal runtime prompt — a special access
      grant) should make `NuaNotificationListenerService` start capturing notifications.
- [ ] Settings → Tier 2 automation card → "Open Android Settings" takes you to
      Accessibility; granting NUA's service there should flip the card to "Enabled."
      With it off, "Test: go home" should not appear at all (never a button that does
      nothing when tapped).

## 2. Home

- [ ] Home destination loads without error and shows *something* — a next-best-action
      recommendation, a morning-briefing excerpt, or an honest empty state. It should
      never show a stale/fabricated recommendation when nothing real is available
      (`WhatNowAdvisor`'s own contract).

## 3. Ask (chat + voice)

- [ ] Type a plain message ("what's the weather") and send. A real reply streams in
      token-by-token, not all at once.
- [ ] Ask for something Tier 1 and unambiguous ("open Spotify", "pause the music",
      "what's the weather"). It executes without a confirmation dialog and reports what
      it did.
- [ ] Ask for something that needs confirmation ("text [a contact] saying I'm on my
      way"). A confirmation dialog appears showing the exact recipient and message
      before anything sends — confirm it, and confirm the text actually sends
      **[external — needs a real contact/phone number you can verify delivery to]**.
      Decline it instead and confirm nothing sent.
- [ ] Tap the mic / say a configured wake word (Settings → Wake words shows which are
      active — "Jarvis" works out of the box). NUA should start listening, a spoken
      reply should play back (TTS), and after it finishes, if continuous-conversation
      mode is active, NUA should listen again automatically for a follow-up — up to the
      configured cap, then stop.
- [ ] **Barge-in**: while NUA is actively speaking a reply out loud, say the wake word
      again. NUA's speech should stop immediately (not finish the sentence) and it
      should start listening right away — this is the one real fix this round made to
      `onWakeWordDetected`; if NUA keeps talking over you, that's a regression.
- [ ] Send a message in a non-English supported language (e.g. Hindi, if you can type
      or speak it) with no language pinned in Settings. The reply text should mirror
      that language, and — this is the new part — the reply should be **spoken** in a
      Hindi voice too, not a flat English TTS voice reading Hindi text. Devanagari/
      Gujarati/Gurmukhi/Chinese script should each pick the matching voice automatically;
      switching between English/Italian/Spanish by voice alone is a known, named
      limitation (`NuaLanguage.scriptDetectedLanguage`'s own doc comment) — don't expect
      that to work.
- [ ] Settings → Personality card → move Humor to High or Low, send a message, confirm
      the reply's tone actually shifted versus default. Move both axes back to Default
      and confirm the reply reads like the original base persona again (no residual
      skew).
- [ ] Ask something that implies a multi-step plan ("I'm going camping tomorrow").
      A plan proposal with concrete reminders should appear, require confirmation, and
      — once confirmed — real calendar reminders should appear in the device's own
      Calendar app.

## 4. Memory

- [ ] Memory → Search: facts NUA has extracted from the conversation above appear.
      Forget one; confirm it's actually gone (not just hidden) on reopening the screen.
- [ ] Memory → Timeline: chronological entries render without error.
- [ ] Memory → Documents: attach a document (image/PDF depending on what's wired),
      confirm it's summarized, and ask a question about it — confirm the answer is
      grounded in the document's actual content.
- [ ] Memory → Privacy: data export produces real text containing your actual
      facts/goals/decisions/dreams, not a placeholder.

## 5. Act

- [ ] Act → Recipes → "Log a recipe" with a plain-text description combining a known
      action and an unknown one ("open spotify and do something nobody could parse").
      It should compile, show a correct "N not understood" badge, and running it should
      execute the real part and report the gap honestly for the rest.
- [ ] Edit that recipe's description, confirm the saved steps actually change.
- [ ] Delete it, behind the confirmation dialog, and confirm it's gone.
- [ ] Act's recent-action log reflects what actually ran above (open app, text sent,
      etc.) with correct outcome state, not all showing as uniformly "succeeded."

## 6. You (Settings)

- [ ] Reply language: pin one explicitly, send a message in a different language, and
      confirm the reply comes back in the *pinned* one regardless (overriding mirroring).
      Set back to Auto.
- [ ] Morning briefing: toggle on with a near-future time if you want to see a real one
      fire; otherwise just confirm the toggle persists after leaving and reopening
      Settings.
- [ ] Voice ID: record an enrollment clip, confirm progress updates, and confirm
      "owner verified" gates whatever it's supposed to gate (if wired to a real action).
- [ ] Geofences: add one at your current location with a short radius, leave and
      re-enter the radius (or edit the location to simulate it), and confirm the
      notification fires once, not repeatedly.
- [ ] Trust / Autonomy: after approving the same action type a few times, confirm the
      "Always allow" suggestion appears; confirm "Try shadowed" creates a shadow
      contract (visible in the new Autonomy contracts card) that predicts but never
      auto-acts, and that its accuracy readout updates after a few resolved predictions.
      Revoke it and confirm it disappears from the active list.
- [ ] Goals: add one, confirm it shows up and that `GoalReviewWorker`'s weekly pass
      doesn't need to be manually triggered to be considered "working" (reviewed
      separately under Background workers below).
- [ ] Decisions: log one with alternatives *and* an outcome recorded. The "What if?"
      button should appear only once both exist; tapping it should produce real,
      clearly-speculative text (never stated as fact) grounded in what you actually
      typed. Log a decision missing either field and confirm the button doesn't appear.
- [ ] Vision monitors: if any are configured, confirm a recheck against a photo
      produces a real result, not a stale cached one.
- [ ] Diagnostics: "Test API connection" reports a real pass/fail against the
      configured key, not a hardcoded success.
- [ ] Security: encryption audit entries render; biometric step-up actually prompts for
      biometrics on whatever action requires it.
- [ ] Tier 2 automation: with the service enabled, tap "Test: go home" and confirm the
      device actually navigates to the home screen (`performGlobalAction`'s real,
      previously-unverified dispatch — see §7 below for why this specific check
      matters more than most).
- [ ] Battery optimization: the exemption button actually opens the system dialog and
      the card's state reflects the real post-grant status.
- [ ] About: version/build info is present and not a placeholder string.

## 7. Background workers (needs the app left running, or a wait)

These run on `WorkManager` schedules (`NuaApplication.onCreate`) — hard to force without
Android Studio's WorkManager inspector, but worth a sanity check if you leave the app
installed for a few days:

- [ ] Memory consolidation (daily): old messages get summarized/pruned without losing
      anything important-looking.
- [ ] Goal review (weekly): goals you added actually get revisited; a new
      `GoalObservationEntity` appears.
- [ ] Dream synthesis (weekly): a new Dream appears in Memory, with connections you can
      actually trace back (Memory's `world_relationships` read-side) to real facts/goals.
- [ ] Vision monitor / document expiry (daily): stale entries actually get flagged or
      cleaned up, not silently ignored forever.

## 8. The one check that matters most this round

Everything above has *some* precedent from before this session. These four do not — each
is new, device-dependent, and explicitly marked "implemented but not verified against a
real device" in its own commit this round. If you can check only a handful of boxes
on a real device, make it these:

- [ ] **Tier 2 system navigation** (§6 above) — `performGlobalAction` actually working
      is the single highest-confidence check available without external hardware.
- [ ] **Code-switching voice output** (§3 above) — confirms `scriptDetectedLanguage`
      picks a real, audibly-different TTS voice, not just that the code compiles.
- [ ] **Barge-in** (§3 above) — confirms `stopSpeaking()` actually cuts off a real TTS
      utterance in flight, not just that the call exists.
- [ ] **Shadow-mode autonomy contracts** (§6 above) — confirms a prediction is recorded
      and later resolved correctly against what you actually did, the one thing a shadow
      contract exists to prove before anyone would trust it live.

---

## External-hardware-only steps

Each of these needs hardware this development environment has never had access to (no
`adb`, `emulator`, or Android SDK tooling at all — confirmed throughout this session,
most recently for Presence Mesh and Android Auto). CI only confirms the relevant module
compiles against the real library API; none of the following has ever executed:

- [ ] **[external]** Wear OS tile shows live phone presence. Needs a real paired Wear OS
      watch (or the Wear emulator, which itself needs a full Android Studio install this
      sandbox doesn't have). Pair one, open the NUA app on the phone (which publishes
      presence once per open — `PresenceRepository.publishSelf`), and confirm the
      watch's NUA tile updates from the static "Say a wake word to talk" text to
      "Phone active Xs ago." If it doesn't, the real `DataClient` round trip
      (`docs/PRESENCE_MESH_RFC.md` §4's named open question) is the first thing to
      debug — the code is structurally correct per Google's documented API but has
      never actually run.
- [ ] **[external]** Android Auto. Needs a real head unit or the Desktop Head Unit
      emulator (needs Android Studio). Connect, confirm NUA appears as an available app
      under its `IOT` category, and that `createHostValidator()`'s release-build
      allowlist (`androidx.car.app.R.array.hosts_allowlist_sample`) doesn't reject the
      real host — debug builds use `ALLOW_ALL_HOSTS_VALIDATOR` instead, so this
      specifically needs a *release* build to test honestly.
- [ ] **[external]** SMS delivery confirmation (§3 above) to a real, different phone
      number you control, confirming the text actually arrived, not just that
      `SmsManager` accepted it.
- [ ] **[external]** Picovoice Eagle owner-voice verification against the real Picovoice
      service (`OwnerVerifier`'s own doc comment: verified against the SDK's published
      source, never against a live device or the actual service).

## What "smoke test" means for this document specifically

This document is the test plan, not the test *run*. No session without real device
access can mark any box above as checked — doing so would be exactly the "claimed
verification that wasn't run is worse than no verification at all" failure this
project's own engineering discipline (`docs/ENGINEERING.md`) rules out. What this
session verified instead, honestly: every build this document's steps depend on is
CI-green (`.github/workflows/android-build.yml`, all four static audits plus the full
unit test suite including the new Robolectric-backed tests), and every code path this
checklist exercises was reviewed line-by-line against its own commit's reasoning before
being marked done. The actual pass — ticking these boxes on a real phone — is the
explicit next step for whoever has that device.
