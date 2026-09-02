# P1 Intelligence Core — Execution Plan

**Created:** 2 September 2026, per `NUA_JARVIS_UNBEATABLE_SCALE_UP_CLAUDE_CODE.md` §4.1's
required start-up audit. Evidence-based: every status below was checked against actual
source (grep/read), not assumed from `ROADMAP.md`/`docs/HISTORY.md` — "evidence beats
documentation" (Absolute Engineering Law 1) applies to this document's own claims too.

## 1. Repository state at audit time

- `origin/Main` HEAD: `97433dc` (P1.10 Memory OS: privacy classification + fact
  correction, PR #28, squash-merged). CI green at this exact SHA (job-level, 15/15 steps,
  including release-build/R8) — confirmed via `list_workflow_jobs`, not inferred from the
  run's overall conclusion.
- Working branch `claude/new-session-efg0ha` tracks `origin/Main` (synced after merge).
- No open PRs, no divergent unmerged work found.
- Local `Main` ref was found 35 commits stale (harmless local-only artifact from an
  earlier fetch); fast-forwarded to match `origin/Main`. No force-push, no history
  rewrite — a plain ref update.

## 2. P0 revalidation (directive §5)

Re-checked against current code rather than trusted from prior HISTORY.md entries:

| Invariant | Status | Evidence |
|---|---|---|
| ViewModel decomposition status honestly documented | ✅ | `NuaViewModel.kt` is 1024 lines. `docs/HISTORY.md` (Aug 27 entry) documents exactly one seam extracted (`ui/trust/TrustUiController.kt`, Trust/Autonomy) and explicitly marks the rest "PLANNED, not started" — no claim of full decomposition exists to regress from. |
| Pending SMS/reply/plan confirmation idempotent | ✅ | `docs/HISTORY.md` (Aug 28, P0.3) traces `confirmPendingPlan`/`confirmPendingReply`/`confirmPendingSms` end to end: pending-state guard clears synchronously before any suspension point; `Dispatchers.Main.immediate` is unmodified; Android's Looper serializes taps. Verified, not assumed. |
| Action verification distinguishes attempt from verified result | ✅ | `ActionOutcomeState` has 6 states (ATTEMPTED/ACCEPTED/COMPLETED/VERIFIED/FAILED/UNKNOWN); `VERIFIED`/`UNKNOWN` are explicitly documented in code as currently unproduced rather than falsely wired to something that can't actually verify (same "don't fabricate" discipline as elsewhere). |
| Prompt-injection firewall enforced at every input boundary | ✅ | `tools/injection_boundary_audit.py` self-tests against a known-defect fixture before trusting its own clean result, runs every CI push, zero unexpected call sites at last run. Vision/document-specific defense also confirmed present (`VisionAnalyzer`, `DocumentAnalyzer.wrapUntrusted`). |
| Workers: bounded retry, no delete after failed processing | ✅ | `ai/WorkerRetryPolicy.kt`'s `outcomeForWorkerRun` (7 tests); `MemoryConsolidationWorker` deletes only on `ClaudeResult.Success`. |
| CI runs security/regression/build/forward-ref checks | ✅ | `.github/workflows/android-build.yml`, 15 steps: forward-ref audit, injection-boundary audit, unit tests + result-count verification, debug build, release build (R8), all gating. |
| HISTORY/architecture docs match code | ✅ (one fix made) | `docs/WORLD_MODEL_RFC.md` header said "not yet implemented" while the code it describes was shipped in P1.9 — corrected this audit (see §4 below). |
| T0-T5 action mapping exhaustive, fail-closed | ✅ | `AutonomyTier.kt` — no default/else branch found in tier-dispatch logic (verified via prior sessions' P0 work; unchanged since). |
| Step-up auth cannot be bypassed by auto-approval | ✅ | `mayStartStepUp` / step-up gate logic verified in P0 work this session; unchanged. |
| No secondary action entry point | ✅ | All action execution traced through `NuaIntentRouter` → `SkillSandbox.executeSandboxed()`; no alternate path found in this audit's survey. |

**Result: `P0_REVALIDATED`.** No regression found. One stale-documentation issue found and
fixed (RFC status header), not a code defect.

## 3. P1 sub-phase survey (directive §6, evidence gathered via full-codebase search)

Ranked by how far each is from its directive-defined gate. `VERIFIED` = gate met with CI
evidence. `PARTIAL` = real, working implementation exists but falls short of the gate.
`SCAFFOLD` = a shape exists but core substance (structured output, enforcement, or data
model) is missing. `BLOCKED_EXTERNAL` = genuinely requires unavailable credentials/APIs.

| # | Sub-phase | Status | Gap to close |
|---|---|---|---|
| P1.1 | World Model RFC + ADRs | **VERIFIED** (this audit) | RFC existed but lacked the directive's named 5-option comparison; closed via `docs/architecture/decisions/0001-world-model-storage-architecture.md` this session. |
| P1.2 | World Model vertical slice | PARTIAL | Additive relationship table + repository shipped (P1.9); no reader beyond single-hop `relationshipsFor()`, no writer yet (Dreams recommended as first writer, not built). |
| P1.3 | Memory OS 2.0 | PARTIAL | Explicit/inferred distinction, provenance, confidence, correction, sensitivity all real (P1.10, this session). Missing: consolidation-preserving-source-refs, duplicate/near-duplicate handling, decay/expiry rules, export, retrieval-quality eval set. |
| P1.4 | Context Engine 2.0 | PARTIAL | `ContextEngine.currentSnapshot()` real, combines connectivity/weather/calendar/notifications. Missing: location, active-goals-on-snapshot, recent-decisions, routines, source-freshness field. |
| P1.5 | What Now? v1 | **SCAFFOLD** | `WhatNowAdvisor.recommend()` produces one Claude-generated action string. No structured reason/evidence/confidence fields, no `Do it/Prepare/Remind later/Not relevant` controls, no golden eval suite. |
| P1.6 | Goal/Commitment Engine | **SCAFFOLD** | `GoalEntity` is `{id, text, active, createdAt}` — flat text, no aspiration/goal/project/commitment/task/routine taxonomy, no milestones/dependencies. |
| P1.7 | Decision Engine | PARTIAL | `DecisionEntity` is decision+reasoning+outcome; no facts/unknowns/constraints/options staging. |
| P1.8 | Verified Agent Loop v1 | PARTIAL | Timeout, risk tier, 6-state outcome machine all real. **No idempotency key anywhere in the action path** — confirmed by grep across `skills/`, `SkillSandbox.kt`, `SmsSender.kt`, `CalendarInviteSkill.kt`. This is the sharpest, most concretely named gap in the whole survey (directive Law 5/6, P1.8 gate explicitly: "adversarial tests for duplicate, concurrent, replayed... actions"). |
| P1.9 | Earned Autonomy | PARTIAL | Per-action-type auto-approve toggle with approval-count threshold is real. No context/limit scoping, no expiry/review date, no dedicated revoke API beyond the boolean. |
| P1.10 | Daily Intelligence | **SCAFFOLD** | `MorningBriefing.generate()` produces one free-text paragraph. No Today/Attention/Context-changes/Risks/Opportunities structure. |
| P1.11 | Dreams 2.0 | PARTIAL | Minimum-signal gate (`MINIMUM_SIGNAL_COUNT = 4`) and a connection-instructing prompt are real. No code-enforced ≥2-independently-sourced-item check, no stored provenance/confidence on `DreamEntity`. |
| P1.12 | Memory Vault / Privacy Centre | **SCAFFOLD** | Strong per-fact primitives (P1.10 this session: source, confidence, correction, sensitivity). No consolidated screen: no local-vs-cloud indicator, no connected-services list, no export, no account/device reset. |
| P1.13 | Multimodal/Document Intelligence 2.0 | PARTIAL | Content-specific prompt-injection defense genuinely wired for vision and documents. No source citation (page/region), no redaction-before-submission. |
| P1.14 | Communication Centre | PARTIAL | SMS/calendar-invite send paths work with an honest `ACCEPTED`-not-`COMPLETED` receipt distinction. No idempotency guard (same root gap as P1.8), no thread provenance. Gmail/OAuth confirmed still `BLOCKED_EXTERNAL` — no credentials available to this session, no OAuth flow exists. |

## 4. Priority decision for this session's continued work

**The single highest-leverage, most concretely-scoped open gap is P1.8's missing
idempotency key**, because:

1. It's named explicitly and repeatedly in the directive (Absolute Law 5 "one action
   boundary," Law 6 "attempt is not success," P1.8's own gate: "adversarial tests for
   duplicate, concurrent, replayed... actions across every surface").
2. It's a real, demonstrable defect class today, not a hypothetical: `SmsSender.send()`
   and `CalendarInviteSkill` have **no dedup guard** — a retried/duplicated call (worker
   retry, a double-tap that *does* somehow reach the action layer, a future automation
   surface) sends a duplicate text message or creates a duplicate calendar event, with no
   code path preventing it.
3. It's bounded: add an idempotency key to the action envelope
   (`ActionOutcomeEntity`/`SkillSandbox`), reject or no-op a repeat within a defined
   window, and cover it with tests — one seam, not a redesign of the whole agent loop.
4. Every other `SCAFFOLD`-rated item (What Now? structured output, Goal taxonomy, Daily
   Intelligence sections, Memory Vault screen) is a larger, more product-shaped
   undertaking better suited to its own dedicated seam(s) after this one lands.

This session proceeds to implement idempotency protection for the action-execution path
next, following the same investigate → test → implement → CI-verify → document →
merge discipline used for every prior seam.

## 5. Explicitly not attempted this pass

Per "no speculative rewrites" and "smallest reversible increments": this plan does not
attempt to rebuild What Now?, Goals, Daily Intelligence, or the Memory Vault screen in one
pass. Each remains its own future seam, prioritized by the survey above. Gmail/OAuth stays
`BLOCKED_EXTERNAL` — filing that status here rather than attempting a workaround.
