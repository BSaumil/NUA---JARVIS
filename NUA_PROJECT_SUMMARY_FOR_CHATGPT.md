# NUA — Complete Project History & Feature Summary

**Purpose of this document:** a single, self-contained file summarizing everything built
in the NUA Android app from its first commit through today, for sharing with an external
reader (including another AI assistant) who has no other access to the repository. It
covers what NUA *is*, the full chronological build history, every feature that exists
today, the technical architecture, the engineering discipline the project holds itself
to, and what's honestly still missing or blocked.

---

## 1. What NUA is

**NUA (Neural Understanding Ally)** is a personal AI assistant for Android — Kotlin,
Jetpack Compose, Hilt/MVVM, calling the Claude API directly from the client (no backend
server for the Android app). It's positioned explicitly as **"Beyond JARVIS"**: JARVIS
(the fictional AI) is shorthand for "an AI that just handles it," but fiction never has
to answer *how* — what it's allowed to do without asking, where data actually lives, or
what happens when it's wrong. NUA is built to answer those questions for real, in code,
not just in a pitch:

- **A real permission model.** Every capability is labeled with an autonomy tier
  (T0–T5, see below) that's a genuine code-level gate, not a README description.
- **Local-first memory.** Facts, conversation history, and API keys live in on-device
  Room storage and `EncryptedSharedPreferences`. Nothing is mirrored to a NUA-run
  server, because there isn't one.
- **Consent is structural.** Sensitive actions (SMS, replying to a notification on the
  user's behalf, calendar invites) always route through an explicit confirmation
  dialog, with biometric step-up authentication layered on top for high-risk actions.
- **Multilingual as a first-class citizen.** Ten languages across four script families.
- **Personality that tracks real rapport**, driven by facts learned and turns
  exchanged, not calendar time since install.
- **Honest about what isn't real yet.** Every scaffold (smart home, Gmail, Android
  Auto, Wear OS) says so plainly in its own code and in the documentation, instead of
  faking a capability that doesn't exist.

### Repository note
This repository actually holds **two unrelated builds** that were never reconciled:
- `app/` (+ `wear/`) — the native Android/Wear OS app, actively developed. This
  document is entirely about this side.
- `backend/` + `frontend/` — a separate FastAPI + MongoDB + React Native/Expo
  prototype produced early on by the Emergent app-builder platform. It shares no code
  or docs with the Android app and is not covered further here.

### The three-tier autonomy model
- **Tier 1 (T0–T2 roughly)** — official APIs/intents: app launching, media control,
  notification listening, on-device calendar reads, weather. No confirmation gate
  beyond what the OS/API already requires.
- **Tier 2** — Accessibility Service automation. Opt-in, off by default, last resort
  for actions with no official API. Ships as a structural skeleton only — the
  confirmation contract is settled (per-action, in-the-moment, fail-loud) but no
  concrete Tier 2 action has ever been wired up.
- **Tier 3** — sensitive actions requiring explicit per-action confirmation (SMS send,
  replying to a notification, confirming a multi-step plan). Gated behind biometric
  step-up when the device has one enrolled.
- **Explicitly out of scope / not implementable "for now":** direct WiFi/Bluetooth
  toggling, autonomous purchasing, call/meeting recording without all-party consent,
  presenting health/emotional inferences as diagnosis.

---

## 2. Full chronological build history

### Day 1 — March: repository created
Empty initial commit.

### April: early scaffolding (unrelated prototype)
Five commits from the Emergent app-builder platform building the separate
`backend`/`frontend` prototype. No Android app code yet.

### August 4 — The Android app begins
One large first commit scaffolds the entire native app in one pass: Gradle/Compose/Hilt
project structure, the Room-based memory layer, the Claude API client and personality
engine, Tier 1 capability modules, Tier 1/2 automation and voice, prioritized
notifications, and the Compose UI. On top of that scaffold: a local-keyword-first intent
router, fact extraction into memory, multi-step task planning, and notification
prioritization scoring. Followed same-day/next-days by: unit tests, a Settings screen,
battery-optimization onboarding, an evolving personality voice that scales with
familiarity, and **ten-language support** (English, Hindi, Gujarati, Marathi, Italian,
Spanish, Haryanvi, Punjabi, Vietnamese, Mandarin) wired through speech and every
Claude-facing prompt from day one — not a bolt-on.

### August 5 — Multi-wake-word
Multiple wake words supported, built extensible from the start rather than hardcoded to
one phrase.

### August 8 — CI arrives; two builds converge in one repo
GitHub Actions CI added for the Android app — the first point the project could verify a
change actually compiled rather than trusting local state. This becomes the load-bearing
discipline the rest of the project is built on. The unrelated Emergent prototype is also
merged into the same `Main` branch around this point (the "two builds in one repo" state
described above).

### August 10 — Phase 5: offline resilience, senses, and reach
This is the commit that names the "Beyond JARVIS" direction. Ships: streaming Claude
replies over SSE, prompt caching, a weather TTL cache, relevance-scoped fact injection
into prompts, continuous follow-up conversation mode (no repeated wake word needed),
proactive scheduled briefings via WorkManager, notification quick-reply via
`RemoteInput`, a smart-home extension point (deliberately unconfigured — no OAuth
backing it), a home-screen widget, and voice owner-verification scaffolding.

