package com.nua.assistant.automation.uaf

import com.nua.assistant.ai.NuaActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun step(
    id: String,
    dependsOn: List<String> = emptyList(),
    failurePolicy: FailurePolicy = FailurePolicy.STOP,
    compensationStepId: String? = null,
    authorizationProof: AuthorizationProof = AuthorizationProof.NotRequired,
) = PlanStep(id, NuaActionType.GET_WEATHER, dependsOn = dependsOn, failurePolicy = failurePolicy, compensationStepId = compensationStepId, authorizationProof = authorizationProof)

class ActionPlanTest {

    @Test
    fun `topologicalOrder resolves a real dependency chain`() {
        val plan = ActionPlan("p1", listOf(step("c", dependsOn = listOf("a", "b")), step("a"), step("b", dependsOn = listOf("a"))))
        val order = topologicalOrder(plan)
        assertTrue(order != null)
        val positions = order!!.map { it.id }
        assertTrue(positions.indexOf("a") < positions.indexOf("b"))
        assertTrue(positions.indexOf("b") < positions.indexOf("c"))
    }

    @Test
    fun `topologicalOrder returns null for a genuine cycle`() {
        val plan = ActionPlan("p1", listOf(step("a", dependsOn = listOf("b")), step("b", dependsOn = listOf("a"))))
        assertNull(topologicalOrder(plan))
    }

    @Test
    fun `nextRunnableStep offers only a step whose dependencies are already satisfied`() {
        val plan = ActionPlan("p1", listOf(step("a"), step("b", dependsOn = listOf("a"))))
        val fresh = PlanRunState("p1")
        assertEquals("a", nextRunnableStep(plan, fresh)?.id)

        val afterA = fresh.withOutcome(StepOutcome("a", StepOutcomeState.SUCCEEDED))
        assertEquals("b", nextRunnableStep(plan, afterA)?.id)
    }

    @Test
    fun `a SKIPPED dependency satisfies a dependent, same as SUCCEEDED`() {
        val plan = ActionPlan("p1", listOf(step("a"), step("b", dependsOn = listOf("a"))))
        val state = PlanRunState("p1").withOutcome(StepOutcome("a", StepOutcomeState.SKIPPED))
        assertEquals("b", nextRunnableStep(plan, state)?.id)
    }

    @Test
    fun `a FAILED dependency never satisfies a dependent`() {
        val plan = ActionPlan("p1", listOf(step("a"), step("b", dependsOn = listOf("a"))))
        val state = PlanRunState("p1").withOutcome(StepOutcome("a", StepOutcomeState.FAILED))
        assertNull(nextRunnableStep(plan, state))
    }

    @Test
    fun `nextRunnableStep returns null once every step has a terminal outcome`() {
        val plan = ActionPlan("p1", listOf(step("a")))
        val state = PlanRunState("p1").withOutcome(StepOutcome("a", StepOutcomeState.SUCCEEDED))
        assertNull(nextRunnableStep(plan, state))
        assertTrue(isPlanComplete(plan, state))
    }

    @Test
    fun `nextRunnableStep pauses the whole plan once any step is AWAITING_USER`() {
        val plan = ActionPlan("p1", listOf(step("a"), step("b")))
        val state = PlanRunState("p1").withOutcome(StepOutcome("a", StepOutcomeState.AWAITING_USER))
        assertNull("an independent, otherwise-eligible step must not run while another awaits the user", nextRunnableStep(plan, state))
        assertTrue(isPlanAwaitingUser(plan, state))
    }

    @Test
    fun `outcomeForFailedStep maps every FailurePolicy to its documented terminal state`() {
        assertEquals(StepOutcomeState.FAILED, outcomeForFailedStep(step("a", failurePolicy = FailurePolicy.STOP)))
        assertEquals(StepOutcomeState.FAILED, outcomeForFailedStep(step("a", failurePolicy = FailurePolicy.SKIP)))
        assertEquals(StepOutcomeState.FAILED, outcomeForFailedStep(step("a", failurePolicy = FailurePolicy.COMPENSATE)))
        assertEquals(StepOutcomeState.AWAITING_USER, outcomeForFailedStep(step("a", failurePolicy = FailurePolicy.ASK_USER)))
    }

    @Test
    fun `shouldStopPlanAfterFailure is true only for STOP`() {
        assertTrue(shouldStopPlanAfterFailure(step("a", failurePolicy = FailurePolicy.STOP)))
        assertTrue(!shouldStopPlanAfterFailure(step("a", failurePolicy = FailurePolicy.SKIP)))
        assertTrue(!shouldStopPlanAfterFailure(step("a", failurePolicy = FailurePolicy.COMPENSATE)))
        assertTrue(!shouldStopPlanAfterFailure(step("a", failurePolicy = FailurePolicy.ASK_USER)))
    }

    @Test
    fun `shouldCompensate requires both COMPENSATE policy and a named compensation step`() {
        assertTrue(shouldCompensate(step("a", failurePolicy = FailurePolicy.COMPENSATE, compensationStepId = "undo-a")))
        assertTrue("COMPENSATE with no named step has nothing to run", !shouldCompensate(step("a", failurePolicy = FailurePolicy.COMPENSATE, compensationStepId = null)))
        assertTrue(!shouldCompensate(step("a", failurePolicy = FailurePolicy.STOP, compensationStepId = "undo-a")))
    }

    @Test
    fun `withOutcome is additive, never dropping an earlier step's recorded outcome`() {
        val state = PlanRunState("p1")
            .withOutcome(StepOutcome("a", StepOutcomeState.SUCCEEDED))
            .withOutcome(StepOutcome("b", StepOutcomeState.FAILED))
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["a"]?.state)
        assertEquals(StepOutcomeState.FAILED, state.outcomes["b"]?.state)
    }

    @Test
    fun `isPlanComplete is false while any step is unattempted or awaiting the user`() {
        val plan = ActionPlan("p1", listOf(step("a"), step("b")))
        val onlyAAttempted = PlanRunState("p1").withOutcome(StepOutcome("a", StepOutcomeState.SUCCEEDED))
        assertTrue(!isPlanComplete(plan, onlyAAttempted))

        val bAwaiting = onlyAAttempted.withOutcome(StepOutcome("b", StepOutcomeState.AWAITING_USER))
        assertTrue(!isPlanComplete(plan, bAwaiting))
    }
}
