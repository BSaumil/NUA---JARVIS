package com.nua.assistant.recipes

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.uaf.AuthorizationProof
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.ConfirmationPolicy
import com.nua.assistant.automation.uaf.ExecutionAdapterType
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.IdempotencyPolicy
import com.nua.assistant.automation.uaf.Reversibility
import com.nua.assistant.automation.uaf.SideEffectClass
import com.nua.assistant.trust.AutonomyTier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun descriptor(action: NuaActionType, confirmation: ConfirmationPolicy) = CapabilityDescriptor(
    action = action,
    purpose = "test",
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

private val REGISTRY = mapOf(
    NuaActionType.OPEN_APP to descriptor(NuaActionType.OPEN_APP, ConfirmationPolicy.NONE_REQUIRED),
    NuaActionType.GET_WEATHER to descriptor(NuaActionType.GET_WEATHER, ConfirmationPolicy.NONE_REQUIRED),
    NuaActionType.SMS_SEND to descriptor(NuaActionType.SMS_SEND, ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE),
)

private fun registryLookup(action: NuaActionType): CapabilityDescriptor? = REGISTRY[action]

class RecipeCompilerTest {

    @Test
    fun `splitIntoClauses splits on commas, semicolons, and and-then, trimming whitespace`() {
        assertEquals(
            listOf("open spotify", "check the weather", "read notifications"),
            splitIntoClauses("open spotify, check the weather and read notifications"),
        )
        assertEquals(listOf("a", "b", "c"), splitIntoClauses("a; b then c"))
    }

    @Test
    fun `splitIntoClauses drops empty clauses from stray separators`() {
        assertEquals(listOf("open spotify", "check weather"), splitIntoClauses("open spotify, , check weather"))
    }

    @Test
    fun `splitIntoClauses on a single clause returns exactly one`() {
        assertEquals(listOf("check the weather"), splitIntoClauses("check the weather"))
    }

    @Test
    fun `a resolvable clause with no confirmation requirement compiles to a SKIP step`() = runTest {
        val compiled = compileRecipe("check the weather", ::registryLookup)
        assertEquals(1, compiled.steps.size)
        assertTrue(compiled.unresolvedClauses.isEmpty())
        assertEquals(NuaActionType.GET_WEATHER, compiled.steps[0].action)
        assertEquals(FailurePolicy.SKIP, compiled.steps[0].failurePolicy)
    }

    @Test
    fun `failurePolicyFor assigns ASK_USER when the capability requires confirmation, SKIP otherwise`() {
        // KeywordIntentMatcher never resolves a confirmation-required action today (every
        // keyword-matchable action is T0/T1, NONE_REQUIRED) -- so this rule is exercised
        // directly against failurePolicyFor rather than through compileRecipe end to end.
        assertEquals(FailurePolicy.ASK_USER, failurePolicyFor(descriptor(NuaActionType.SMS_SEND, ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE)))
        assertEquals(FailurePolicy.SKIP, failurePolicyFor(descriptor(NuaActionType.OPEN_APP, ConfirmationPolicy.NONE_REQUIRED)))
    }

    @Test
    fun `a clause KeywordIntentMatcher can't resolve is reported, never silently dropped`() = runTest {
        val compiled = compileRecipe("do something wildly ambiguous nobody could parse", ::registryLookup)
        assertTrue(compiled.steps.isEmpty())
        assertEquals(1, compiled.unresolvedClauses.size)
        assertEquals("do something wildly ambiguous nobody could parse", compiled.unresolvedClauses[0].text)
    }

    @Test
    fun `a clause that matches a keyword but has no registered capability is reported as unresolved, never fabricated`() = runTest {
        // "brief me" keyword-matches MORNING_BRIEFING, which this test registry never registers.
        val compiled = compileRecipe("brief me", ::registryLookup)
        assertTrue(compiled.steps.isEmpty())
        assertEquals(1, compiled.unresolvedClauses.size)
    }

    @Test
    fun `a mixed recipe resolves what it can and reports the rest, in original clause order`() = runTest {
        val compiled = compileRecipe("check the weather, do something ambiguous, open spotify", ::registryLookup)
        assertEquals(2, compiled.steps.size)
        assertEquals(1, compiled.unresolvedClauses.size)
        assertEquals("do something ambiguous", compiled.unresolvedClauses[0].text)
    }

    @Test
    fun `step ids reflect the clause's original position, so gaps from unresolved clauses are visible`() = runTest {
        val compiled = compileRecipe("check the weather, do something ambiguous, open spotify", ::registryLookup)
        assertEquals("step-0", compiled.steps[0].id)
        assertEquals("step-2", compiled.steps[1].id)
    }

    @Test
    fun `compileRecipe never assigns authorization at compile time`() = runTest {
        val compiled = compileRecipe("check the weather", ::registryLookup)
        assertEquals(AuthorizationProof.NotRequired, compiled.steps[0].authorizationProof)
    }

    @Test
    fun `a clause introduced by then depends on the previous step -- and and commas stay independent`() = runTest {
        val compiled = compileRecipe("check the weather then open spotify", ::registryLookup)
        assertEquals(2, compiled.steps.size)
        assertEquals(emptyList<String>(), compiled.steps[0].dependsOn)
        assertEquals(listOf("step-0"), compiled.steps[1].dependsOn)

        val independent = compileRecipe("check the weather and open spotify", ::registryLookup)
        assertEquals(emptyList<String>(), independent.steps[0].dependsOn)
        assertEquals(emptyList<String>(), independent.steps[1].dependsOn)
    }

    @Test
    fun `a then immediately after an unresolved clause has nothing to depend on, so it stays independent`() = runTest {
        val compiled = compileRecipe("do something ambiguous then open spotify", ::registryLookup)
        assertEquals(1, compiled.unresolvedClauses.size)
        assertEquals(1, compiled.steps.size)
        assertEquals(emptyList<String>(), compiled.steps[0].dependsOn)
    }

    @Test
    fun `a clause KeywordIntentMatcher misses is resolved through the LLM fallback instead of being reported unresolved`() = runTest {
        val compiled = compileRecipe(
            description = "do something wildly ambiguous nobody could parse",
            descriptorFor = ::registryLookup,
            llmFallback = { ClassifiedIntent(action = NuaActionType.OPEN_APP, confidence = 0.9, parameters = mapOf("app" to "spotify")) },
        )
        assertTrue(compiled.unresolvedClauses.isEmpty())
        assertEquals(1, compiled.steps.size)
        assertEquals(NuaActionType.OPEN_APP, compiled.steps[0].action)
    }

    @Test
    fun `the LLM fallback is never consulted for a clause KeywordIntentMatcher already resolved`() = runTest {
        var fallbackCalls = 0
        compileRecipe(
            description = "check the weather",
            descriptorFor = ::registryLookup,
            llmFallback = { fallbackCalls++; null },
        )
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `an LLM fallback result naming CHAT or an unregistered action is still reported unresolved, never fabricated`() = runTest {
        val chatResult = compileRecipe(
            description = "do something wildly ambiguous nobody could parse",
            descriptorFor = ::registryLookup,
            llmFallback = { ClassifiedIntent(action = NuaActionType.CHAT, confidence = 0.9) },
        )
        assertEquals(1, chatResult.unresolvedClauses.size)

        val unregisteredResult = compileRecipe(
            description = "do something wildly ambiguous nobody could parse",
            descriptorFor = ::registryLookup,
            // MORNING_BRIEFING keyword-matches but this test registry never registers it.
            llmFallback = { ClassifiedIntent(action = NuaActionType.MORNING_BRIEFING, confidence = 0.9) },
        )
        assertEquals(1, unregisteredResult.unresolvedClauses.size)
    }
}