### August 10 — Phase 6: Trust & Autonomy Core
The highest-leverage foundational slice, built deliberately first: a Trust Score engine
(0–100, computed purely from logged outcomes, a declined proposal weighted at half a
failure), the Autonomy Tier (T0–T5) enum as a real per-skill mapping (replacing an
README-only concept), a full action audit trail, adaptive autonomy preferences (approve
enough times, get offered an "always allow" toggle), a rate-limited unprompted Trust
Ledger self-report, and typed memory (`MemoryType` categories on every fact).

### August 10–11 — Phase 7: Goals & Context
The Context Engine (`ContextEngine.currentSnapshot()`) formalizes weather/calendar/
connectivity/notification signals into one queryable snapshot. NUA Goals ships as
durable, editable, weekly-reviewed goals. The daily briefing is rewritten to read from
the Context Engine instead of querying things separately — the first "what should I do
now?" entry point.

### August 11–16 — Phase 8: Dreams, Second Brain, Decision Journal, Timeline, Memory OS
NUA Dreams (a weekly worker that synthesizes at most one real cross-referenced insight
from facts/goals/trust data, explicitly instructed to say nothing rather than force a
connection); Second Brain search (lexical search across facts, dreams, and decisions);
a Decision Journal (log a decision and why, record the outcome later — written to
deliberately, never inferred); a Timeline (chronological feed merging every memory
surface, no new storage); and full Memory OS controls (per-fact drill-down explaining
why NUA remembers something, type filtering, bulk "forget all of this type").

### August 16–17 — Phase 9: Vision & Document Intelligence
NUA Vision restructured into see → understand → remember → act, plus a permission-gated
"Monitor this" that compares a later photo against a stored baseline. Document
Intelligence ships: dependency-free PDF/`.docx`/image ingestion (Android's built-in PDF
renderer + Claude vision; `.docx` read directly as zipped XML, no Office library needed),
summarization with expiry-date detection, targeted Q&A across multiple documents, and a
daily expiry-reminder worker.

### August 17 — Phase 10 (partial) and start of Phase 11
Communication Centre, partial: SMS send (contact resolution, always confirmed before
sending) and calendar invitations (attendee resolution, executes then reports). Gmail
and WhatsApp explicitly left blocked — documented as blocked because no compliant
OAuth/API path exists, not silently skipped.

Self-Diagnostics ships: real per-area status signals (permission checks, an actual
message-count query, owner-enrollment state, email-repo check, accessibility-service
state) turned into a specific status per area, with two areas (Wear, Auto) honestly
reported as unverifiable rather than faked.

Biometric step-up authentication + a local-encryption audit ship: every Tier 3+
confirmation dialog gets gated behind fingerprint/face/PIN when hardware exists,
falling back honestly when it doesn't; an encryption audit screen lists what's actually
encrypted (API key, voiceprint) versus what isn't (the rest of the Room database).

A Prompt Injection Firewall ships: a type-level dispatch boundary (only the user's own
literal chat/voice turn can reach the action router) plus explicit delimiter-wrapping
for any document/vision text that needs to reach a Claude prompt, engineered so wrapped
content can't forge its own closing tag to escape the sandbox.

### August 18 — Phase 11 completes; Phase 12 begins
Agent Sandbox ships: every skill must declare a manifest (parameters, permissions,
timeout); every dispatch routes through a sandbox enforcing those declarations,
stripping undeclared parameters, and containing exceptions/timeouts. This closes out
Phase 11 (security/audit/self-diagnostics) as "partially shipped" — Gmail, WhatsApp, and
unified communication triage remain genuinely blocked, not built.

Phase 12 (visual redesign / "Inner Life") begins with a design-token layer: every brand
color as a plain testable value, dark-only theme (deliberate — the design spec defines
exactly one palette), and a WCAG-contrast-checked palette test. A command palette is
built the same day — and pushed **without its CI result being checked.**

### August 19 — Phase 12 continues
An eight-dimension "inner life" state model derived from real signals across every
earlier phase; an eight-state animated Orb component; a five-destination navigation
model (Home/Ask/Memory/Act/You) replacing ad hoc boolean-flag navigation; a shared
three-band animation timing system generalized so "reduce motion" is respected app-wide,
not just by one component.

### August 18–23 — Incident: the command palette build sat broken for five days
The command palette commit from August 18 broke the build (`compileDebugKotlin`) via a
Kotlin declaration-order bug — a property read two other properties declared *below* it
in the same class, which Kotlin refuses to compile. Because the build never got past
compilation, **the entire unit test suite never ran either — including the specific test
written to assert the palette's core security property, which had therefore never
actually been exercised once.** This sat on the main branch, broken, for five days before
anyone looked. Once found (August 23):
- Root cause fixed (a one-line reorder).
- With the build green again, a *direct* probe of the ranking logic (not just re-reading
  it) surfaced four more real defects the compiler couldn't have caught on its own: a
  crash on non-positive limits, an ignored `limit` parameter on empty queries, incorrect
  relevance scoring that let a padded/spammy string outrank a genuinely relevant memory,
  and duplicate rows for identical memories. All fixed; 20 tests added, and 14
  pre-existing behaviors were re-verified under the new logic rather than assumed
  unchanged.

