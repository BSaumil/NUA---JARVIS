package com.nua.assistant.recipes

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.uaf.AuthorizationProof
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.PlanStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeStepDataTest {

    @Test
    fun `a compiled step round-trips through its persisted form unchanged, except authorization`() {
        val step = PlanStep(
            id = "step-0",
            action = NuaActionType.OPEN_APP,
            parameters = mapOf("app" to "spotify"),
            failurePolicy = FailurePolicy.SKIP,
            authorizationProof = AuthorizationProof.NotRequired,
        )
        val restored = step.toData().toPlanStep()
        assertEquals(step, restored)
    }

    @Test
    fun `a step's persisted authorization is never carried over -- restoring always yields NotRequired`() {
        // Compile-time steps never carry a real proof anyway (see RecipeCompiler.kt), but
        // this pins the restore path itself: RecipeStepData has no field for it at all.
        val restored = RecipeStepData(id = "step-0", actionName = "OPEN_APP", failurePolicyName = "SKIP").toPlanStep()
        assertEquals(AuthorizationProof.NotRequired, restored?.authorizationProof)
    }

    @Test
    fun `toPlanStep returns null, never a guess, for an actionName that names no real NuaActionType`() {
        val data = RecipeStepData(id = "step-0", actionName = "NOT_A_REAL_ACTION", failurePolicyName = "SKIP")
        assertNull(data.toPlanStep())
    }

    @Test
    fun `toPlanStep returns null, never a guess, for a failurePolicyName that names no real FailurePolicy`() {
        val data = RecipeStepData(id = "step-0", actionName = "OPEN_APP", failurePolicyName = "NOT_A_REAL_POLICY")
        assertNull(data.toPlanStep())
    }

    @Test
    fun `parameters round-trip exactly, including an empty map`() {
        val step = PlanStep(id = "step-0", action = NuaActionType.GET_WEATHER, parameters = emptyMap(), failurePolicy = FailurePolicy.SKIP)
        assertEquals(emptyMap<String, String>(), step.toData().toPlanStep()?.parameters)
    }
}
