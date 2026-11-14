package com.nua.assistant.sandbox.wasm

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.uaf.ActionPlan
import com.nua.assistant.automation.uaf.AdapterExecutionContext
import com.nua.assistant.automation.uaf.AuthorizationProof
import com.nua.assistant.automation.uaf.CapabilityRegistry
import com.nua.assistant.automation.uaf.PlanStep
import com.nua.assistant.automation.uaf.WorkflowExecutor
import com.nua.assistant.presence.protocol.ComputeJobRequest
import com.nua.assistant.trust.idempotencyKeyFor
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val WASM_CAPABILITY = "sandbox.wasm"

/**
 * OpenJarvis Final 10, workstream 7 — the Android half. Submits a WASM computation to a
 * paired companion ([SandboxTransport], real implementation in the Presence Mesh
 * companion client) and, if the result proposes an [ActionPlan], routes it through the
 * real [WorkflowExecutor] rather than ever creating a side effect itself. This is the
 * directive's own explicit boundary: "WASM may propose an ActionPlan but cannot directly
 * create external side effects; those return to UAF."
 *
 * A proposed step naming an action with no real [CapabilityRegistry] entry, or a string
 * that isn't a real [NuaActionType] at all, refuses the **whole** plan rather than
 * silently dropping just that step — unlike a user's own Recipe (where an unresolved
 * clause is an honest, visible gap, `recipes/RecipeCompiler.kt`'s own stance), a sandbox
 * result is computation-derived, not something a person directly reviewed before this
 * point, so a single bogus/hallucinated action is treated as evidence the whole proposal
 * shouldn't be trusted rather than something to partially honor.
 *
 * Steps run sequentially (each depending on the previous one) — the ordering a module's
 * own `steps` list already expresses — not a WASM-controlled dependency graph; nothing
 * here lets sandboxed code describe its own DAG.
 */
@Singleton
class SandboxJobRepository @Inject constructor(
    private val transport: SandboxTransport,
    private val capabilityRegistry: CapabilityRegistry,
    private val workflowExecutor: WorkflowExecutor,
    private val json: Json,
) {
    suspend fun submit(
        program: ByteArray,
        expectedSha256: String,
        context: AdapterExecutionContext,
        export: String = "run",
        inputValue: Int? = null,
    ): SandboxJobOutcome {
        val payload = WasmJobPayload(
            programBase64 = Base64.getEncoder().encodeToString(program),
            expectedSha256 = expectedSha256,
            export = export,
            inputValue = inputValue,
        )
        val request = ComputeJobRequest(
            jobId = UUID.randomUUID().toString(),
            capability = WASM_CAPABILITY,
            payloadJson = json.encodeToString(payload),
            idempotencyKey = idempotencyKeyFor("SANDBOX_WASM", expectedSha256, export, inputValue?.toString() ?: ""),
        )

        val response = transport.submit(request)
        if (!response.succeeded) return SandboxJobOutcome.Failed(response.errorCode ?: "TRANSPORT_FAILURE")

        val resultPayload = response.resultJson?.let {
            runCatching { json.decodeFromString<WasmJobResultPayload>(it) }.getOrNull()
        } ?: return SandboxJobOutcome.Failed("MALFORMED_RESULT")

        if (!resultPayload.succeeded) return SandboxJobOutcome.Failed(resultPayload.errorCode ?: "SANDBOX_FAILURE")

        val proposed = resultPayload.proposedPlan ?: return SandboxJobOutcome.ComputedOnly(resultPayload.result)
        return submitProposedPlan(proposed, resultPayload.result, request.jobId, context)
    }

    private suspend fun submitProposedPlan(
        proposed: ProposedPlanPayload,
        computedResult: Double?,
        planId: String,
        context: AdapterExecutionContext,
    ): SandboxJobOutcome {
        if (proposed.steps.isEmpty()) return SandboxJobOutcome.PlanRefused("proposed plan has no steps")

        val resolvedSteps = mutableListOf<PlanStep>()
        proposed.steps.forEachIndexed { index, stepPayload ->
            val action = runCatching { NuaActionType.valueOf(stepPayload.action) }.getOrNull()
                ?: return SandboxJobOutcome.PlanRefused("step $index names an unknown action: \"${stepPayload.action}\"")
            if (capabilityRegistry.forAction(action) == null) {
                return SandboxJobOutcome.PlanRefused("step $index's action $action has no registered capability")
            }
            resolvedSteps += PlanStep(
                id = "sandbox-step-$index",
                action = action,
                parameters = stepPayload.parameters,
                dependsOn = if (index == 0) emptyList() else listOf("sandbox-step-${index - 1}"),
                authorizationProof = AuthorizationProof.NotRequired,
            )
        }

        val plan = ActionPlan(id = planId, steps = resolvedSteps)
        workflowExecutor.run(plan, context)
        return SandboxJobOutcome.PlanSubmitted(computedResult, planId, resolvedSteps.size)
    }
}
