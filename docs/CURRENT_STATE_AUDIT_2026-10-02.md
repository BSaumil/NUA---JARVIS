# NUA — Current State Audit (2026-10-02)

Fresh repository truth audit per the Personal Test Deployment + Completion Master
Directive, §1. Verified against actual source, Gradle files, manifests, CI workflows,
and current test files — not against prior documentation's claims. Supersedes any
conflicting classification in `README.md`/`ROADMAP.md`, both of which predate the
September 27–30 work and are flagged stale where relevant (see §21 reconciliation).

Classification: **DONE** / **PARTIAL** / **NOT STARTED** / **BLOCKED EXTERNALLY** /
**OBSOLETE**.

---

## Core platform

| Item | State | Evidence |
|---|---|---|
| Native Android app, Kotlin/Compose/Hilt/MVVM | DONE | whole `app/` module |
| Local-first Room memory | DONE | `memory/MemoryStore.kt`, DB version 20 |
| Encrypted API key / voice-profile storage | DONE | `memory/SecureKeyRepository.kt` (EncryptedSharedPreferences) |
| Claude-backed AI | DONE | `ai/ClaudeApiClient.kt` |
| Local keyword-first routing | DONE | `automation/KeywordIntentMatcher.kt`, still the router's own path — see Action Fabric below |
| Streaming AI replies | DONE | `ClaudeApiClient`'s streaming support |
| Offline fallbacks | DONE | `ConnectivityMonitor` + `isOffline` paths throughout |
| Continuous follow-up mode | DONE | existing chat flow |
| Ten-language support | DONE | `voice/NuaLanguage.kt` |
| Multiple wake-word architecture | PARTIAL | wired end to end; only one phrase ("Jarvis") fires without an externally-trained `.ppn` model — **BLOCKED EXTERNALLY** for the other five |
| Voice prosody heuristic | DONE | `voice/VoiceProsody.kt` |
| Home-screen widget | DONE (untested on device) | Glance widget code exists; no Robolectric/instrumented coverage — see §13 |
| Android Auto entry surface | PARTIAL | `car/NuaCarAppService.kt` compiles, real Car App Library usage, **never run on a head unit or DHU emulator** |

## Intelligence / memory

All DONE and unchanged since the last audit: Memory OS, typed facts, relevance-scoped
injection, fact correction, privacy classification, Second Brain search, Timeline,
Decision Journal, Goals, Context Engine, structured What Now?, structured Daily
Intelligence, Dreams 2.0, relationship/world-model foundation (`world/`).

## Vision / documents

All DONE and unchanged: vision pipeline, photo classification, remember/monitor flows,
PDF/DOCX/image intelligence, summarization, deadline/expiry extraction, cross-document
Q&A, page-level citation, structured PII redaction.

## Trust / security / autonomy

All DONE and unchanged: T0–T5 autonomy model, Trust Score, action audit trail, truthful
multi-state outcomes, biometric step-up, Prompt Injection Firewall, Agent Sandbox,
idempotency, legacy earned-autonomy grants, Memory Vault/Privacy Centre,
Self-Diagnostics, worker cancellation hardening, CI test-count gate, release/R8
verification, the three self-proving static audits (`tools/*_audit.py`).

## Communication / action

SMS path, calendar invites, notification RemoteInput quick reply, recipient/thread
provenance — all DONE, unchanged.

---

## 5-Year Standalone work — verified against real code, not the prior report's summary

