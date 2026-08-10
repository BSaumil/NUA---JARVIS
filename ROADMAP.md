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
- **Memory OS typing, partial** (`#3`) — `UserFactEntity` gained a `MemoryType`
  (identity / episodic / semantic / behavioral / emotional / relationship), `source`, and
  `confidence`, so Settings can show *why* something is remembered, not just that it is.
  Full memory-control UI (per-fact "why do you remember this" drill-down, forget-by-type)
  is Phase 8.

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

## Phase 8 — Dreams 2.0 & Second Brain

- **NUA Dreams 2.0** (`#7`) — the pre-session Dreams plan (one weekly, rate-limited,
  non-repackaged insight), extended with the categories from the spec (opportunity,
  pattern, concern, optimisation, relationship, finance, productivity...).
- **Decision Journal** (`#32`) — optional structured record (options considered, reasoning,
  choice, outcome) for decisions the user chooses to log, feeding both Dreams and Trust.
- **Second Brain search** (`#33`) — natural-language search over facts, summaries, and the
  decision journal ("what did I decide about the cafe?").
- **Timeline** (`#34`) — a chronological view over logged events/messages/actions; mostly
  a UI layer over data Phases 6–8 already produce.
- **Full Memory OS controls** (`#3`, completes Phase 6's partial work) — per-fact
  "remembered → why → source → confidence → last used → forget" inspector.

## Phase 9 — Vision & Document Intelligence

- **NUA Vision as a system** (`#10`) — structures the existing camera → Claude vision
  pipeline into see / understand / remember / act, with an explicit permission gate for
  "monitor" (recurring vision checks).
- **Document Intelligence** (`#11`) — PDF/Word/image ingestion, summarization, targeted
  Q&A ("what am I obligated to do"), and expiry reminders.

## Phase 10 — Communication Centre

- Turns the existing email scaffold (`#12`, already structurally real but blocked on
  external Gmail OAuth) into a real, provisioned integration, then extends the same
  pattern to SMS and calendar invitations. WhatsApp only if a compliant API path exists —
  won't be built against anything that risks the user's account.

## Phase 11 — Security, Audit, and Self-Diagnostics

- **Prompt Injection Firewall** (`#44`) — a hard boundary between "data NUA read" and
  "instructions NUA follows," enforced before any tool call, not just prompted for.
  Becomes non-optional once Phase 10 gives NUA an inbox to read.
- **Agent Sandbox** (`#45`) — every tool gets a declared input/output schema, permission,
  risk level, timeout, and audit log; no arbitrary tool execution path.
- **Security architecture** (`#23`) — local encryption audit, device trust, and
  step-up authentication (biometric) for the highest-risk actions.
- **Self-Diagnostics** (`#25`) — a real system-health view (API, memory, voice, location,
  calendar, email, automation, Wear, Auto) with specific failure reasons, not "something
  went wrong."

## Phase 12 — Visual Identity & Command Centre

The full redesign (`#14`–`#17`, `#38`–`#42`): color system, dark theme tokens, the NUA
Orb and its state animations, five-destination navigation (Home / Ask / Memory / Act /
You), the home dashboard, information-hierarchy labeling (Now/Next/Later/Memory/Insight/
Action/Alert), animation timing, and accessibility (dynamic type, TalkBack, reduced
motion, haptics). Deliberately sequenced after Phases 7–9 so the dashboard has Goals,
Context Engine, and Dreams data to actually show — building the shell first would mean
either empty cards or fabricated demo content.

## Phase 13 — Voice-First & Personality Depth

- **Barge-in / interruption handling** (`#18`) — user can interrupt NUA mid-reply and
  redirect; whisper mode; driving mode.
- **Personality dimensions** (`#19`) — replaces the current single tone-by-familiarity-tier
  model with tunable dimensions (formal↔casual, concise↔detailed, etc.) that NUA learns
  toward, with safety always overriding tone.
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
