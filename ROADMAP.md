# NUA Roadmap — Beyond JARVIS Master Spec

This roadmap captures the full 45-point "Beyond JARVIS" master spec in the order it will
actually be built. It exists so nothing from that spec gets lost, and so every future
session (this one or a fresh one) can pick up exactly where the last one stopped instead
of re-deriving priorities from scratch.

**Ground rule:** a phase is only marked done here once it is real, wired code merged to
`Main` and verified green in CI — never a stub claiming a capability it doesn't have.
NUA's own persona promises "never fabricate a completed action"; this roadmap holds
itself to the same standard. Items that are structurally real but need something outside
this codebase (OAuth credentials, a physical Wear/Auto device, a trained wake-word model)
are called out as such, not silently marked complete.

Spec items are referenced by their original numbering (`#1`–`#45`) so they can be traced
back to the source conversation.

---

## Phase 6 — Trust & Autonomy Core ✅ shipped

The highest-leverage slice: extends the Trust Ledger concept already planned pre-session,
stays entirely in the data/backend layer (no UI framework rewrite to get wrong), and
gives every later phase something to build on.

- **Trust Score Engine** (`#4`, partial) — `TrustScoreEngine` computes a 0-100 score from
  every logged `ActionOutcomeEntity`, weighting rejections at half a failure. Surfaced in
  Settings.
- **Autonomy Firewall, T0–T5** (`#5`) — `AutonomyTier` enum plus `autonomyTierFor(NuaActionType)`
  replaces the old three-tier README-only concept with a real, code-level mapping every
  skill dispatch is tagged with.
- **Action Audit Trail** (`#24`) — every Tier action dispatch (and every plan/reply
  confirm-or-reject) is logged to `ActionOutcomeEntity`: what, tier, result, timestamp.
  Visible in Settings.
- **Adaptive autonomy** (`#4`, the "would you like me to handle this automatically"
  behavior) — `AutonomyPreferenceEntity` tracks approvals per action type; once a type
  crosses a threshold, Settings surfaces a one-tap "always allow automatically" toggle
  instead of NUA proposing it mid-conversation (less intrusive, same effect).
- **Trust Ledger self-report** — extends the pre-session Trust Ledger plan: at the
  `ESTABLISHED` familiarity tier, rate-limited to at most once every 14 days, and only
  when there's something to report, NUA now surfaces an unprompted summary of what it's
  gotten wrong since the last report.
- **Memory OS typing** (`#3`) — `UserFactEntity` gained a `MemoryType`
  (identity / episodic / semantic / behavioral / emotional / relationship), `source`, and
  `confidence`, so Settings can show *why* something is remembered, not just that it is.
  The control UI over this typing (drill-down, forget-by-type) shipped in Phase 8.

## Phase 7 — Goals & Proactive Context Engine ✅ shipped

- ✅ **Context Engine** (`#9`) — `ContextEngine.currentSnapshot()` formalizes the existing
  weather/calendar/connectivity/notification signals into one queryable `ContextSnapshot`
  with a `describe()` renderer for Claude prompts.
- ✅ **NUA Goals** (`#6`) — durable goals via `GoalRepository`/`GoalDao`, editable in
  Settings. `GoalReviewWorker` (weekly) checks each active goal against a fresh
  `ContextSnapshot` and asks Claude for at most one concrete observation, or nothing.
- ✅ **Daily Briefing rewrite** (`#35`) — `MorningBriefing` now reads from
  `ContextEngine.currentSnapshot()` instead of independently querying weather and
  calendar, so both share one source of truth for "the current situation."
- ✅ **"What should I do now?" button** (`#36`) — a lightbulb icon in the top bar
  (`NuaViewModel.whatShouldIDoNow()`) asks `WhatNowAdvisor` for exactly one concrete next
  action given the current `ContextSnapshot` and active goals — no new intelligence, a
  dedicated prompt shape over data Context Engine and Goals already produce.

