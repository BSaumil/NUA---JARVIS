# NUA — OPENJARVIS FINAL 10 COMPLETION MASTER DIRECTIVE
## Cross-Platform Sovereign Runtime: Android + Windows + iOS/iPadOS + macOS/Linux Companion + Future Surfaces

**Execution date:** 14 November 2026  
**Target coordination deadline:** **11:00 AM AEST / 12:00 PM Melbourne AEDT, 14 November 2026**  
**NUA repository:** `BSaumil/NUA---JARVIS`  
**Verified baseline branch:** `Main`  
**Verified baseline SHA:** `dfd63a84c8f8240c40b268e3f1130d0184411363`  
**OpenJarvis benchmark:** `open-jarvis/OpenJarvis` `main` @ `afdbd5b35cb21f51564588c8cdbf6b74889c4f1f`  
**NUA current version:** `0.4.0 / versionCode 2`  
**Brand:** NUA Sovereign  
**Primary principle:** **NUA may improve itself automatically. NUA may not silently grant itself new authority.**

---

# 0. MISSION

Complete the remaining ten high-value OpenJarvis-derived capabilities in NUA, translated into NUA's sovereign architecture rather than cloned.

Target architecture:

> **One NUA intelligence fabric across Android, Windows, iOS/iPadOS, macOS/Linux companion nodes, Wear/Auto and future surfaces, with local-first intelligence, bounded autonomy, cross-device presence, secure external tools, traceable execution and governed self-improvement.**

The ten remaining workstreams are:

1. Sovereign MCP / A2A Gateway
2. Presence Mesh + Companion Compute
3. Complexity-Aware Hybrid Model Mesh
4. Sovereign Connector Library
5. External Skills Import / Compatibility
6. NUA Deep Research
7. Sandboxed Computation / WASM
8. Energy / Thermal / Cost-Aware Intelligence
9. Evolution Engine Phase 2 / Spec Optimization
10. Local API + Desktop Service + Cross-Platform Packaging

Do not re-implement work already merged through PR #35 and PR #36:
- Gemini Nano / AICore private OS model
- privacy-aware Model Mesh
- validator-aware fallback
- provider-specific local/cloud prompts
- Sovereign Operators
- Sovereign Skill Packages
- privacy-bounded routing learning
- Evolution Memory
- R0–R4 Evolution Engine
- bounded NUA_TRANSFORM_V1 runner
- Champion/Challenger/Known-Bad repair lab
- local-only transform repair synthesis
- R3 whitelisted runtime tuning + rollback
- Evolution boundary CI audit

---

# 1. PARALLEL WORK OWNERSHIP

## CHATGPT OWNED BRANCH

`chatgpt/openjarvis-final-10-20261114`

ChatGPT owns the **security-critical shared core** and final integration.

### ChatGPT primary workstreams
- #1 Sovereign MCP / A2A Gateway core
- #3 Complexity-Aware Hybrid Model Mesh
- #8 Energy / Thermal / Cost-Aware Intelligence
- #9 Evolution Engine Phase 2
- #10 shared local API / protocol contracts + final integration
- shared protocol foundation needed by #2

### ChatGPT-owned paths
Claude Code must NOT edit these paths unless ChatGPT explicitly hands them off:

- `app/src/main/java/com/nua/assistant/ai/mesh/**`
- `app/src/main/java/com/nua/assistant/evolution/**`
- `app/src/main/java/com/nua/assistant/automation/uaf/**`
- `app/src/main/java/com/nua/assistant/presence/protocol/**`
- `app/src/main/java/com/nua/assistant/telemetry/**`
- `app/src/main/java/com/nua/assistant/localapi/**`
- `tools/*boundary_audit.py`
- `.github/workflows/android-build.yml`
- `.github/workflows/integration-*.yml`
- final integration docs/reports

## CLAUDE CODE OWNED BRANCH

`claude/openjarvis-final-10-20261114`

Claude Code owns **high-volume platform, connector, research, import and sandbox implementation**.

