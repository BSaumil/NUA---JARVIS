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

The strategy is therefore **adopt the architectural advantages, not clone the product**.

## Adopted now

### 1. Real private OS model tier

OpenJarvis's local-engine breadth exposed NUA's most important gap: Model Mesh declared
LOCAL_MODEL/PRIVATE_OS_MODEL but had no actual model behind either.

NUA now implements PRIVATE_OS_MODEL with Gemini Nano via Android AICore/ML Kit Prompt.
It is capability-probed at runtime, text-task scoped, exception-safe, and local-first.

Security improvement over a generic local runtime:
- no arbitrary model server/socket;
- no shell execution;
- no model download initiated by background work;
- unsupported tasks report unavailable;
- HIGH-sensitivity contracts exclude cloud by default.

### 2. Privacy-aware routing contract

TaskContract now has an explicit cloud boundary. HIGH sensitivity defaults to local-only.
A caller can permit cloud only explicitly, intended to happen after Data Egress Gateway
policy/consent. This removes "local unavailable => silently send sensitive context away"
as a routing failure mode.

## Adopt next

### Sovereign Operators

Translate OpenJarvis Operators into recipe-backed persistent workers:
- WorkManager scheduling;
- deny-by-default declared action envelope;
- per-operator rate limit;
- heartbeat/health;
- failure backoff;
- all effects through WorkflowExecutor;
- all sensitive actions still require live autonomy/confirmation;
- no shell/code execution.

### Sovereign Skill Packages

Extend NUA's existing NuaSkill catalog with signed, declarative skill manifests:
- required NuaActionType capability set;
- dependency DAG validation;
- max composition depth;
- source/provenance;
- signature/trust state;
- injection scan before activation;
- no imported scripts by default;
- every side effect compiled into ActionPlan and executed by WorkflowExecutor.

### Trace-driven routing

Use NUA's existing Flight Recorder/action outcomes and model usage as the substrate for
routing feedback. Learn only after a minimum sample count; include success, latency, user
correction/rejection, privacy and cost. Never let a learned policy widen cloud permission
or autonomy.

### Local-cloud collaboration

For long/sensitive context, local model should extract/redact/compress first; cloud may
reason only over the approved capsule. This should be implemented inside DataEgressGateway
and ModelMesh, not as a second inference path.

### Evaluation discipline

Add repeatable suites for:
- response quality;
- local/cloud route choice;
- latency;
- cost;
- privacy-egress violations;
- action success;
- autonomy refusal correctness;
- memory retrieval quality;
- battery impact on Android.

## Explicitly rejected

NUA will not adopt:
- default-open capability policy;
- arbitrary shell execution in the personal Android runtime;
- imported executable scripts by default;
- tool/plugin paths that bypass T0-T5, biometric, sandbox, idempotency or Flight Recorder;
- learned routing that can override privacy constraints;
- autonomous self-modification of application source.

## Competitive target

NUA should combine OpenJarvis-class local execution/orchestration with a stronger personal
world model, safer autonomy, richer mobile context and verifiable action lineage.

The differentiator is not "more tools." It is:

**private intelligence + personal world model + bounded autonomy + verified action.**
