# NUA Sovereign 0.5.0 — OpenJarvis Final 10 GO-GO Report

**Date:** 14 November 2026  
**Repository:** BSaumil/NUA---JARVIS  
**Baseline Main:** dfd63a84c8f8240c40b268e3f1130d0184411363  
**OpenJarvis benchmark:** afdbd5b35cb21f51564588c8cdbf6b74889c4f1f  
**Code integration merge:** 5d966050082cc4f345a13654ae5e8c6abaec84fc  
**Release:** NUA 0.5.0 / versionCode 3

## Governing principle

NUA may increase capability and improve performance, but no learned policy, external
tool, connector, imported skill, companion node or self-repair candidate may silently
increase NUA's authority.

## Workstream status

### 1. Sovereign MCP / A2A Gateway — SHIPPED_VERIFIED

Implemented governed RemoteEndpoint manifests/trust, fingerprint drift invalidation,
capability allowlists, HTTPS/pinning, mandatory Privacy Capsule egress, secret-shaped
payload refusal, idempotency, MCP and A2A transports, REMOTE_TOOL, T3 ask-first
classification, UAF adapter bindings and a dedicated remote-gateway CI audit.

Direct router/skill execution refuses remote actions. WorkflowExecutor remains the sole
ActionAdapter execution caller. Remote output is treated as untrusted and omitted from
Flight Recorder detail payloads.

### 2. Presence Mesh + Companion Compute — SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED

Shared Presence v1 schemas cover Android, Wear/Auto, Windows, macOS, Linux, iOS/iPadOS
and future surfaces. Device identities advertise key algorithm/encoding, capabilities,
models, connector state and resource state.

Android uses a persistent Android Keystore P-256 signing identity. Companion service uses
a persistent Ed25519 identity, proves its identity on challenge, verifies Android P-256
proofs, persists pairing/revocation, binds API scopes to owner approval and rejects
nonce replay/clock skew.

Companion supports configured Ollama and OpenAI-compatible local inference endpoints,
covering llama.cpp/vLLM/SGLang-compatible servers when exposed through compatible APIs.

Physical multi-device/LAN acceptance remains hardware validation, not a code claim.

### 3. Complexity-Aware Hybrid Model Mesh — SHIPPED_VERIFIED

Added task difficulty, profile, routing preference and device resource context.

Hybrid modes:
- Local First
- Local Draft -> Frontier Refine
- Frontier Plan -> Local Execute
- Local Advisors -> Frontier Judge
- Parallel Advisors -> Deterministic Aggregator
- Tool Orchestra
- Research Orchestra

Every route starts from privacy-safe fallbackOrder. Complexity, cost, energy and learned
reliability may reorder only providers already allowed by the TaskContract.

### 4. Sovereign Connector Library — SHIPPED_EXTERNAL_ACTIVATION_REQUIRED

Added connector manifests, encrypted secret storage, exact-host HTTPS read connector,
provider catalog templates and write-to-UAF proposal flow.

Catalog includes Google, Microsoft Graph, Dropbox, Slack and Home Assistant templates.

OAuth-backed services require real owner credentials/provider activation. Those external
credentials are intentionally not fabricated or committed.

### 5. External Skills Import / Compatibility — SHIPPED_VERIFIED

Added declarative import normalization for OpenJarvis, AgentSkills-style, Hermes,
OpenClaw and generic JSON manifests.

Recursive scanning refuses script/command/shell/exec/python/javascript/node/binary/
Docker/entrypoint fields before conversion. Imported packages require immutable
source/digest, verified provenance and valid NUA action capabilities.

No imported arbitrary code executes directly.

### 6. NUA Deep Research — SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED

Added encrypted resumable research sessions with question, plan, subquestions, sources,
evidence, claims, contradictions, confidence, synthesis and citations.

Direct URL source provider is HTTPS-only, disables redirects, bounds response size and
treats all source text as untrusted. Model Mesh research planning/extraction/synthesis
retains privacy rules; LOCAL_PRIVATE mode is HIGH + offline-only.

Broad live search-provider activation remains connector/provider integration, while the
research state machine itself is implemented.

### 7. Sandboxed Computation / WASM — SHIPPED_VERIFIED

Companion pins Wasmtime and executes only digest-pinned WASM modules with:
- no WASI;
- no host imports;
- no ambient filesystem/network/process APIs;
- fuel limits;
- memory/instance/table limits;
- wall-time epoch interruption;
- bounded numeric input/output.

Sandbox jobs require an owner-approved SANDBOX scope.

Android does not pretend to expose unrestricted shell/Python execution.

### 8. Energy / Thermal / Cost-Aware Intelligence — SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED

Android routing now consumes battery percentage, charging state, power-save mode,
thermal state, memory pressure and network class plus latency/success telemetry.

Routing preferences include fastest, lowest cost, most private, lowest energy and
balanced. Privacy remains a hard constraint and is applied before energy/cost ranking.

No unsupported precise-joule claims are made. Real battery/thermal measurements remain
physical-device acceptance.

### 9. Evolution Engine Phase 2 / Spec Optimization — SHIPPED_VERIFIED

Added bounded PromptSpecPolicy and ResearchSpecPolicy families in addition to Model Mesh
routing thresholds.

R3 may auto-promote only low-risk reversible whitelist values; every applied policy has
rollback state. Structured intent/fact/planning/briefing/Dreams/repair/research tasks now
consume the prompt spec. Deep Research consumes the research spec.

Evolution CI pins allowed tuning ranges and keeps permissions, UAF authorization,
biometrics, Data Egress, Guardian and local-only repair synthesis outside learnable
authority.

### 10. Local API + Desktop Service + Cross-Platform Packaging — SHIPPED_BUILDABLE_NOT_DEVICE_VERIFIED

Defined Local API v1 with device identity, scopes, nonce/replay control, timestamp bounds,
operation binding and signed canonical payload strings.

Surfaces:
- Android remains primary owner/control surface.
- Windows Tauri shell talks only to localhost companion on the pinned port.
- iOS/iPadOS SwiftUI shell compiles for simulator, uses Keychain and states iOS platform
  limitations honestly.
- Windows/macOS/Linux companion service provides Presence, pairing, model compute and
  sandbox APIs.
- CI packages Windows executable, iOS simulator app and companion wheel.

Production Windows code signing and Apple device/App Store signing require owner
certificates/provisioning and are deliberately external.

## Security gates

Final integration is required to pass:
- forward-reference audit;
- prompt-injection boundary audit;
- UAF boundary audit;
- remote MCP/A2A boundary audit;
- Model Mesh privacy boundary audit;
- Evolution boundary audit;
- JVM unit tests and non-zero test verification;
- debug APK;
- release/R8 APK;
- Windows/macOS/Linux companion tests;
- Windows Rust/Tauri tests/build;
- iOS simulator build.

## Remaining external acceptance

These are not code-completeness failures:

1. real Android Gemini Nano/AICore availability and performance on supported hardware;
2. physical Android <-> desktop Presence pairing over the user's LAN;
3. real OAuth credentials for Google/Microsoft/Dropbox/Slack;
4. Windows production code-signing certificate;
5. Apple Developer provisioning/device/App Store signing;
6. battery/thermal measurements on actual devices.

## Competitive result

OpenJarvis remains a strong general-purpose agent/runtime framework.

NUA now incorporates its highest-value runtime concepts while adding a stricter personal
sovereignty architecture:

**private intelligence + personal world model + cross-device presence + governed external
tools + bounded code execution + evidence-backed research + auditable action + governed
self-improvement.**

**Your Intelligence. Your Control.**