### Claude Code primary workstreams
- #2 Presence Mesh platform clients + companion service
- #4 Sovereign Connector Library
- #5 External Skills Import / Compatibility
- #6 NUA Deep Research
- #7 Sandboxed Computation / WASM
- #10 Windows/iOS/macOS/Linux platform shells and packaging

### Claude-owned paths

Prefer new, isolated paths to prevent merge collisions:

- `clients/windows/**`
- `clients/ios/**`
- `clients/macos/**`
- `clients/linux/**`
- `services/companion/**`
- `shared/protocol-consumers/**`
- `app/src/main/java/com/nua/assistant/connectors/**`
- `app/src/main/java/com/nua/assistant/skills/importers/**`
- `app/src/main/java/com/nua/assistant/research/**`
- `app/src/main/java/com/nua/assistant/sandbox/wasm/**`
- `docs/CLAUDE_*`
- platform-specific CI workflows, except final shared integration workflow

Claude Code must not modify the ChatGPT-owned core paths above.

## INTEGRATION BRANCH

`integration/openjarvis-final-10-20261114`

Only integration-ready commits are cherry-picked/merged here.

Do not use `Main` as the collaborative scratch branch.

---

# 2. TIME-BOXED EXECUTION PLAN

The target window is aggressive. Optimize for **verified vertical slices**, not broad stubs.

## Gate A — Foundation freeze
**By 05:15 AEST / 06:15 AEDT**
- both branches created from exact baseline SHA
- ownership respected
- protocol/contracts agreed
- no modifications to Main

## Gate B — Parallel implementation
**05:15–07:15 AEST / 06:15–08:15 AEDT**
- ChatGPT: #1, #3, protocol base, #8 foundations
- Claude: #2, #4, #5 foundations
- tests added with each subsystem

## Gate C — Agent / sandbox / evolution
**07:15–09:00 AEST / 08:15–10:00 AEDT**
- ChatGPT: #9 Evolution Phase 2 + CI boundaries
- Claude: #6 Deep Research + #7 WASM sandbox
- integration protocol tests

## Gate D — Cross-platform service + shells
**09:00–10:00 AEST / 10:00–11:00 AEDT**
- #10 local API / companion service
- Windows shell build path
- iOS/iPadOS project/build path
- macOS/Linux companion service path
- Android integration
- honest signing/environment blockers documented

## Gate E — GO-GO integration
**10:00–11:00 AEST / 11:00–12:00 AEDT**
- merge/cherry-pick only green commits into integration branch
- run Android CI
- run desktop/server tests
- run iOS compile if macOS/Xcode CI is available
- run Windows compile if Windows runner is available
- run protocol compatibility tests
- run security audits
- final report
- merge to Main only if required gates are green

External signing, proprietary accounts, physical hardware and Apple provisioning are not allowed to be fabricated. Code may be considered implemented while signing/device validation remains explicitly external.

---

# 3. WORKSTREAM 1 — SOVEREIGN MCP / A2A GATEWAY
## Owner: ChatGPT

Implement MCP and A2A as governed NUA execution adapters, never as raw unrestricted clients.

Required flow:

`Model/Agent -> CapabilityDescriptor -> Privacy Capsule -> DataEgressGateway -> MCP/A2A Gateway -> UAF Authorization -> ActionAdapter -> Flight Recorder`

Required:
- `SovereignMcpClient`
- `SovereignA2AClient`
- remote capability/agent manifests
- trust policy
- timeout/retry/cancellation
- idempotency
- endpoint fingerprint/provenance
- allowlist/denylist
- egress minimization
- remote result treated as untrusted
- MCP ActionAdapter binding
- governed A2A equivalent

Must refuse undeclared tools, privilege expansion, remote permission requests, raw secrets, sensitive memory without capsule, identity swaps without re-approval, and any remote action bypassing UAF.

Definition of Done: `ExecutionAdapterType.MCP` has a real tested adapter and A2A has a governed equivalent.

---

# 4. WORKSTREAM 2 — PRESENCE MESH + COMPANION COMPUTE
## Owner: Claude Code