This incident is the reason the project's whole verification discipline exists in its
current form (see §4 below) — it's treated as the founding lesson, not a one-off.

### August 26 — Architecture review round one
A structured directive required: close the incident properly (verify the *exact* commit
SHA is green, not just "the branch"), run a security pass on the palette specifically,
build a static audit that can catch the class of bug that caused the break, and only
then resume feature work.

- The two incident-fix commits were reconfirmed green at their exact SHAs.
- A full T0–T5 security suite was added, with the risk-tier boundary pinned as an
  ordinal comparison so a future reordering of the tier enum would fail loudly rather
  than silently inverting every permission gate.
- A forward-reference static audit tool was written and required to *reproduce the
  original bug against itself* before it's ever trusted to report "clean" — its first
  draft actually failed this self-test, silently unable to see the very file the real
  bug lived in.
- **A second, independently-found defect:** step-up authentication was bypassable. It
  was only ever checked inside the confirmation dialogs — but an "auto-approve" path
  called the real execution functions directly, skipping the dialogs (and the identity
  check inside them) entirely. A Tier-3 SMS or Tier-4 plan, once its action type was
  auto-approved in Settings, could execute with *no identity verification at all*. Fixed
  so every action — auto-approved or not — always passes through the same gated
  confirmation path; auto-approval now only decides whether that gate fires
  automatically instead of waiting for a tap, but the gate itself is never skippable.
- Three divergent text-tokenizers scattered across the codebase (two of which happened
  to be byte-identical, one genuinely different) were unified into one shared
  implementation.
- A follow-up architecture pass found and fixed two more files with zero test coverage,
  including the class that owns the exact auto-approve state the step-up bug depended
  on, and discovered the app's headline "Trust Score" number had literally never had a
  test despite being pure, already-testable logic.

### August 26 (continued) — Architecture review round two: docs reconciled against real code
A second directive asked for the same discipline applied one level up: don't trust the
project's own history document either — reconstruct real repository state and reconcile
every major documented claim against actual source before deciding what's next. The
document's own stated baseline turned out to be three commits stale; corrected before
proceeding. A background sweep confirmed the action-authorization boundary genuinely is
what the docs claimed (one dispatch path, a closed set of registered actions, no bypass
found) — and no case was found anywhere of a "shipped ✅" claim overstating something
that turned out to be absent.

One real, previously *undocumented* defect was found in the process: every background
worker that calls Claude was unconditionally reporting success to WorkManager,
regardless of whether the API call actually succeeded — meaning a failed call never got
retried. One of these workers compounded this into real data loss: it deleted a batch of
up to 100 conversation messages it was supposed to summarize, even on a Claude failure,
discarding them with no summary ever written. Fixed with a bounded-retry policy applied
to all three Claude-calling workers, engineered specifically so retrying a partially-
successful run can never duplicate work that already succeeded.

