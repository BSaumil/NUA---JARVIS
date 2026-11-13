# OpenJarvis Competitive Audit — NUA Sovereign

**Date:** 2026-11-13  
**Benchmark:** open-jarvis/OpenJarvis main @ `afdbd5b35cb21f51564588c8cdbf6b74889c4f1f`  
**NUA baseline:** `b169faf9c05006cf66b950a61b1a7e1c8e08d1fe`

## Executive conclusion

OpenJarvis is strongest as a modular local-AI/agent runtime: local and cloud engines,
continuous operators, broad tools/connectors, portable skills, traces, learned routing,
telemetry/energy benchmarking, MCP/A2A, and research-oriented optimization.

NUA is strongest as a personal mobile intelligence system: durable typed personal memory,
privacy classification, contextual trust/autonomy, biometric step-up, Android-native
actions, prompt-injection boundaries, idempotency, Flight Recorder lineage, personal
goals/decisions/Dreams, document/vision context, and user-visible consent.

The implementation strategy is therefore **adopt the architectural advantages, not clone
the product**. OpenJarvis is Apache-2.0; this round independently implements the useful
patterns in NUA's Kotlin/Android architecture rather than importing its Python runtime.

## Implemented from the benchmark

### 1. Real private OS model tier

NUA's previously-declared `PRIVATE_OS_MODEL` tier is now real through Gemini Nano /
Android AICore via ML Kit Prompt. Runtime availability is capability-probed rather than
guessed from device model.

Safety properties:
- no arbitrary local model server/socket;
- no shell execution;
- no background model download initiated by NUA;
- unsupported tasks report unavailable;
- AICore/status failures degrade to unavailable rather than crashing;
- HIGH-sensitivity contracts exclude cloud by default.

### 2. Privacy-aware Model Mesh

`TaskContract` now has an explicit cloud boundary. `HIGH` sensitivity defaults to
`allowCloud = false`; `requiresOffline` always removes cloud tiers.

`ModelMesh.completeValidated` adds output-contract validation. A local model returning
syntactically unusable output can fall through to another *already authorized* tier; a
validation failure can never widen the privacy contract.

The Mesh also supports separate `userPrompt` and `cloudUserPrompt` payloads. Local
inference may use richer private context while cloud receives a minimized/redacted view.

### 3. Real NUA experiences moved local-first

The following text-only intelligence paths now route through Model Mesh:
- intent classification;
- fact extraction;
- task planning;
- What Now reasoning;
- Daily Intelligence / morning briefing.

Daily Intelligence gives local inference the richer fact set while the cloud fallback is
built through `DataEgressGateway`, excluding sensitive facts without an explicit
privacy capsule.

Three broad autonomous personal-data jobs are stricter still and are now **on-device
only**:
- goal review;
- memory consolidation;
- Dreams synthesis.

If private inference is unavailable, these jobs defer rather than exporting broad
personal history to a cloud model.

### 4. Sovereign Operators

OpenJarvis-style scheduled/continuous agents are translated into NUA-native,
recipe-backed WorkManager operators.

Each operator has:
- an explicit deny-by-default action envelope;
- schedule;
- maximum runs per day;
- cooldown;
- persisted health/run state;
- recipe capability re-validation on every run.

Operators cannot invoke an adapter or skill directly. Execution remains
`RecipeRepository -> WorkflowExecutor`, preserving NUA's authorization, autonomy,
idempotency and Flight Recorder boundaries.

### 5. Sovereign Skill Packages

Portable skills are implemented as **declarative packages, not executable script
bundles**.

Packages declare:
- semantic version and provenance;
- exact NUA action capabilities;
- recipe IDs;
- dependencies.

Activation rejects:
- unknown capabilities;
- missing dependencies;
- dependency cycles;
- excessive dependency depth;
- transitive capability escalation;
- missing recipes;
- recipe/action envelope drift;
- untrusted imported provenance.

Imported packages require verified provenance plus immutable source metadata. Package
content cannot self-assert trust. Every real side effect still runs through UAF.

### 6. Privacy-bounded trace-driven routing

NUA now learns aggregate provider reliability per inference task from:
- accepted/invalid outcome;
- latency;
- sample count;
- recency.

It stores **no prompt, response or personal content** for this routing signal. A tier is
only deprioritized after a minimum sample count and poor recent success rate; history
expires so a later AICore/model update gets another chance.

Most importantly, learning receives the output of `fallbackOrder`. It can reorder only
tiers the current privacy/offline policy already allowed; it cannot add a cloud provider
that the contract excluded.

### 7. CI-enforced privacy boundary

`tools/model_mesh_boundary_audit.py` is self-testing and runs in Android CI. It fails if:
- Dreams, memory consolidation or goal review regain direct `ClaudeApiClient` access;
- those workers lose `HIGH + requiresOffline`;
- HIGH sensitivity stops defaulting cloud off;
- Daily Intelligence loses cloud prompt minimization;
- learned routing stops being subordinate to `fallbackOrder`.

This sits alongside the existing forward-reference, injection and UAF boundary audits.

## OpenJarvis capabilities deliberately not transplanted

### Arbitrary shell / imported executable scripts

Rejected for the personal Android runtime. They would materially weaken NUA's capability
model and create an execution path that is difficult to reconcile with T0-T5,
confirmation, biometric step-up and UAF.

### Raw MCP / A2A clients

Useful eventually, but not as unrestricted network clients. A future NUA implementation
should expose MCP/A2A only through explicit capability descriptors, Privacy Capsules,
Data Egress Gateway and UAF adapters so remote agents cannot become a second action or
data-egress path.

### Desktop/server inference engines

Ollama, vLLM, SGLang and llama.cpp are valuable on workstation/server hardware but are
not direct substitutes for an Android personal assistant's OS-managed private model.
They belong later in Presence Mesh as trusted companion compute nodes.

### GPU/SoC MFU, MBU and rack-style energy telemetry

OpenJarvis's hardware-efficiency telemetry is excellent for model-serving research.
For Android NUA the useful equivalent is battery/thermal/latency measurement around
on-device inference, not NVIDIA/AMD server GPU metrics. That should be measured on real
Android hardware before claims are made.

### Self-optimizing prompts/code

No autonomous source-code rewriting is introduced. Any learning remains bounded routing
or explicitly reviewed configuration. Privacy and authorization policy are not learnable
parameters.

## Competitive position after this round

OpenJarvis remains broader as a general-purpose local-agent research/runtime platform.
NUA is intentionally narrower and stronger in the personal sovereign-assistant category:

**private intelligence + personal world model + bounded autonomy + verified action.**

The important advantage is structural: NUA can use more private context locally, send
less context to cloud, operate continuously through declared capability envelopes, learn
which provider works best without learning its way around privacy, and preserve one
auditable action boundary throughout.