Target Android phone/tablet, Wear, Auto, Windows, macOS, Linux, iPhone/iPad and future nodes.

Each node advertises:
- device ID/public key
- owner label
- platform/app version
- capabilities
- models
- presence
- battery/power/thermal/load where available
- network class
- trust/revocation state

Implement pairing, challenge/response, encryption, revocation, key rotation, reconnect and stale-device expiry.

Companion compute should support configured:
- Ollama
- llama.cpp / OpenAI-compatible local endpoints
- optional vLLM/SGLang
- future NUA model runtime

At least one desktop companion must pair in test and advertise capabilities; revoked/unpaired nodes cannot execute jobs.

---

# 5. WORKSTREAM 3 — COMPLEXITY-AWARE HYBRID MODEL MESH
## Owner: ChatGPT

Add task difficulty/profile and hybrid orchestration.

Difficulty:
- TRIVIAL
- SIMPLE
- MODERATE
- COMPLEX
- FRONTIER

Profile:
- output type
- context size
- reasoning depth
- tool requirement
- privacy
- offline requirement
- cost ceiling
- energy preference
- multimodal requirement
- latency priority

Hybrid modes:
1. Local First
2. Local Draft -> Frontier Refine
3. Frontier Plan -> Local Execute
4. Local Advisors -> Frontier Judge
5. Parallel Advisors -> Deterministic Aggregator
6. ToolOrchestra
7. Research Orchestra

Privacy remains hard constraint. Learned routing cannot add forbidden providers.

---

# 6. WORKSTREAM 4 — SOVEREIGN CONNECTOR LIBRARY
## Owner: Claude Code

Build one connector SDK with auth state, scopes, read/write capabilities, data classes, limits, health, provenance and privacy classification.

Priority:
1. safe HTTP/REST
2. web research
3. Gmail/Google OAuth path
4. Google Calendar/Drive
5. Microsoft Graph/Outlook/OneDrive
6. Dropbox/generic cloud files
7. Slack/Teams abstraction
8. Home Assistant
9. weather/news/finance
10. companion mounted-files connector

No committed secrets. Connector writes go through UAF. Outputs are untrusted. Support revoke/disconnect/health/retry.

---

# 7. WORKSTREAM 5 — EXTERNAL SKILLS IMPORT / COMPATIBILITY
## Owner: Claude Code

Support conversion where feasible from:
- agentskills.io-style manifests
- OpenJarvis
- Hermes
- OpenClaw
- GitHub-hosted manifests
- generic JSON/YAML declarative skills

Pipeline:

`External package -> Parse -> Provenance -> Static scan -> Capability extraction -> NUA conversion -> Guardian tests -> User review -> Sovereign Skill Package`

Do NOT directly execute arbitrary imported scripts.

At least one valid fixture must import and one malicious/over-privileged fixture must be refused.

---

# 8. WORKSTREAM 6 — NUA DEEP RESEARCH
## Owner: Claude Code

Create resumable evidence-backed research state:
- question
- scope
- plan
- subquestions
- sources
- notes
- claims
- evidence links
- contradictions
- unresolved questions
- confidence
- progress
- synthesis
- citations

Pipeline: plan -> search -> read -> evidence -> cross-check -> contradictions -> gaps -> synthesis -> citations -> persistence.

External content is untrusted and cannot become action authority.

Modes: Fast, Standard, Deep, Local/private-documents only.

---

# 9. WORKSTREAM 7 — SANDBOXED COMPUTATION / WASM
## Owner: Claude Code

Implement WASM first.

Sandbox per run:
- immutable digest
- declared inputs
- mounted virtual files only
- no ambient filesystem
- no ambient network
- no process spawn
- no secrets
- CPU/fuel/memory/time/output limits
- cancellation
- provenance/audit

WASM may propose an ActionPlan but cannot directly create external side effects; those return to UAF.

Desktop companion may later expose stronger isolated compute, but never as a raw shell API.

---

# 10. WORKSTREAM 8 — ENERGY / THERMAL / COST-AWARE INTELLIGENCE
## Owner: ChatGPT