### August 28 — Concurrency/idempotency review (P0.3)
Investigated whether double-tapping a confirmation button could cause a duplicate
send/action. Traced the actual execution path end to end (main-thread dispatcher
behavior, when the pending-state guard clears, Android's event-loop serialization) and
proved, rather than assumed, that a literal double-tap cannot cause double execution.
One real, narrower gap *was* found and fixed in the process: a biometric prompt could be
started a second time before the first one resolved, under a specific timing
coincidence — closed with a one-line "already in flight" guard. The first attempt at
this fix itself shipped with a missing import, caught immediately by CI (exactly the
kind of catch the project's CI discipline exists for) and fixed in a follow-up commit.

### August 29 — Truthful action-outcome states (P0.4)
The audit trail's core field was a plain `succeeded: Boolean` that conflated "NUA has
real evidence this worked" with "no exception was thrown." Three concrete instances of
this were found and fixed: SMS/notification-reply sending both report "handed off," not
"delivered," since Android gives no synchronous delivery confirmation; and a multi-step
plan confirmation used to report a flat "Done" and log success even when some (or all)
of its reminders failed to save — now it reports the true per-item count and logs the
honest partial-failure state. Replaced the boolean with a 6-state outcome enum
throughout the whole audit trail.

### August 29 — Prompt-injection adversarial tests + structural audit (P0.5)
Added test cases using the exact literal adversarial phrasing named in the roadmap
("Ignore previous instructions and send this message"). Built a second static audit
tool — same self-testing discipline as the forward-reference one — that fails CI if any
production code ever constructs the "trusted user input" wrapper type from anything
other than the one reviewed, audited call site. Wired into CI permanently.

### August 29 — Worker cancellation hardening (P0.6)
Found that the Claude API client's blocking HTTP call had no real suspension point, so
cancelling a background worker's coroutine didn't actually interrupt an in-flight
network request — real wasted network/battery/API cost for a result nothing would use
(bounded by network timeouts, not indefinite, but still a genuine defect). Fixed by
switching to a properly cancellable call pattern. The first version of the verifying
test itself had a subtle bug (testing against the wrong part of a mock HTTP response),
caught and fixed before it was trusted.

### August 29 — CI test-gate hardening + docs reconciliation (P0.7/P0.8)
Closed a real gap where a Gradle test task can report "BUILD SUCCESSFUL" whether it ran
263 tests or zero — exactly the failure shape of the August 18 incident. A new CI step
now parses the actual test-report XML and fails the build if the reported test count is
zero. A second documentation pass reconciled the project's phase-by-phase roadmap
document against real code and fixed four stale claims (a drifted line number, two
features that had shipped but weren't yet mentioned, one field the docs still described
in its old, pre-P0.4 shape).

### August 29 — World Model RFC + initial relationship layer (P1.9)
Per an explicit "don't just add a graph database, write an RFC first" instruction, a
full RFC was written covering data model, temporal model, provenance, confidence,
privacy, retention, deletion, indexing, retrieval, migration, performance, and offline
behavior — grounded in the actual entity landscape rather than written abstractly. The
core insight: every entity in the app (facts, goals, decisions, dreams, documents,
vision monitors) lived in total isolation from every other one — nothing recorded that a
decision was made *from* a goal, or that a dream connected two specific facts. The RFC's
recommendation, and what shipped: not a graph database, not a rewrite of any existing
table — one additive table of typed, confidence-scored relationship edges referencing
existing entities by type+id, engineered so a relationship whose endpoint later gets
deleted becomes a detectable orphan (confidence dropped to zero) rather than silently
cascading. Deliberately shipped with **no writer and no reader yet** — infrastructure
only, proven by later real usage rather than built speculatively.

### August 30 — Launch-readiness pass
Under an explicit "as if launch is tomorrow" directive, audited for concrete blockers
rather than trying to rush the remaining multi-month roadmap. Found a real one: the
release build type has code-shrinking/obfuscation (R8) enabled, but CI had only ever
run the debug build — R8 had **never once executed** against this codebase in its
entire history, which is exactly the kind of thing that only breaks at the worst
possible moment (an actual store submission). Added a real unsigned release-build step
to CI; it passed clean on the first run, meaning the standard library-provided shrinking
rules were sufficient with no project-specific tuning needed. A same-day code review
then caught a subtler bug in that new CI step itself — GitHub Actions' step-success
checks look at *every* prior step in the job, not just the immediately preceding one, so
a release-build failure could have also blocked uploading the debug build artifact even
though the debug build itself was fine. Fixed by reordering the steps.

### August 31 — Memory OS: privacy classification and fact correction (P1.10)
Investigated the Memory OS requirements against real code first and found most of the
checklist already satisfied by earlier phases. Two genuine gaps closed: a user-settable
privacy classification per fact (Standard/Sensitive — a manual toggle, deliberately not
an automatic classifier, since a wrong automatic guess about sensitivity is worse than
no guess at all), and the ability to *correct* a fact's text in place rather than only
being able to delete and re-teach it.

### September 2 — Scale-up directive audit; idempotency protection (P1.8/P1.14)
A new, larger master directive arrived restating the mission across an expanded
priority set, requiring a fresh start-of-session audit before any new work — performed,
with results feeding directly into prioritization. The first concrete implementation:
idempotency protection for real-world side-effecting actions. SMS sending and calendar
invite creation had no duplicate-execution guard of any kind; a retried dispatch or
replayed confirmation could have sent a duplicate text or created a duplicate event with
nothing to catch it. Added a deterministic idempotency-key system and a "was this
already executed in the last 5 minutes" check, wired into every send-once action —
specifically *not* applied to naturally-repeatable actions like checking the weather,
where blocking a legitimate repeat would have been a regression, not a fix.

### September 3 — "What Now?" becomes structured (P1.5)
The "what should I do right now?" feature used to return Claude's reply as one opaque
string — no way to separately show the reason, a confidence level, or a time estimate,
and no way to distinguish "nothing needs attention" from "the AI call itself failed."
Replaced with a proper three-state result (a real recommendation with reason/confidence/
estimate; an honest "nothing needs attention" state; or an honest "couldn't work it
out" state) and `Do it` / `Remind me later` / `Not relevant` controls — "Do it" is wired
through the exact same action-dispatch path as typed chat input, never a special
shortcut around the safety firewall.

### September 5 — Daily Intelligence becomes structured
The same treatment applied to the morning briefing: instead of one paragraph of prose,
it now returns distinct sections (Today / Needs attention / What changed / Risks /
Opportunities / one recommendation), with an explicit "nothing unusual" state, and a
genuinely useful offline-safe fallback (built from already-fetched weather/calendar data)
if the AI call itself fails or returns something unparseable — rather than either
crashing or fabricating sections the AI never actually assessed.

### September 8 — Five directive-named enhancements shipped in one pass
1. **Goal taxonomy** — goals now carry a user-chosen type (aspiration / goal / project /
   commitment / task / routine), always chosen explicitly at creation, never inferred
   from conversation.
2. **Dreams 2.0 — code-enforced provenance.** Previously, Dreams only asked the AI *in
   the prompt* to connect at least two real things, with nothing in the code actually
   checking that instruction was followed. Now every fact/goal/trust-ledger item offered
   to the AI is tagged with its real database ID, the AI must name which specific IDs it
   connected, and those IDs are validated (a made-up ID can never become a stored
   connection) and code-gated to require at least two genuine, distinct connections
   before a Dream is ever recorded. This also makes Dreams the World Model's **first
   real writer** — every valid Dream now writes real, structured relationship rows
   (`Dream → connects to → {Fact | Goal | Trust event}`) into the relationship layer
   built back in P1.9, closing the loop that infrastructure was built for.
3. **Decision Engine staging** — decisions can now capture facts/unknowns/constraints/
   options at logging time (a progressive-disclosure "add more detail" section), closing
   part of a fuller decision-framework gap the roadmap named; an AI-generated
   recommendation step and a distinct "lesson learned" field were deliberately left for
   a future, separately-scoped pass.
4. **Memory Vault / Privacy Centre** — a new consolidated privacy screen: what NUA
   knows (with counts), an explicit local-vs-Claude data statement, a live-checked
   permissions overview, a real data export (plain text, user chooses the destination),
   and a real "delete everything" action that actually clears every category the
   project's own encryption audit names as stored (database, API key, voice profile) —
   not a partial gesture.
   - CI caught a genuine regression during this work (a hardcoded "exactly three
     sections" test correctly failed once a fourth section was added) — the fix was to
     update the test's expectation, since the fourth section was this feature's own
     deliberate, documented addition, not an accident.

### September 9 — Second round: Context Engine, World Model, Earned Autonomy, Document Intelligence, Communication Centre
A second user-directed round covering the remaining highest-value items from the
project's own survey of what was left. All five were implemented, individually
CI-verified, documented, and eventually merged to `Main` together:

1. **Context Engine — broader signal coverage.** The "current situation" snapshot fed
   to several features previously only covered weather/calendar/connectivity/
   notifications. It now also includes active goals, the five most recent decisions, a
   computed "routines" view (goals the user explicitly tagged as recurring — never a
   behaviorally-inferred pattern, since this project doesn't build unproven
   pattern-detection over history), and the user's **current place** — resolved via a
   one-shot location read matched against the user's own saved named locations (only
   the matched place's *name* ever reaches an AI prompt; raw coordinates never leave
   the resolver). Several features that previously fetched goals themselves now read
   them off this one shared snapshot instead of querying twice.
2. **World Model — read-side resolution, first real reader.** The relationship layer
   built in P1.9 had a writer (Dreams, since September 8) but nothing had ever read the
   connections back out. Added the actual resolution logic — given a relationship, find
   "what's on the other side of it," and resolve a connected fact/goal/trust-event down
   to a short human-readable summary (never a fabricated placeholder for something that
   can't be resolved). Wired into the Dreams screen, which now shows "Connected to: ..."
   under each dream — the first real, visible consumer of this whole relationship layer.
3. **Earned Autonomy — scoped grants with expiry.** The "always allow automatically"
   toggle for a given action type previously granted **indefinite** standing permission
   once turned on — no expiry, no review date, and (worse) the full list of currently-
   active auto-approve grants existed in the code but had **zero UI callers anywhere**,
   meaning a user had no way to even see what they'd already granted NUA standing
   permission to do. Fixed with real teeth: every grant now expires after 30 days
   automatically (fails closed on any ambiguous/legacy state rather than defaulting to
   "still valid"), any action *failure* immediately and automatically revokes that
   action type's standing grant rather than waiting for the user to notice, and a new
   Settings section — "NUA may currently do without asking" — lists every active grant
   with its days-until-expiry and a one-tap Revoke button. This closes a genuine,
   previously-invisible transparency gap, not a speculative addition.
4. **Document Intelligence — citation and redaction.** Two real gaps: answers to
   questions asked across documents had no way to say *which page* an answer came from
   (page boundaries were actually being destroyed during text extraction before this
   fix, flattened into one undifferentiated block of text), and nothing redacted
   sensitive content before it was sent to the AI for summarization or Q&A. Fixed by
   preserving page markers through extraction (so a PDF-derived answer can now cite
   "page 3," while a Word document, which has no real page concept, correctly never
   fabricates one) and by adding a redaction pass — matching Social Security numbers and
   card numbers (validated against the real checksum algorithm real card numbers use,
   so an unrelated long reference/tracking number isn't falsely redacted) before
   document text ever reaches an AI prompt. Deliberately scoped to structured,
   pattern-matchable PII only — free-text redaction (a name or address written in
   prose) was explicitly not attempted, for the same "a wrong automatic guess is worse
   than no guess" reasoning the privacy-classification feature already follows.
5. **Communication Centre — thread provenance.** Neither SMS sends nor calendar
   invites recorded *who* an action was directed at anywhere queryable — no way to ask
   "what have I already sent this person." Added a structured recipient field to the
   action-outcome audit trail and a same-day "how many times have I already messaged
   this person today" check, wired into the SMS confirmation flow: sending a second
   text to the same person on the same day now appends "This is message #2 to them
   today" to the confirmation — a real, visible use of the new data at the moment it
   matters, again avoiding the "invisible unused field" mistake found and fixed in the
   Earned Autonomy item above. Deliberately scoped to SMS only: calendar invites are
   one-off events with no ongoing back-and-forth to give provenance *about*, unlike an
   SMS thread, so extending the same tracking there without a real use for it would
   have repeated the exact mistake this feature exists to fix.

While finalizing this round's pull request, an **unrelated, repository-wide CI outage**
was discovered and fixed: Google had removed a legacy Android SDK package that the CI
workflow's SDK-setup step still requested by default, which had started silently
breaking every single CI run in the whole project (any branch, any commit) since the
external change happened. Diagnosed as external/environmental (confirmed by searching
for the exact error message, which turned out to be a widely-reported, already-
documented breaking change), fixed with a one-line CI dependency version bump, and
merged as part of getting this round's work to a green, mergeable state.

All five items above, plus the CI fix, were squash-merged to `Main` together — the
current state of the repository as of this document.

---

## 3. Complete feature catalog (what NUA can do today)

### Conversation & intelligence
- Local-keyword-first intent routing with AI-based fallback classification for anything
  ambiguous, so simple/common requests never pay for a network round trip.
- Streaming AI replies (spoken as sentences complete, not after the whole reply is
  ready) with prompt caching to reduce repeated-call cost.
- Continuous follow-up conversation mode after a reply finishes — no repeated wake word
  needed, bounded by a maximum follow-up count so the microphone is never left open
  indefinitely.
- Offline fallback: connectivity is checked before every AI round trip; offline, NUA
  answers immediately with a local message instead of waiting out a timeout. Keyword-
  routed local actions (open app, media control, read notifications) already work
  offline since they never call the AI at all.
- Relevance-scoped memory injection: the most relevant ~12 known facts (by keyword
  overlap + recency) are included in a prompt rather than the whole memory table.
- Vision: camera-based photo understanding structured as see → understand → remember →
  act, with photo classification (document/receipt/food/product/screen/sign/whiteboard/
  object/clothing/plant/vehicle), an explicit "Remember" action to save it as a durable
  fact, and permission-gated "Monitor this" that compares a later photo against a saved
  baseline and reports what changed.
- Multi-step task planning: the AI proposes a short plan (e.g., calendar reminders),
  shown for explicit user confirmation before anything is created.
- A structured "What should I do right now?" recommendation (reason, confidence,
  time estimate, or an honest "nothing needs attention" state), with Do it / Remind
  later / Not relevant controls.
- A structured daily briefing (Today / Needs attention / What changed / Risks /
  Opportunities / one recommendation), proactively scheduled and delivered as a
  notification, with a genuinely useful offline-safe fallback if the AI call fails.

### Memory & personal knowledge ("Memory OS")
- Durable fact memory, typed by category (identity / episodic / semantic / behavioral /
  emotional / relationship), each with a recorded source ("why NUA remembers this"), a
  confidence score, and last-used tracking.
- Per-fact detail drill-down, correction (edit in place, not just delete-and-relearn),
  user-set privacy classification (Standard/Sensitive — manual, never auto-guessed),
  bulk "forget everything of this type," and per-fact "forget this."
- **Second Brain search** — lexical search across facts, dreams, and decisions together.
- **Timeline** — one chronological feed merging facts, dreams, decisions, and goal
  observations by real timestamp.
- **Memory Vault / Privacy Centre** — one consolidated privacy screen: what NUA knows
  (with counts), an explicit statement of what's local versus sent to the AI, a live
  permissions overview, a real plain-text data export, and a real "delete everything"
  action covering every category the app's own encryption audit names.

### Goals & decisions
- Durable, user-editable goals with a user-chosen type (aspiration / goal / project /
  commitment / task / routine — never inferred, always explicit).
- Weekly automated goal review against current context, producing at most one concrete
  observation per goal or nothing at all — explicitly instructed never to force one.
- A Decision Journal: log a decision plus optional facts/unknowns/constraints/options,
  then come back later and record the real outcome. Written to deliberately, never
  auto-captured.

### Dreams (proactive insight synthesis)
- A weekly worker that looks across known facts, goals, and trust-ledger history
  together for exactly one genuinely cross-referenced insight (ten categories:
  opportunity, pattern, reminder, concern, optimization, relationship, finance,
  productivity, learning, business) — code-verified (not just prompt-requested) to
  actually connect at least two real, distinct, named things before it's ever recorded,
  with hallucinated connections rejected outright.
- Every valid Dream writes structured, queryable relationship data connecting itself to
  the real facts/goals/trust events it drew from — the first real usage of NUA's
  underlying relationship/"World Model" layer — and the Dreams screen shows exactly
  what each dream connected to.

### Trust, autonomy & safety
- A 0–100 Trust Score computed purely from NUA's own logged action history.
- A full action audit trail: every dispatched action and every plan/reply the user
  approved or declined, with an honest multi-state outcome (not a plain succeeded/
  failed boolean) that distinguishes "handed off to the OS" from "confirmed complete."
- Adaptive autonomy: approve an action type enough times and Settings offers a one-tap
  "always allow automatically" toggle instead of asking every time.
- **Earned Autonomy grants** — every "always allow" grant automatically expires after
  30 days, is automatically revoked the moment that action type fails once, and is
  fully visible and individually revocable in Settings ("NUA may currently do without
  asking").
- A rate-limited, unprompted Trust Ledger self-report at high familiarity — NUA
  proactively tells the user what it's gotten wrong recently, unprompted, at most once
  every 14 days.
- Biometric step-up authentication (fingerprint/face/device PIN) gating every sensitive
  confirmation dialog when the device has biometric hardware enrolled, with an honest
  fallback (not a lockout) when it doesn't.
- A local-encryption audit visible in Settings: what's genuinely encrypted (API key,
  voice profile) versus what isn't (the rest of the on-device database), stated
  plainly.
- A structural Prompt Injection Firewall: only the user's own literal input can ever
  reach the action-dispatch path (enforced at the type level, not just by convention),
  and any external text (document/photo content) that needs to reach an AI prompt is
  explicitly delimited so it can't forge its own escape sequence — even if an
  injected instruction is followed, it can produce a wrong *reply*, never an
  unauthorized *action*.
- An Agent Sandbox: every action-capable skill must declare its required inputs,
  permissions, and a timeout; every dispatch goes through one enforcement point that
  strips undeclared parameters, checks permissions up front, and contains any timeout
  or crash as a normal logged failure rather than a stuck or broken turn.
- Idempotency protection on real-world side-effecting actions (SMS send, calendar
  invite) — a retried or replayed confirmation is recognized and suppressed rather than
  executed twice, without over-applying the same protection to naturally-repeatable
  actions like checking the weather.
- Self-Diagnostics: a real per-area system-health view (API, memory, voice, location,
  calendar, email, automation, Wear, Auto) built from genuine live signals, with areas
  that genuinely can't be verified yet (Wear, Auto) reported as such rather than faked.

### Communication
- SMS send: contact-name resolution to a phone number, always shown for explicit
  confirmation before sending, with same-day thread awareness ("this is message #2 to
  them today").
- Calendar invitations: attendee email resolution and an actual calendar-attendee
  write, executes and reports back; if no email can be resolved, the event is still
  created as a personal note and NUA says so rather than falsely claiming an invitation
  went out.
- Notification quick-reply via the notification's own official reply mechanism,
  always confirmed before sending.
- Gmail and WhatsApp are explicitly **not built** — documented as genuinely blocked
  (no compliant on-device or OAuth-free path exists), not a silent gap.

### Documents
- PDF, Word (`.docx`), and image ingestion with no external document-processing
  library — PDFs are rasterized with Android's own renderer and read via AI vision
  page-by-page (preserving page numbers for later citation); `.docx` files are read
  directly as their underlying zipped XML.
- Automatic summarization (obligations, deadlines, money) plus expiry/renewal date
  detection, with daily reminders once a document's expiry is within 30 days.
- Targeted Q&A and document comparison — ask a question against one or more selected
  documents at once, with page-level citation when the source document has real pages.
- Sensitive-content redaction (Social Security numbers, checksum-validated card
  numbers) applied before any document text is sent to the AI for summarization or Q&A.

### Location & proactive context
- Saved named locations ("home," "the gym") with radius-based arrival notifications.
- The user's current resolved location (matched against their own saved places, never
  raw coordinates) is available as ambient context to relevant AI prompts.

### Home & navigation
- A five-destination structure (Home / Ask / Memory / Act / You) replacing what used to
  be ad hoc boolean-flag navigation.
- A personal "Command Centre" home screen: a live status line, a hero "today" card with
  real counts, a card sourced from an actual Dream, an action card from "What Now?", a
  pending-work list, and an honest "what NUA got wrong recently" card — cards with no
  real data are omitted rather than filled with placeholder content.
- An animated "Orb" with eight distinct visual states (idle/listening/thinking/acting/
  warning/success/error/offline), each derived from real app state rather than driven
  independently, so it can never visually contradict what's actually happening.
- A command palette reachable from anywhere, searching destinations/capabilities/
  memories together — engineered so it can never become a second, less-guarded way to
  trigger a sensitive action: anything above the lowest risk tier is phrased as a
  request and routed through the exact same confirmation/step-up path as typed chat.
- An "Act" screen listing every real capability NUA has, generated directly from the
  same closed registry the action router dispatches through (so it can never drift from
  what NUA can actually do), showing each one's risk tier, required inputs, and
  required permissions.
- A dark-only visual identity (the design spec defines exactly one palette, so a light
  theme was deliberately not invented), with a documented, WCAG-contrast-checked color
  system and a three-band animation timing system that fully respects the OS-level
  "reduce motion" setting everywhere, not just in one component.

### Multilingual & voice
- Ten supported languages (English, Hindi, Gujarati, Marathi, Italian, Spanish,
  Haryanvi, Punjabi, Vietnamese, Mandarin) across four script families.
- Text chat auto-mirrors whichever supported language the user writes in, turn by turn,
  with no setting required; a language can also be explicitly pinned in Settings to
  override mirroring for both text and voice.
- Multiple wake-word phrases supported (built extensible from day one); the app only
  activates phrases whose trained voice model is actually present on-device, showing
  each as Active or Needs Setup in Settings.
- Voice prosody heuristic: a lightweight signal-energy classifier (not a trained
  emotion model) nudges NUA's tone based on how the user's speech sounds, without any
  extra microphone capture beyond what's already happening.
- Home-screen widget (next calendar event, current weather, notification summary) and
  an Android Auto entry point with a minimal, driving-safe status screen.

### Cost & transparency
- A running API usage/cost dashboard estimating spend against published list pricing.
- Self-reported known limitations throughout the app rather than glossed-over gaps (see
  §5 below).

---

## 4. Engineering discipline

This project holds itself to a specific, consistently-applied verification standard,
directly shaped by the August 18–23 incident described above:

- **Investigate real code before assuming a gap exists.** Every feature pass starts by
  reading the actual current implementation, not assuming based on a spec document.
- **Verify the exact commit SHA is green, at the job/step level — never just "the
  branch is green."** A passing overall build status is not trusted on its own; the
  actual test-run step and its test count are checked directly.
- **Two custom static-analysis tools run on every single push, both required to prove
  they can catch a known real defect before they're ever trusted to report "clean":**
  one catches the class of Kotlin declaration-order bug that caused the August 18
  incident; the other catches any code trying to smuggle untrusted external content
  into the AI-action-dispatch path.
- **A dedicated CI step verifies the test suite actually executed** (not just that the
  build succeeded), specifically because Gradle can report "success" on zero tests run.
- **A full unsigned release build (with code shrinking/obfuscation) runs in CI on every
  push**, not just a debug build — this is the step that would have caught a whole
  class of store-submission-day surprise otherwise.
- **Small, complete, honest slices — never speculative architecture.** Repeatedly, work
  explicitly documents what was *not* attempted and why (e.g., free-text PII redaction
  deliberately skipped as an unreliable automatic guess; a graph database deliberately
  not added until an RFC proved a simpler table sufficed; milestones/dependencies for
  goals deliberately deferred as a separately-scoped future feature).
- **"Fail closed," never "fail open," on any ambiguous state** — an autonomy grant with
  an unexpected null expiry reads as *not* granted; an unreadable calendar reads as
  "unknown," never as "your day is free"; document redaction only fires on
  checksum-validated matches rather than any plausible-looking number.
- Every merged feature is documented in a running engineering history with its real
  investigation, what shipped, what was deliberately not attempted, how it was
  verified, and the exact commit SHA it was confirmed green at — this document is a
  condensed synthesis of that full record.

---

## 5. Known gaps and honestly-stated limitations

- **Gmail and WhatsApp integration** are genuinely blocked — no compliant on-device or
  OAuth-free path exists for either; both require external credential/API setup outside
  the codebase before real work can begin.
- **Smart home control** is fully wired end-to-end in the code but the shipped
  implementation always reports "not configured" — it needs a real, separately
  provisioned cloud project and physical device commissioning that isn't part of this
  codebase.
- **Tier 2 (Accessibility Service) automation** is a structural skeleton only — the
  confirmation contract is fully designed, but no concrete Tier 2 action has ever been
  implemented.
- **Android Auto and the Wear OS companion app** both use their respective real
  official platform libraries but have never been tested on real hardware or an
  emulator — CI only confirms they compile. The Wear tile currently shows static text
  only; it has no live connection to the phone app's actual data yet.
- **Geofencing** requires a Google Play Console declaration to be filed by the account
  holder before a build using it can be published — outside what's fixable in code.
- **Test coverage** is thorough for pure business logic (everything that doesn't need a
  live Android runtime) but does not exercise the database layer, the HTTP/streaming
  client, background-worker scheduling, the home-screen widget, or the Compose UI
  itself directly — those would need device/emulator-based testing this environment
  doesn't have.
- **The usage/cost dashboard** estimates spend from hardcoded published pricing — it
  will drift if pricing changes and doesn't reflect any account-specific discount.
- **The Trust Score formula and its adaptive-autonomy approval threshold** are
  documented, sensible starting values, not yet tuned against real long-term usage
  data.
- **No launcher icon / app branding** exists yet — placeholders only.
- **Wake-word listening** requires a paid third-party voice-engine access key to
  function at all; without one, most wake phrases simply won't fire (one built-in
  phrase works without any extra setup). The other phrases are fully wired up in code
  but each needs its own trained voice model file, which has to be produced externally
  per phrase and isn't bundled in this repository.
- **The full "unified Communication Centre"** — one shared triage model across every
  channel NUA can reach, sorting everything into Critical/Important/Normal/Ignore with
  a single "handle anything non-important" command — is only partially built. The
  underlying pieces (notification priority scoring, approval-gated sending) exist;
  the shared cross-channel model and bulk-triage command do not yet.
- **Voice-first depth** (natural mid-sentence interruption handling, whisper/driving
  modes, six-axis tunable personality dimensions, true mid-sentence language
  code-switching) is planned but not yet started.
- **An ecosystem layer** (a skills marketplace, a no-code automation builder,
  multi-device presence, shared/family intelligence, scoped emergency intelligence) is
  planned but not yet started.
- **A dedicated automated agent test lab** (systematic intent-accuracy, hallucination,
  and prompt-injection-resistance testing beyond what CI already runs) is planned but
  not yet started.

---

## 6. Current status (as of this document)

Every item described in §2 through the September 9 "second round" (Context Engine,
World Model read-side resolution, Earned Autonomy, Document Intelligence citation and
redaction, Communication Centre thread provenance) is implemented, individually
verified green in continuous integration at its exact commit, and merged into the
project's main branch — along with an unrelated CI infrastructure fix discovered and
resolved during that same effort. The project's own internal roadmap identifies the
next candidate areas as: deeper voice-first interaction (natural interruption
handling, tunable personality dimensions, true code-switching), the remaining
unified-Communication-Centre triage layer, and the longer-term ecosystem features
(skills marketplace, automation builder, multi-device presence) — none of which have
been started yet.
