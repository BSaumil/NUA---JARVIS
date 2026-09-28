package com.nua.assistant.recipes

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.uaf.AuthorizationProof
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.ConfirmationPolicy
import com.nua.assistant.automation.uaf.ExecutionAdapterType
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.IdempotencyPolicy
import com.nua.assistant.automation.uaf.PlanStep
import com.nua.assistant.automation.uaf.Reversibility
import com.nua.assistant.automation.uaf.SideEffectClass
import com.nua.assistant.trust.AutonomyTier
import org.junit.Assert.assertEquals
import org.junit.Test

private fun descriptor(action: NuaActionType, confirmation: ConfirmationPolicy) = CapabilityDescriptor(
    action = action,
    purpose = "purpose-$action",
    parameters = emptyList(),
    riskTier = AutonomyTier.T1,
    permissions = emptyList(),
    sideEffect = SideEffectClass.NONE,
    reversibility = Reversibility.REVERSIBLE,
    idempotency = IdempotencyPolicy.SAFE_TO_REPEAT,
    confirmation = confirmation,
    timeoutMillis = 5000,
    preferredAdapter = ExecutionAdapterType.LOCAL_NATIVE,
)

private fun step(action: NuaActionType) =
    PlanStep(id = "step-0", action = action, authorizationProof = AuthorizationProof.NotRequired, failurePolicy = FailurePolicy.SKIP)

class RecipeSimulatorTest {

    @Test
    fun `a step with no confirmation requirement would execute regardless of any autonomy grant`() {
        val recipe = CompiledRecipe(listOf(step(NuaActionType.GET_WEATHER)), emptyList())
        val sim = simulateRecipe(recipe, { descriptor(it, ConfirmationPolicy.NONE_REQUIRED) }, { false })
        assertEquals(SimulatedStepStatus.WOULD_EXECUTE, sim.steps[0].status)
    }

    @Test
    fun `a confirmation-required step with a live autonomy grant would execute`() {
        val recipe = CompiledRecipe(listOf(step(NuaActionType.SMS_SEND)), emptyList())
        val sim = simulateRecipe(recipe, { descriptor(it, ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE) }, { true })
        assertEquals(SimulatedStepStatus.WOULD_EXECUTE, sim.steps[0].status)
    }

    @Test
    fun `a confirmation-required step with no autonomy grant would pause for the user, never silently run`() {
        val recipe = CompiledRecipe(listOf(step(NuaActionType.SMS_SEND)), emptyList())
        val sim = simulateRecipe(recipe, { descriptor(it, ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE) }, { false })
        assertEquals(SimulatedStepStatus.WOULD_AWAIT_USER, sim.steps[0].status)
    }

    @Test
    fun `a step with no registered capability is reported unresolved regardless of any autonomy grant`() {
        val recipe = CompiledRecipe(listOf(step(NuaActionType.SMS_SEND)), emptyList())
        val sim = simulateRecipe(recipe, { null }, { true })
        assertEquals(SimulatedStepStatus.UNRESOLVED, sim.steps[0].status)
    }

    @Test
    fun `unresolved clauses from compilation pass through the simulation unchanged`() {
        val recipe = CompiledRecipe(emptyList(), listOf(UnresolvedClause("do something ambiguous")))
        val sim = simulateRecipe(recipe, { null }, { false })
        assertEquals(listOf(UnresolvedClause("do something ambiguous")), sim.unresolvedClauses)
    }

    @Test
    fun `simulateRecipe is pure -- the same inputs always produce the same result`() {
        val recipe = CompiledRecipe(listOf(step(NuaActionType.SMS_SEND)), emptyList())
        val descriptorFor = { a: NuaActionType -> descriptor(a, ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE) }
        val autoApprove = { _: NuaActionType -> false }
        assertEquals(simulateRecipe(recipe, descriptorFor, autoApprove), simulateRecipe(recipe, descriptorFor, autoApprove))
    }
}