| Feature | Verified state |
|---|---|
| **Universal Action Fabric** | PARTIAL. `automation/uaf/` is real: `CapabilityDescriptor`, `ActionPlan`/`PlanStep`, `WorkflowExecutor` (one authorization checkpoint), `CapabilityRegistry`. Only 2 of 7 named adapter types implemented (`LocalNativeAdapter`, `NotificationRemoteInputAdapter`). **Confirmed by grep: `NuaIntentRouter.kt` contains zero references to `WorkflowExecutor`** — it is still a fully separate dispatch path, not converged. |
| **Sovereign Model Mesh** | PARTIAL. `ai/mesh/` routing core is real and tested. **Confirmed by grep: only `IntentClassifier.kt` calls `ModelMesh`.** 11 other files still call `claudeApiClient` directly: `FactExtractor`, `TaskPlanner`, `NuaViewModel` (main chat), `GoalReviewWorker`, `DreamSynthesisWorker`, `MorningBriefing`, `VisionAnalyzer`, `SelfDiagnosticsRepository`, `WhatNowAdvisor`, `DocumentAnalyzer`, `MemoryConsolidationWorker`. |
| **Privacy Capsules / Data Egress Gateway** | PARTIAL. Real policy enforcement exists on exactly one integration point (main chat fact extraction, per `docs/HISTORY.md`). Documents/vision/goals/decisions/Dreams/briefings/What Now?/Recipes parsing are not covered. |
| **Flight Recorder** | DONE. `trust/lineage/` hash-chained lineage, wired into `WorkflowExecutor` itself — every Action Fabric step is recorded. No UI reads it yet. |
| **Guardian Lab baseline + extension** | PARTIAL. `security/guardian/` has 4 test files (`UafAdversarialTest`, `AutonomyContractAdversarialTest`, `RecipeAdversarialTest`, `RuntimeSafetySentinelTest`) covering 6 of 16 originally-named adversarial scenario classes. `RuntimeSafetySentinel.kt` (`auditContracts`/`auditRecipeSteps`) is real and tested but **confirmed by grep: has zero callers outside its own file and tests** — fully diagnostic, not wired to any live path. |
| **Contextual Autonomy Contracts + Shadow Mode** | PARTIAL. `trust/AutonomyContract.kt`, `AutonomyContractEntity`/DAO, `TrustRepository` methods (`createContract`/`contractDecisionFor`/`recordShadowPrediction`) are real and wired into the real decision path (`NuaViewModel.applyPendingEffect`, `RecipeRepository`). **Confirmed by grep: zero Settings/UI screen references `createContract` or any contract type** — no way for a user to create, view, or revoke a contract, or see Shadow Mode's accuracy, except by calling the repository directly. |
| **NUA Recipes** | PARTIAL. `recipes/` compiler, simulator, and repository are real; `RecipeRepository.runRecipe` genuinely calls `WorkflowExecutor.run` — the fabric's first real production caller. Parsing is deterministic-only (`KeywordIntentMatcher`-based) — **confirmed: `RecipeCompiler.kt`'s only `ModelMesh` reference is a doc comment, no actual LLM fallback call**. No scheduling/triggers (`runRecipe` only fires when called directly — no WorkManager wiring). No step dependency DAG (every compiled step has empty `dependsOn`). No creation/review UI. |
| **Runtime Safety Sentinel** | See Guardian Lab row above — real, tested, unwired. |
| **Temporal World Model 2.0 / CommitmentGraph** | NOT STARTED. Explicitly skipped in the prior session on direct user instruction ("Move to Phase C"), never investigated for feasibility. |
| **Counterfactual Decision Simulator** | NOT STARTED. Same as above; the directive itself notes it depends on the Temporal World Model existing first. |
| **Conditional release-signing infrastructure** | DONE, verified CI-green at commit `45d6c11` (job-level, all 17 steps). `app/build.gradle.kts` reads `storeFile`/`storePassword`/`keyAlias`/`keyPassword` from local `keystore.properties` or CI secrets (`RELEASE_STORE_FILE`/etc.), else builds unsigned. **Current state: the four `RELEASE_*` repository secrets have not been added** (confirmed — no commit since `45d6c11` has changed; nothing to indicate secrets were added, and secret addition alone doesn't produce a new commit/run to check). Release APK is currently unsigned. |

---

## Deployment readiness (this directive's own new scope)

| Item | State |
|---|---|
| NUA Sovereign brand (tokens/wordmark/icon/Orb mapping/doc) | NOT STARTED as of this audit — existing palette is the prior "Orange/Violet/Pink" identity (`ui/theme/NuaPalette.kt`), existing launcher icon is an explicit placeholder (`ic_launcher_foreground.xml`'s own comment: "no real launcher icon yet"). Full remap plan below, implementing next. |
| Database migration hardening | NOT STARTED. `di/AppModule.kt:45` uses `.fallbackToDestructiveMigration()` unconditionally; DB is at version 20 with no `Migration` objects anywhere in the codebase. |
| Robolectric/instrumented tests | NOT STARTED. No Robolectric dependency in `app/build.gradle.kts`; no `androidTest` source set with real instrumented tests beyond what may be scaffolded. All 400+ existing tests are pure-JVM logic tests. |
| Accessibility Tier 2 first action | NOT STARTED. `automation/NuaAccessibilityService.kt` exists as a structural skeleton (confirmation contract, `lastFailure`) with zero concrete actions. |
| Presence Mesh / Wear transport | NOT STARTED. Investigated in the prior session and found to need a transport decision + a real device to verify against — neither exists. `:wear` module is a static Tile only. |
| Android Auto real verification | BLOCKED EXTERNALLY. No Desktop Head Unit emulator or physical head unit available in this execution environment. |
| Voice-first depth (barge-in, personality axes, code-switching) | NOT STARTED. |
| Public Play Store prep (Privacy Policy, Data Safety, `SEND_SMS` strategy) | NOT STARTED beyond the `SEND_SMS` policy research already on record in `docs/HISTORY.md`. |

---

## What this audit changes about the starting hypothesis

The directive's own §2 "already delivered" list is accurate for what it names, but
several 5-Year-Standalone items it describes only as "foundation" are confirmed, by
direct grep against current source, to be **more incomplete** than a casual read of
`FINAL_5_YEAR_STANDALONE_IMPLEMENTATION_REPORT.md` might suggest — specifically the exact
call-site counts for Model Mesh (11 remaining, not "some") and the confirmation that
`RuntimeSafetySentinel` has *zero* live callers, not just "insufficient" ones. Nothing in
the directive's hypothesis was found to be overstated to the point of being wrong — the
prior report was already honest about scope — this audit just makes the remaining gap
exactly countable before work starts.
