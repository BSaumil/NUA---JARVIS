# NUA Sovereign Evolution Engine

Status: Implemented foundation for NUA 0.4.0
Date: 2026-11-13
Product principle: NUA may improve itself automatically. NUA may not silently grant itself new authority.

## Purpose

The Sovereign Evolution Engine gives NUA a bounded self-improvement loop:

1. observe real failures;
2. remember recurring failure signatures without storing user content;
3. generate a repair candidate;
4. test Champion vs Challenger;
5. prove the evaluation harness can reject a Known-Bad control;
6. run Guardian/privacy/authority/regression gates;
7. auto-promote only a tiny reversible runtime-tuning envelope;
8. roll back an applied runtime tuning;
9. require owner/release approval for code-like changes.

It is intentionally not a live self-rewriting Android application.

## Repair levels

### R0 — Observe
Records failure signatures only. No repair candidate is generated.

### R1 — Suggest
Creates repair candidates but does not test or change behaviour automatically.

### R2 — Auto-test (default)
NUA may generate challengers and run the Self-Repair Lab automatically. No runtime change is applied.

### R3 — Auto-promote runtime
After a valid Champion/Challenger/Known-Bad experiment passes, NUA may auto-promote only a low-risk, reversible, authority-neutral runtime tuning that has a real whitelisted apply/rollback path.

Current R3 auto-promotion whitelist:
- Model Mesh minimum reliability sample count: 3–20.
- Model Mesh minimum success-rate threshold: 30%–80%.
- Model Mesh routing-memory lifetime: 1–30 days.

These settings can reorder only provider tiers that already survived Model Mesh privacy/offline policy. They cannot add a cloud tier, grant a new action capability, add a permission, or bypass UAF.

### R4 — Code promotion requires approval
NUA may prepare/test a source, recipe or skill-package challenger, but the result is REQUIRES_OWNER_APPROVAL. The Android runtime cannot self-merge, self-release, or silently turn such a candidate into executable production code.

Any repair attempting to bypass UAF or weaken Guardian is refused at every level.

## Evolution Memory

EvolutionMemoryStore persists only structured, content-free repair telemetry:
- failure source;
- component name;
- category;
- stable reason code;
- severity;
- recurrence count;
- first/last seen timestamps;
- candidate/outcome references.

The failure fingerprint is SHA-256 over those structural fields. It is not a hash of the prompt, response, recipient, document, memory or other user content.

Current live producers:
- Model Mesh provider failures.
- Model Mesh output-contract validation failures.
- UAF missing capability/adapter failures.
- UAF adapter failure/exception.

Authorization refusal is explicitly not recorded as a repairable failure. A legitimate safety denial must never teach NUA to remove the safety control.

## Sovereign Code Runner

The Android runner currently enables one runtime:

NUA_TRANSFORM_V1

It is an in-memory, bounded transformation language with:
TRIM, LOWERCASE, UPPERCASE, REPLACE_LITERAL, PREFIX, SUFFIX, TAKE, DROP, EXTRACT_BETWEEN, JSON_FIELD and SHA256.

Resource limits include input bytes, output bytes, operation count and wall-time budget.

The Android Evolution package has no process execution, OS shell, filesystem, direct network client, dynamic class loader, reflection escape, ActionAdapter access, CapabilityRegistry access or WorkflowExecutor access.

The enum reserves future names for JavaScript, Python, Shell and WASM, but those runtimes currently return UNSUPPORTED_RUNTIME. They are not represented as available.

## Private repair synthesis

TransformRepairSynthesizer can ask the private OS model to synthesize a bounded transform challenger from failing examples.

The inference contract is:
- SENSITIVE_LOCAL_TRANSFORM
- PrivacySensitivity.HIGH
- requiresOffline = true

Therefore examples never fall back to cloud. If Gemini Nano/AICore is unavailable, synthesis returns no candidate.

The model may only propose the bounded instruction set above, with structural validation and an operation-count limit. A synthesized program is still only a challenger; it does not become active just because the model produced it.

## Self-Repair Lab

TransformRepairExperimentRunner executes the same ephemeral examples through Champion, Challenger and Known-Bad.

Promotion requires the evaluation harness to detect Known-Bad first.

evaluateRepairExperiment then rejects the challenger when any of these are true:
- insufficient sample count;
- Guardian violation;
- privacy violation;
- authority violation;
- non-target regression;
- no target failure fixed;
- overall correctness regression;
- excessive latency regression.

A model claiming “I fixed it” is not evidence. The challenger has to outperform the current implementation under a harness that has proven it can detect a deliberately broken control.

## Runtime auto-repair and rollback

EvolutionRuntimeTuningApplier currently applies only the Model Mesh routing-policy whitelist.

Before applying a candidate, it stores the previous routing policy under the candidate ID. rollback(candidateId) restores that snapshot and removes the rollback record.

A repair outside the whitelist fails closed.

## UI control

Settings → NUA Evolution exposes R0–R4 explicitly.

Default: R2 — Auto-test.

R3 and R4 require an owner selection in Settings. They are not silently enabled by the app, a model, a repair candidate, a migration or an update.

## CI / Guardian enforcement

tools/evolution_boundary_audit.py self-tests against a known process-execution violation before auditing the repository.

The build fails if the Android Evolution package introduces Runtime.exec / ProcessBuilder, dynamic class loading, reflection escape, direct network APIs, direct filesystem APIs, direct ActionAdapter / CapabilityRegistry / WorkflowExecutor access, or system native library loading.

The audit also pins NUA_TRANSFORM_V1 as the only enabled Android code-runner runtime, owner-approval handling for source-like repairs, and explicit runtime-tuning whitelist/ranges.

This runs in Android CI alongside the forward-reference, injection, UAF and Model Mesh privacy audits.

## What is deliberately not claimed

Not implemented/activated:
- arbitrary Android shell execution;
- unrestricted Python/JavaScript execution;
- arbitrary filesystem access;
- arbitrary network access;
- downloading and executing remote code;
- self-modifying APK bytecode;
- silent source-code merge;
- silent release/deployment;
- automatic Android-permission expansion;
- automatic new UAF capabilities;
- automatic privacy-policy relaxation.

Those are authority changes, not ordinary learning.

## Future safe expansion

The next safe runtime expansion should be an isolated WASM or out-of-process companion sandbox with no ambient filesystem/network, explicit mounted inputs, CPU/memory/time quotas, signed/provenanced code, declared capabilities, all external side effects compiled back into UAF ActionPlans, and full Flight Recorder lineage.

Desktop/companion execution can later live behind Presence Mesh so powerful local compute does not turn the Android process into a general-purpose privileged shell.

## Competitive thesis

NUA's target is not “an agent that can execute anything.”

It is an intelligence that can learn from failure, generate a challenger, prove the challenger is better, promote only changes inside its existing authority, roll them back, and explain what happened.

Your Intelligence. Your Control.
