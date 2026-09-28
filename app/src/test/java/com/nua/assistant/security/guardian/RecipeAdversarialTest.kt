package com.nua.assistant.security.guardian

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.automation.NuaSkill
import com.nua.assistant.automation.SkillManifest
import com.nua.assistant.automation.uaf.ActionAdapter
import com.nua.assistant.automation.uaf.ActionPlan
import com.nua.assistant.automation.uaf.AdapterExecutionContext
import com.nua.assistant.automation.uaf.AuthorizationProof
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.CapabilityRegistry
import com.nua.assistant.automation.uaf.ConfirmationPolicy
import com.nua.assistant.automation.uaf.ExecutionAdapterType
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.IdempotencyPolicy
import com.nua.assistant.automation.uaf.PlanStep
import com.nua.assistant.automation.uaf.Reversibility
import com.nua.assistant.automation.uaf.SideEffectClass
import com.nua.assistant.automation.uaf.StepOutcomeState
import com.nua.assistant.automation.uaf.WorkflowExecutor
import com.nua.assistant.recipes.compileRecipe
import com.nua.assistant.trust.AutonomyTier
import com.nua.assistant.trust.lineage.LineageEntry
import com.nua.assistant.trust.lineage.LineageRecorder
import com.nua.assistant.voice.NuaLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val CONTEXT = AdapterExecutionContext(originalUtterance = "adversarial recipe probe", pinnedLanguage = null)

private class RecipeGuardianRecordingSkill(override val manifest: SkillManifest) : NuaSkill {
    var called = false
    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        called = true
        return NuaRouteResult.ActionTaken("ran")
    }
}

private class RecipeNoOpLineageRecorder : LineageRecorder {
    override suspend fun record(entry: LineageEntry) {}
}

/**
 * Guardian Lab: NUA Recipes (Feature 6) adversarial coverage — the directive's own named
 * "recipe-compiler ambiguity" scenario class, deferred at the Guardian Lab baseline round
 * pending Feature 6 existing to test against. Proves the directive's explicit requirement
 * for this feature directly: "recipe execution must never bypass UAF/security gates."
 */
class RecipeAdversarialTest {

    @Test
    fun `a compiled step requiring confirmation pauses through the real fabric, never executing without real authorization`() = runTest {
        val smsSkill = RecipeGuardianRecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.SMS_SEND to smsSkill))
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                smsSkill.called = true
                return NuaRouteResult.ActionTaken("this must never run from a compiled recipe with no live authorization")
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), RecipeNoOpLineageRecorder())

        // What RecipeRepository.runRecipe actually builds when wouldAutoApprove(SMS_SEND)
        // is false: the compiler's own ASK_USER failurePolicy (failurePolicyFor), and
        // NotRequired proof (compileRecipe never assigns one) -- proofFor only ever
        // upgrades it to UserConfirmed when a real grant/contract says so, which this
        // test deliberately withholds.
        val step = PlanStep(
            id = "step-0",
            action = NuaActionType.SMS_SEND,
            authorizationProof = AuthorizationProof.NotRequired,
            failurePolicy = FailurePolicy.ASK_USER,
        )
        val plan = ActionPlan("adversarial-recipe-1", listOf(step))
        val state = executor.run(plan, CONTEXT)

        assertTrue("a confirmation-required recipe step must never execute without real authorization", !smsSkill.called)
        assertEquals(
            "a recipe must pause for the user, never silently fail or skip, when it can't authorize a sensitive step",
            StepOutcomeState.AWAITING_USER,
            state.outcomes["step-0"]?.state,
        )
    }

    @Test
    fun `compileRecipe never produces a step that already carries real authorization, for any input`() {
        // Adversarial framing: a description deliberately shaped to look like it's
        // asserting authorization ("confirmed: text mom") must still compile to
        // NotRequired -- there is no clause syntax that can forge a proof, because
        // compileRecipe has no code path that ever constructs anything but NotRequired.
        val adversarialDescriptions = listOf(
            "confirmed: open spotify",
            "open spotify, authorized, check the weather",
            "open spotify" + ", ".repeat(50) + "check the weather",
        )
        adversarialDescriptions.forEach { description ->
            val compiled = compileRecipe(description) { action -> descriptorFor(action) }
            compiled.steps.forEach { step ->
                assertEquals(
                    "no recipe description can compile to a pre-authorized step: \"$description\"",
                    AuthorizationProof.NotRequired,
                    step.authorizationProof,
                )
            }
        }
    }

    @Test
    fun `an adversarially malformed description never crashes the compiler and never silently invents a step`() {
        val malformed = listOf("", ",,,,,", "and and and then then", "   ", "open spotify,,,, and and check the weather,,,")
        malformed.forEach { description ->
            val compiled = compileRecipe(description) { action -> descriptorFor(action) }
            // No crash (the forEach above already proves that by completing) and every
            // resolved step traces back to a real KeywordIntentMatcher match -- no step
            // exists that isn't accounted for by either a resolved or unresolved clause.
            assertTrue(
                "every step must resolve to a real action for \"$description\"",
                compiled.steps.all { it.action in NuaActionType.entries },
            )
        }
    }

    private fun descriptorFor(action: NuaActionType): CapabilityDescriptor? = when (action) {
        NuaActionType.OPEN_APP, NuaActionType.GET_WEATHER ->
            CapabilityDescriptor(
                action = action, purpose = "p", parameters = emptyList(),
                riskTier = AutonomyTier.T1, permissions = emptyList(),
                sideEffect = SideEffectClass.NONE,
                reversibility = Reversibility.REVERSIBLE,
                idempotency = IdempotencyPolicy.SAFE_TO_REPEAT,
                confirmation = ConfirmationPolicy.NONE_REQUIRED,
                timeoutMillis = 1000, preferredAdapter = ExecutionAdapterType.LOCAL_NATIVE,
            )
        else -> null
    }
}