Android telemetry:
- inference latency/provider/task
- token estimates
- battery/charging/power-save
- thermal status
- memory pressure
- network class
- estimated cost
- success/validation

Do not claim precise joules without a supported measurement source.

Model Mesh preferences:
- fastest
- lowest cost
- most private
- lowest energy
- balanced

Privacy always outranks cost/energy.

---

# 11. WORKSTREAM 9 — EVOLUTION ENGINE PHASE 2 / SPEC OPTIMIZATION
## Owner: ChatGPT

Safe optimization targets:
- system prompt variants
- structured-output instructions
- tool descriptions
- connector descriptions
- recipe ordering
- retry/backoff
- routing weights
- research heuristics
- skill descriptions
- model/task mapping
- safe timeouts
- cache TTLs

Never auto-modify:
- Android permissions
- UAF authorization
- biometric requirement
- privacy classification
- Data Egress rules
- Guardian gates
- sandbox escape policy
- signing/release protections

Use Champion/Challenger/Known-Bad + held-out tests + rollback.

At least two new safe config classes beyond current routing thresholds must be challengeable/promotable/rollbackable.

---

# 12. WORKSTREAM 10 — LOCAL API + DESKTOP SERVICE + CROSS-PLATFORM PACKAGING
## Shared
### ChatGPT: protocol/security/integration
### Claude Code: platform shells/build systems

Shared versioned API:
- health
- device identity
- capabilities
- model availability
- chat/inference
- research jobs
- sandbox jobs
- presence
- pairing
- status/cancel
- streaming/events
- connector health

Security:
- localhost by default
- authenticated paired LAN transport
- authenticated encryption
- replay protection
- version negotiation
- rate limits
- no unauthenticated privileged endpoint

Windows:
- desktop shell
- companion service
- tray/menu
- pairing UI
- model/capability status
- logs/diagnostics
- clean lifecycle

iOS/iPadOS:
- pair
- chat/stream
- NUA state
- devices
- research/jobs
- pending authorization
- Keychain
- honest background limitations

macOS/Linux:
- headless companion service first

Android:
- remains primary owner/control surface

Packaging where environment permits:
- Android APK + release/R8
- Windows executable/installer/build artifact
- iOS Xcode project + simulator/archive CI where possible
- macOS/Linux runnable package

Never fabricate signing or device validation.

---

# 13. SHARED PROTOCOL

Canonical schemas should include:
- DeviceIdentity
- DeviceCapability
- PresenceState
- ComputeNode
- ComputeJobRequest
- ComputeJobResult
- ModelAdvertisement
- ConnectorAdvertisement
- RemoteCapability
- ResearchJob
- SandboxJob
- PairingChallenge
- PairingApproval
- RevocationRecord

Claude consumes protocol authored by ChatGPT and must not silently redefine it.

---

# 14. SECURITY NON-NEGOTIABLES

Reject any design that introduces:
- raw Android shell path
- direct external action outside UAF
- unrestricted MCP/A2A reachable by model
- arbitrary imported code execution
- auto permission expansion
- privacy relaxation
- biometric bypass
- Guardian bypass
- secret commits
- unauthenticated companion control
- silent pairing
- arbitrary remote filesystem access
- arbitrary network proxy
- cloud fallback for HIGH/offline
- learned provider expansion beyond policy
- self-merge/self-release
- fake hardware validation

---

# 15. REQUIRED TEST MATRIX

Unit:
- schemas
- routing
- capability diff
- imports
- pairing/revocation
- research state
- sandbox limits
- evolution promotion
- cost/energy

Security/adversarial:
- MCP injection
- connector injection
- malicious skill
- capability escalation
- revoked device
- replay token
- endpoint identity swap
- sandbox escape
- research-source injection
- HIGH privacy cloud attempt
- Evolution weakening attempt

Integration:
- Android -> companion pair
- Android -> companion inference
- connector read/write
- skill import -> recipe -> UAF
- Deep Research state machine
- WASM job
- Evolution challenger -> rollback
- protocol version negotiation