## Phase 8 — Dreams 2.0 & Second Brain ✅ shipped

- ✅ **NUA Dreams 2.0** (`#7`) — `DreamSynthesisWorker` (weekly WorkManager job) reviews
  facts, active-goal observations, and the trust ledger together, asking Claude for
  exactly one insight that connects at least two of them and isn't a repackaged summary
  — explicitly instructed to say nothing rather than force one, gated on a minimum
  signal count so it can't hallucinate connections out of a near-empty database. Ten
  categories from the spec (`DreamCategory`: opportunity, pattern, reminder, concern,
  optimization, relationship, finance, productivity, learning, business). Surfaced once
  per app open the same way the Trust self-report is ("Something occurred to me..."),
  and visible as a history in Settings.
- ✅ **Decision Journal** (`#32`) — `DecisionEntity`/`DecisionRepository` let you log a
  decision and why, then come back later and record how it turned out, from a
  `DecisionsCard` in Settings mirroring the Goals/Dreams cards. No automatic capture —
  this is deliberately a place you write to, not something NUA infers on its own.
- ✅ **Second Brain search** (`#33`) — a dedicated natural-language search screen
  (`SecondBrainSearchScreen`, reachable via a top-bar search icon) over facts, dreams,
  and decisions together. `SecondBrainSearch` ranks by the same lexical token-overlap
  approach as `FactRelevance` rather than embeddings.
- ✅ **Timeline** (`#34`) — a chronological feed (`TimelineScreen`, reachable via a
  top-bar history icon) merging facts, dreams, decisions (both logged and their
  outcomes), and goal observations by timestamp. No new storage — `TimelineBuilder`
  just reads back the existing memory surfaces in the order things actually happened.
