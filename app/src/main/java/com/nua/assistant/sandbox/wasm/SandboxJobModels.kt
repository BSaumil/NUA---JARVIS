package com.nua.assistant.sandbox.wasm

import kotlinx.serialization.Serializable

/**
 * OpenJarvis Final 10, workstream 7 (Sandboxed Computation / WASM) — Android's half of
 * the split with `services/companion/nua_companion/sandbox.py`, which owns the real
 * `wasmtime` execution (no WASI, no host imports, fuel/memory/time-bounded — see that
 * module's own doc comment). Android never runs WASM itself; it submits a job to a
 * paired companion via [com.nua.assistant.presence.protocol.ComputeJobRequest] and
 * interprets the result. This file is the payload/result shape carried inside that
 * envelope's `payloadJson`/`resultJson` — a private contract between this Android
 * package and the companion's `sandbox.py`, layered *inside* the canonical protocol
 * rather than redefining it (`ComputeJobRequest`/`ComputeJobResult` themselves are
 * ChatGPT's `presence.protocol` types, untouched).
 */
@Serializable
data class WasmJobPayload(
    val programBase64: String,
    val expectedSha256: String,
    val export: String = "run",
    val inputValue: Int? = null,
)

@Serializable
data class WasmJobResultPayload(
    val succeeded: Boolean,
    val result: Double? = null,
    val errorCode: String? = null,
    /** A WASM module's only way to affect the world outside its own sandbox: naming an
     *  action by the exact same closed [com.nua.assistant.ai.NuaActionType] set every
     *  other caller is bound to, never a fabricated or free-form capability. See
     *  [SandboxJobRepository]'s own doc comment for why this can never execute directly. */
    val proposedPlan: ProposedPlanPayload? = null,
)

@Serializable
data class ProposedPlanPayload(val steps: List<ProposedStepPayload>)

@Serializable
data class ProposedStepPayload(val action: String, val parameters: Map<String, String> = emptyMap())

/** What [SandboxJobRepository.submit] actually produced, for the caller to show/log —
 *  deliberately distinct from the raw [WasmJobResultPayload]: this names exactly what
 *  happened to any proposed plan, not just whether the sandbox computation succeeded. */
sealed interface SandboxJobOutcome {
    data class ComputedOnly(val result: Double?) : SandboxJobOutcome
    data class PlanSubmitted(val result: Double?, val planId: String, val stepCount: Int) : SandboxJobOutcome
    data class PlanRefused(val reason: String) : SandboxJobOutcome
    data class Failed(val errorCode: String) : SandboxJobOutcome
}