Build:
- Android tests/debug/release-R8
- desktop service tests
- Windows compile
- iOS compile/simulator when available
- macOS/Linux service compile
- protocol compatibility

---

# 16. CLAUDE CODE RULES

Claude may inspect repo, create/refactor in its owned paths, add dependencies/tests/platform projects, run builds, commit/push to its branch and open a PR.

Claude must NOT:
- edit ChatGPT-owned paths without explicit handoff
- merge to Main
- force push
- fabricate secrets/signing/device results
- weaken UAF/Guardian/Privacy/Evolution to pass tests
- add alternate action paths
- add unrestricted shell execution to Android

If externally blocked:
1. implement code boundary;
2. add fakes/tests;
3. document exact blocker;
4. continue other work.

Do not stop for routine questions.

---

# 17. CLAUDE REQUIRED HANDOFF

Create:

`docs/CLAUDE_OPENJARVIS_FINAL10_HANDOFF_2026-11-14.md`

Include:
1. branch
2. exact head SHA
3. commits
4. files changed
5. workstream status
6. tests/results
7. platform builds
8. artifacts
9. security decisions
10. blockers
11. protocol assumptions
12. dependencies
13. known issues
14. recommended integration order
15. final statuses using only:

- SHIPPED_VERIFIED
- SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED
- SHIPPED_EXTERNAL_ACTIVATION_REQUIRED
- DEFERRED_PLATFORM_LIMITATION
- BLOCKED_CRITICAL

No vague "mostly done."

---

# 18. INTEGRATION ORDER

Into `integration/openjarvis-final-10-20261114`:

1. shared protocol
2. ChatGPT MCP/Model Mesh core
3. Claude Presence Mesh companion
4. connectors
5. skill importers
6. Deep Research
7. WASM
8. energy telemetry
9. Evolution Phase 2
10. platform shells
11. CI/audits
12. docs/versioning

Preserve stricter security behavior on conflicts.

---

# 19. GO-GO ACCEPTANCE

GO-GO only when:
- no unexplained critical gap
- MCP/A2A governed by UAF/Privacy
- Presence Mesh has identity/pair/revoke
- hybrid Mesh preserves privacy
- connector writes use UAF
- imports cannot run arbitrary code
- Deep Research has evidence/citations
- sandbox has resource/capability limits
- cost/energy cannot override privacy
- Evolution cannot alter security authority
- Android green
- Windows/iOS/companion compile-verified where environment permits
- unverified signing/hardware marked honestly
- exact SHAs recorded
- final implementation report exists

---

# 20. CLAUDE CODE START DIRECTIVE

Start from:

Repository:
`BSaumil/NUA---JARVIS`

Branch:
`claude/openjarvis-final-10-20261114`

Baseline:
`dfd63a84c8f8240c40b268e3f1130d0184411363`

Your owned workstreams:
**2, 4, 5, 6, 7 and platform implementation of 10.**

Read first:
- `docs/OPENJARVIS_COMPETITIVE_AUDIT_2026-11-13.md`
- `docs/NUA_SOVEREIGN_EVOLUTION_ENGINE.md`
- `docs/HISTORY.md`
- `ROADMAP.md`
- `app/src/main/java/com/nua/assistant/automation/uaf/`
- `app/src/main/java/com/nua/assistant/automation/packages/`

Do not change ChatGPT-owned paths.

Implement complete vertical slices with tests.

Commit/push in isolated batches.

When green, create:
`docs/CLAUDE_OPENJARVIS_FINAL10_HANDOFF_2026-11-14.md`

Do not wait for routine approval.
Do not merge to Main.
Do not weaken safety to meet the deadline.

---

# 21. PRODUCT PRINCIPLE

OpenJarvis demonstrates the breadth possible in a local agent runtime.

NUA's target is stronger:

> **NUA Sovereign — one intelligence across every device, able to reason, research, connect, compute, act, learn and improve, while every permission and every expansion of authority remains owned by the user.**

**Your Intelligence. Your Control.**