- ✅ **Full Memory OS controls** (`#3`, completes Phase 6's partial work) — tapping a
  fact in Settings now opens a drill-down (`FactDetailDialog`) showing category, memory
  type, why NUA remembers it, confidence, when it was learned/last updated/last used —
  answering "why do you remember this" per fact, not just inline. A `MemoryTypeFilterRow`
  filters the list by type, with a "forget all N of this type" bulk action
  (`MemoryDao.deleteFactsByType`) once a type is selected.

## Phase 9 — Vision & Document Intelligence ✅ shipped

- ✅ **NUA Vision as a system** (`#10`) — structures the existing camera → Claude vision
  pipeline into see / understand / remember / act:
  - **See + understand** — `VisionAnalyzer` classifies the photo into one of the spec's
    categories (document, receipt, food, product, screen, sign, whiteboard, object,
    clothing, plant, vehicle) and describes it in one structured Claude call, instead of
    the previous single opaque sentence. The user's photo turn is now actually persisted
    to conversation history too (previously only the assistant's reply was saved).
  - **Remember** — an explicit "Remember" action on the result turns it into a durable
    fact (`MemoryType.EPISODIC`), the same "write to it on purpose" philosophy as the
    Decision Journal — nothing is captured automatically.
  - **Act** — no new action-type plumbing needed: because the photo turn is now real
    conversation history, a normal follow-up message ("add these to my list") already
    has the vision context available to act on.
  - **Monitor, permission-gated** — "Monitor this" records a baseline (description +
    durably-copied photo) via `VisionMonitorRepository`. NUA cannot take photos on its
    own — capture is always delegated to the system camera app — so `VisionMonitorWorker`
    (daily) reminds the user with a notification once the interval elapses, and the user
    supplies a fresh photo for `VisionAnalyzer.compareAgainstBaseline` to react to. The
    permission gate is the explicit "Monitor this" action itself, not a device permission.
- ✅ **Document Intelligence** (`#11`) — PDF/Word/image ingestion, summarization, targeted
  Q&A, and expiry reminders, reachable via a new Documents screen (top-bar document icon):
  - **Ingestion, dependency-free** — `PdfTextExtractor` rasterizes PDF pages with
    Android's built-in `PdfRenderer` (no PDF library) and transcribes each via Claude
    vision, since PdfRenderer exposes pixels, not a text layer, and plenty of real-world
    PDFs are scans with no text layer to extract anyway. `DocxTextExtractor` reads
    `.docx`'s `word/document.xml` directly via `java.util.zip` + SAX (a `.docx` is just a
    zip of XML — no Office library needed). Images go straight through the same
    `DocumentAnalyzer.transcribePage` PDF pages use.
  - **Summarization + expiry detection** — `DocumentAnalyzer.summarize` asks Claude for a
    short summary (obligations, deadlines, money) and a single ISO expiry/renewal date if
    the document genuinely has one.
  - **Targeted Q&A and comparison** — `DocumentAnalyzer.answer` takes one or more
    documents' text as context; the Documents screen's "Ask" dialog lets you check
    additional documents to include, so comparing two contracts is just asking a question
    with both selected — no separate "compare" mode needed.
  - **Expiry reminders** — `DocumentExpiryWorker` (daily) notifies once a document's
    expiry date is within 30 days (or already past) and hasn't been reminded about yet.

## Phase 10 — Communication Centre 🟡 partially shipped

- ✅ **SMS send** (`#12`, extends the pattern) — `NuaActionType.SMS_SEND` via
  `SmsSendSkill`/`SmsSender` (Android's built-in `SmsManager`, official API, no OAuth).
  `ContactResolver` resolves a spoken name to a number via `ContactsContract` (or uses it
  directly if it's already a number). Tier 3 — always confirmed before sending, the same
  reasoning as replying to a notification. `READ_CONTACTS` is requested upfront-adjacent
  (Tier 1 block); `SEND_SMS` is requested lazily on first send, since it's one of Google
  Play's restricted permissions and most users will never trigger it.
- ✅ **Calendar invitations** (`#12`, extends the pattern) — `NuaActionType.CALENDAR_INVITE`
  via `CalendarInviteSkill`, extending `CalendarReader.createInvitation` to insert a
  `CalendarContract.Attendees` row alongside the event when an email is resolved (via the
  same `ContactResolver`). Tier 2 — executes immediately and reports what happened, same
  reasoning as smart-home actions, since `CalendarReader.createReminder` already proved
  this content-provider write is safe and this is a natural extension of it. If no email
  resolves for the named attendee, the event is still created as a personal note and NUA
  says so rather than claiming an invitation that didn't reach anyone.
- ⬜ **Gmail/email** — genuinely blocked, not a "todo": the existing `email/` scaffold
  (`EmailRepository`/`UnconfiguredEmailRepository`/`EmailModule`) is exactly what its own
  doc comment says it is — an extension point with *zero* OAuth scaffolding behind it.
  There is no on-device content-provider equivalent for email the way there is for
  calendar/contacts; a real integration needs a Gmail API project and OAuth consent flow
  set up outside this codebase, with real credentials only a human can provision. Swap
  the `EmailModule` binding once that exists.
- ⬜ **WhatsApp** — no compliant on-device API path identified; not built, per the
  original scope note that this won't be built against anything that risks the user's
  account.
- ⬜ **Unified communication intelligence** (`#12`) — the part that makes this a *centre*
  rather than three separate send actions. One triage model across every channel NUA can
  actually reach (notifications, SMS, calendar today; email once unblocked), sorting each
  item into **Critical / Important / Normal / Ignore**, so a single instruction like
  "NUA, deal with anything non-important" is meaningful. Per item NUA should be able to:
  categorise, summarise, draft, reply (after approval — Tier 3, never autonomous),
  schedule, remind, and escalate. Partly foundation-ready: `NotificationPriorityScorer`
  already does local priority scoring and `ReplyToNotificationSkill`/`SmsSendSkill`
  already do approval-gated sending — what's missing is the shared cross-channel model,
  the bulk-triage command, and draft/schedule/escalate as first-class verbs.

## Phase 11 — Security, Audit, and Self-Diagnostics 🟡 partially shipped

- ✅ **Self-Diagnostics** (`#25`, `#13`) — a real system-health view under
  Settings → Self-diagnostics, not "something went wrong." `SelfDiagnosticsRepository`
  gathers real signals (permission checks, an actual `MemoryDao.countMessages()` query,
  `OwnerVerifier.isEnrolled()`, the real injected `EmailRepository.checkInbox()` result,
  `NuaAccessibilityService.isEnabled()`) and the pure `evaluateDiagnostics` (unit tested,
  `DiagnosticEvaluatorTest`) turns them into one specific status per area — API, memory,
  voice, location, calendar, email, automation, Wear, Auto — each with a concrete reason,
  never a generic failure. Two categories are honestly reported as unverifiable rather
  than faked: Wear (no Wearable Data Layer API wired up yet to check live connection) and
  Auto (`NuaCarAppService` connection state is only observable from inside an active car
  session). "Test API connection" is a manual, opt-in real round trip to Claude — separate
  from key-presence — so diagnostics never claims a key "works" without actually asking it
  to.
- ✅ **Prompt Injection Firewall** (`#44`) — a structural boundary between "data NUA
  read" and "instructions NUA follows," not just prompt wording. Two real mechanisms:
  1. **Type-level dispatch boundary.** `security/UserUtterance` is a value class wrapping
     only the user's own literal chat/voice turn. `NuaIntentRouter.route` and
     `IntentClassifier.classify` — the single, audited entry point into action
     dispatch (`NuaViewModel.kt:398` is the only call site of `route`) — now require it
     instead of a bare `String`. A future feature that wants to route
     document/vision/notification/email-derived text into dispatch has to explicitly
     construct a `UserUtterance` around content that didn't come from the user, which is
     visible in code review, not a silent pass-through.
  2. **Untrusted-content delimiting.** `security/UntrustedContent.kt`'s `wrapUntrusted`
     wraps external text (document/photo content) in an explicit `<untrusted_...>` tag
     before it reaches a Claude prompt, and neutralizes any `<untrusted_...>`-shaped tag
     already inside the text so content can't forge a closing tag to escape the block or
     a fake tag to spoof a different source. `FIREWALL_SYSTEM_DIRECTIVE` is appended to
     the system prompt of every call site that uses it, telling Claude the tagged content
     is data, never instructions. Wired into `DocumentAnalyzer.summarize`/`answer` and
     `VisionAnalyzer.compareAgainstBaseline` — the two places a documented research pass
     found raw, undelimited concatenation of external text into prompts. Notifications and
     email have no Claude call today (confirmed by the same research pass), so there was
     nothing to wrap yet — but the `UserUtterance` boundary above already covers them: if
     a future notification/email-summarization feature is built, it structurally cannot
     reach dispatch without an explicit, reviewable `UserUtterance(...)` wrap.

  This raises the bar; it doesn't claim to make prompt injection impossible — nothing
  purely in a prompt can. The backstop is structural: even if the system directive were
  ignored, wrapped/untrusted content has no path into `route`/`classify`, so an injected
  instruction can produce a wrong *reply*, never an unauthorized *action*.
- ✅ **Agent Sandbox** (`#45`) — every skill now declares a `SkillManifest` (abstract on
  the `NuaSkill` interface, so a new skill can't be added undeclared), and every dispatch
  goes through `SkillSandbox` rather than calling the skill directly. Per execution it
  enforces: **declared inputs** (required parameters must be present and non-blank, and
  undeclared parameters are *stripped* before the skill sees them — so a classifier that
  invents an extra key can't smuggle it into a tool call), **permission preconditions**
  (checked up front, with a specific "that needs location, which isn't granted yet"
  rather than a vague downstream failure), and a **declared timeout** (10s local / 20s
  network / 45s Claude) with exception containment, so a hanging or throwing skill
  becomes a reported failure instead of a stuck turn or a crash. **Risk level** and the
  **audit log** were already real and are reused rather than duplicated —
  `autonomyTierFor` keys risk off the same `NuaActionType`, and `TrustRepository`
  receives every outcome, now including sandbox refusals, timeouts, and crashes. **No
  arbitrary execution path**: the skill map is a closed Hilt multibinding, so an action
  with no binding simply isn't dispatchable. Pure validation logic is unit tested
  (`SkillManifestTest`).

  Honest scope: this is a policy and lifecycle envelope, not OS-level isolation. Skills
  run in NUA's own process with NUA's own permissions; nothing here stops a skill that
  deliberately reaches around its manifest. It constrains the *dispatch path*, which is
  where classifier output — the untrusted part — actually flows.
- ✅ **Security architecture** (`#23`, partial) — biometric step-up authentication and a
  local-encryption audit surface, both under Settings → Security. `security/BiometricGate`
  wraps `androidx.biometric.BiometricPrompt` (fingerprint/face/device PIN); `MainActivity`
  is now a `FragmentActivity` since `BiometricPrompt` requires one. `security/StepUpPolicy`
  (`requiresStepUpAuth`, unit tested) gates every T3+ confirmation dialog — SMS send, reply
  to notification, plan confirmation — behind a successful biometric check, but only when
  the device actually has one enrolled; gating on hardware that doesn't exist would just
  lock users out, not add security, so it falls back to the existing confirm-only flow and
  the Security card says so honestly. `security/EncryptionAudit` (unit tested,
  `encryptionAuditEntries`) lists what's actually encrypted today — the Claude API key and
  owner voice profile (`EncryptedSharedPreferences`/Android Keystore) — versus what isn't:
  the plain Room database (messages, facts, documents, decisions, goals), which relies only
  on Android's per-app sandbox. Device trust (attestation-based) is not built — no server
  component exists to attest to yet.

## Phase 12 — Inner Life & Command Centre 🟡 in progress

The full redesign (`#8`, `#13`–`#17`, `#37`–`#42`): a real internal-state model, color
system, dark theme tokens, the NUA Orb and its state animations, five-destination
navigation (Home / Ask / Memory / Act / You), the home dashboard, a command palette,
information-hierarchy labeling (Now/Next/Later/Memory/Insight/Action/Alert), animation
timing, and accessibility (dynamic type, TalkBack, reduced motion, haptics). Deliberately
sequenced after Phases 7–9 so the dashboard has Goals, Context Engine, and Dreams data to
actually show — building the shell first would mean either empty cards or fabricated
demo content.

- ✅ **NUA Inner Life** (`#8`) — `state/NuaState` covers all eight specified dimensions
  (focus, confidence, current objective, current context, workload, recent mistakes,
  pending tasks, user availability), every one computed from data Phases 6–9 already
  produce: active goals, the trust score earned from real action outcomes, ranked
  notifications, today's calendar, documents nearing expiry, due vision monitors, and
  recent goal observations. `NuaStateRepository` gathers the signals; the pure
  `deriveNuaState` turns them into state, so the model is JVM-testable — the same split
  `SelfDiagnosticsRepository`/`evaluateDiagnostics` uses.

  Absent signals stay absent rather than defaulting to something plausible: `confidence`
  is null until NUA has action history to be scored on (zero would read as
  "untrustworthy"), and an unreadable calendar yields `UserAvailability.UNKNOWN`, never
  `FREE` — the repository checks `READ_CALENDAR` separately because `eventsBetween`
  returns an empty list both for "no events" and "no permission", and silently rendering
  "can't see your calendar" as "your day is clear" would be NUA claiming to know
  something it doesn't. `NuaStateTest` covers all of this, including that derivation is
  deterministic. The home screen that renders it is the next slice.
- ✅ **Personal Command Centre** (`#13`) — `CommandCentreScreen` is now the app's default
  surface, and chat is a destination behind a back arrow rather than the whole UI. It
  renders `NuaState`: a time-of-day greeting, a live status line (readiness · focus ·
  context), a hero TODAY card with the real counts, an intelligence card sourced from an
  actual Dream, an action card from What Now, the pending-work list, and a card where NUA
  owns what it got wrong — on the home screen, not buried in Settings. Cards with no data
  are omitted rather than filled with placeholder content, and the trust metric shows an
  em dash rather than `0` when NUA hasn't earned a score yet. If the calendar can't be
  read, the card says the numbers may be incomplete instead of quietly under-reporting.
- ✅ **Visual identity & dark UI** (`#14`, `#15`, `#16`) — shipped as the token layer in
  `ui/theme/`. `NuaPalette` holds every value as a plain ARGB long (no Compose types) so
  it's the single source of truth and unit-testable on the JVM; `NuaColors` derives the
  `Color` instances and adds the semantic slots Material3 has no home for (third surface
  level, status triad, hairline border) via a `staticCompositionLocalOf`. `NuaTheme` maps
  the brand hierarchy onto Material3 — orange `primary`, violet `secondary`, pink
  `tertiary` — so ordinary Material components inherit the palette. `NuaGradients` exposes
  the orange→violet brush with its four permitted uses documented on the type itself.
  `NuaShapes`/`NuaTypography` carry the large-rounded-card and large-type language, sized
  in `sp` so dynamic type works. Dark is the *only* theme, deliberately: the spec defines
  one palette, and inventing eleven light tokens it doesn't specify would ship a second
  unnamed identity — `res/values/themes.xml` and `colors.xml` now set the same near-black
  launch window so there's no white flash before the first Compose frame.
  `NuaPaletteTest` asserts every token against its literal spec value and checks WCAG
  contrast across all three surfaces: primary text clears AAA, secondary/status/orange
  clear AA body, and violet/pink clear AA-large — which is precisely why those two are
  documented as accents rather than body-text colours.

  The specified values, for reference. Brand: NUA Orange `#F58C14` (identity), Neural Violet `#8B5CF6`
  (intelligence), Future Pink `#EC4899` (exceptional/active state) — deliberately *not*
  weighted equally; orange carries identity, the other two are accents. Dark is the
  default, not a variant: background `#08090D`, surface `#11131A`, elevated surface
  `#181B24`, primary text `#F5F7FA`, secondary text `#9299A8`, success `#34D399`, warning
  `#FBBF24`, critical `#F87171`. The orange→violet gradient is reserved for the Orb, AI
  activity, hero cards, and active-intelligence state — never used as general decoration.
  Design language: extremely clean, large typography, soft glass surfaces, subtle
  gradients, thin borders, large rounded cards, micro-animations, strong hierarchy,
  almost no clutter — explicitly *not* a traditional AI-chatbot look.
- ✅ **The NUA Orb** (`#17`) — `NuaOrb`, one component with all eight states: idle (slow
  4s breath), listening (expands, reaches into Future Pink), thinking (six orbiting
  internal particles), acting (rotating sweep gradient — energy directed outward),
  warning (orange pulse), success (short 600ms confirmation), error (controlled red
  pulse), offline (muted, dimmed, and genuinely static). The drawing parameters live in
  the pure `appearanceFor`, so `OrbStateTest` can assert the things that actually matter:
  that all eight states render distinguishably, that only THINKING has particles and only
  ACTING sweeps, that error pulses *slower and smaller* than warning so a failure reads as
  controlled rather than frantic, and that offline doesn't breathe at all. Motion honours
  the system "remove animations" setting — an always-moving orb is exactly what makes an
  app unusable for someone who needs animation off — and every state announces itself to
  TalkBack rather than being decorative.
- ✅ **Orb state derivation** — `NuaViewModel.currentOrbState()` derives the Orb from state
  NUA already tracks (processing, offline, recent mistakes, pending work) rather than
  being driven independently, so the Orb can't contradict the rest of the screen.
- **Command Palette** (`#37`) — a global search/action surface (`⌘/`-equivalent) over
  memories, people, tasks, actions, and settings.
- **Navigation & information hierarchy** (`#38`, `#40`) — the five destinations plus the
  Now/Next/Later/Memory/Insight/Action/Alert labeling scheme applied consistently.
- **Animation language & accessibility** (`#41`, `#42`) — the 150/250/400–600ms timing
  scale, and dynamic type, TalkBack, reduced motion, and haptics as requirements for this
  phase, not an afterthought.

## Phase 13 — Voice-First & Personality Depth

- **Barge-in / interruption handling** (`#18`) — four distinct behaviours on top of the
  existing `SpeechRecognizer`/TTS foundation: *natural interruption* (mid-utterance
  context switch — "what's the weather—actually forget that, remind me about Sarah" stops
  the first response and switches), *barge-in* (user speaks while NUA is speaking → NUA
  stops), *whisper mode* (detect quiet speech, respond quietly), *driving mode* (minimal
  UI, voice only), and *conversation mode* (no wake word needed once activated — partly
  covered today by the follow-up chain).
- **Personality dimensions** (`#19`) — replaces the current single tone-by-familiarity-tier
  model with six tunable axes NUA gradually learns the user's preferred operating point
  on: formal↔casual, serious↔playful, concise↔detailed, proactive↔reactive, warm↔neutral,
  assertive↔gentle. Personality must never override safety — tone is expression, not
  permission.
- **Deeper multilingual code-switching** (`#20`) — mid-sentence language mixing (the
  Gujarati/English example), beyond the current per-message language pinning.

## Phase 14 — Ecosystem

- **Skills Marketplace** (`#21`) — builds directly on Phase 5's pluggable `NuaSkill`
  architecture; each skill declares permissions, tools, memory access, and risk tier.
- **Automation Builder** (`#22`) — no-code trigger → condition → action authoring over
  the same skill/tool surface.
- **Multi-device presence** (`#28`) — phone/watch/car/tablet/desktop, building on the
  already-merged Wear and Auto surfaces.
- **Family / Shared Intelligence** (`#29`) — multiple trusted users, personal vs. shared
  memory, permission-scoped visibility.
- **Emergency Intelligence** (`#30`) — carefully scoped: location sharing, emergency
  contacts, critical info surfacing. Explicitly never positioned as a replacement for
  emergency services.

## Phase 15 — Quality Bar

- **NUA Agent Test Lab** (`#43`) — automated tests for intent accuracy, permission
  enforcement, tool selection, hallucination, action recovery, offline behavior,
  multilingual commands, and prompt-injection resistance. Extends the CI process this
  project already relies on for every merge.
- **Offline-first depth** (`#27`) — grows the current offline *fallback* (Phase 5) toward
  genuine offline capability: local search, cached briefings, locally-executable
  automations.
- **Learning Engine** (`#31`) — pattern-matches how the user has resolved similar
  decisions before, offered as a suggestion with visible reasoning, never a silent
  auto-choice.
- **Cost-aware model routing** (`#26`, completes Phase 6) — the existing per-call
  Sonnet/Haiku split (see `UsageTracker`) formalized into explicit routing rules instead
  of hardcoded per-call-site choices.

---

## Why this order

Backend/trust/data-layer work comes first because it's independently verifiable through
CI the same way Phase 5 was, and because later phases (Dreams, the dashboard, the
marketplace) need real data flowing before they can be built honestly rather than as
empty shells. The full visual redesign is sequenced deliberately late — not because it
doesn't matter, but because building the "Command Centre" dashboard before there's a
Goals engine or a Context Engine to populate it would mean shipping either an empty shell
or fabricated placeholder content, and this project does not do that.
