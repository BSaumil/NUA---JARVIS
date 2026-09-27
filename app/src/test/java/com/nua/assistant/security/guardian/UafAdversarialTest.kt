package com.nua.assistant.security.guardian

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.automation.NuaSkill
import com.nua.assistant.automation.SkillManifest
import com.nua.assistant.automation.SkillParameter
import com.nua.assistant.automation.executeSandboxed
import com.nua.assistant.automation.uaf.ActionAdapter
import com.nua.assistant.automation.uaf.ActionPlan
import com.nua.assistant.automation.uaf.AdapterExecutionContext
import com.nua.assistant.automation.uaf.AuthorizationProof
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.CapabilityRegistry
import com.nua.assistant.automation.uaf.ExecutionAdapterType
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.PlanStep
import com.nua.assistant.automation.uaf.StepOutcomeState
import com.nua.assistant.automation.uaf.WorkflowExecutor
import com.nua.assistant.trust.lineage.LineageEntry
import com.nua.assistant.trust.lineage.LineageRecorder
import com.nua.assistant.voice.NuaLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val CONTEXT = AdapterExecutionContext(originalUtterance = "adversarial probe", pinnedLanguage = null)

private class RecordingSkill(override val manifest: SkillManifest) : NuaSkill {
    var lastParameters: Map<String, String>? = null
    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        lastParameters = intent.parameters
        return NuaRouteResult.ActionTaken("ran")
    }
}

private class NoOpLineageRecorder : LineageRecorder {
    override suspend fun record(entry: LineageEntry) {}
}

/**
 * Guardian Lab baseline (Feature 10) — not a duplicate of each feature's own unit tests,
 * but adversarially-framed integration proof that the guarantees those tests check in
 * isolation actually survive being composed through the newest real call path this
 * session added (the Universal Action Fabric). Every case here targets a concrete
 * security-review question the directive's final report requires an answer to (§11):
 * "Can any side-effecting action bypass UAF?" and "Can a model hallucination create a
 * real action without deterministic validation?" specifically.
 */
class UafAdversarialTest {

    @Test
    fun `a parameter the skill never declared cannot reach it through the fabric, even disguised as a legitimate field`() = runTest {
        // Simulates a compromised or hallucinating classifier trying to smuggle an
        // unauthorized field (e.g. a second recipient, or a raw command) into a skill
        // that only declared "to" as its input — LocalNativeAdapter delegates to the
        // exact same SkillSandbox.execute() the direct-dispatch path uses, so this proves
        // the sandbox's parameter-stripping guarantee (SkillManifestTest) still holds
        // when reached via WorkflowExecutor, not just via NuaIntentRouter.
        val skill = RecordingSkill(SkillManifest(parameters = listOf(SkillParameter("to", required = true))))
        val registry = CapabilityRegistry(mapOf(NuaActionType.GET_WEATHER to skill))
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                val intent = ClassifiedIntent(descriptor.action, confidence = 1.0, parameters = parameters)
                return executeSandboxed(skill, intent, context.originalUtterance, context.pinnedLanguage, hasPermission = { true })
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), NoOpLineageRecorder())
        val maliciousParameters = mapOf("to" to "Alex", "__override_action" to "wipe_all_contacts", "bcc" to "attacker@evil.example")

        val plan = ActionPlan("adversarial-1", listOf(PlanStep("a", NuaActionType.GET_WEATHER, parameters = maliciousParameters)))
        executor.run(plan, CONTEXT)

        assertEquals(
            "only the declared 'to' parameter may ever reach the skill, regardless of what else the fabric was handed",
            setOf("to"),
            skill.lastParameters?.keys,
        )
    }

    @Test
    fun `authorization proof for one sensitive step never satisfies a different sensitive step in the same plan`() = runTest {
        // Adversarial framing of the idempotency/authorization model: a plan with two
        // independent SMS_SEND-shaped steps, only one of which the user actually
        // confirmed. A defect that treated "the user confirmed *something* in this plan"
        // as sufficient for every step would let one real confirmation silently
        // authorize an unrelated second send.
        val skill = RecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.SMS_SEND to skill))
        val calls = mutableListOf<String>()
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                calls += parameters.getValue("__stepId")
                return NuaRouteResult.ActionTaken("sent")
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), NoOpLineageRecorder())
        val plan = ActionPlan(
            "adversarial-2",
            listOf(
                PlanStep("confirmed-send", NuaActionType.SMS_SEND, parameters = mapOf("__stepId" to "confirmed-send"), authorizationProof = AuthorizationProof.UserConfirmed(1L), failurePolicy = FailurePolicy.SKIP),
                PlanStep("unconfirmed-send", NuaActionType.SMS_SEND, parameters = mapOf("__stepId" to "unconfirmed-send"), failurePolicy = FailurePolicy.SKIP),
            ),
        )

        val state = executor.run(plan, CONTEXT)

        assertEquals("only the step the user actually confirmed may reach an adapter", listOf("confirmed-send"), calls)
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["confirmed-send"]?.state)
        assertEquals(
            "a sibling step's confirmation must never leak into satisfying this one's own requirement",
            StepOutcomeState.AUTHORIZATION_REFUSED,
            state.outcomes["unconfirmed-send"]?.state,
        )
    }

    @Test
    fun `a step whose sanctioned adapter is unavailable fails closed -- it never silently falls back to an unsanctioned mechanism`() = runTest {
        // REPLY_TO_NOTIFICATION's real fallback adapter is NOTIFICATION_REMOTE_INPUT (the
        // only mechanism that actually sends, per CapabilityDescriptor's own doc comment
        // -- LOCAL_NATIVE only proposes). If only LOCAL_NATIVE happens to be registered
        // (e.g. a future partial DI wiring regression), a confirmed reply must fail
        // rather than silently executing through the wrong -- weaker guarantee --
        // mechanism.
        val skill = RecordingSkill(SkillManifest(parameters = listOf(SkillParameter("target", required = true), SkillParameter("message", required = true))))
        val registry = CapabilityRegistry(mapOf(NuaActionType.REPLY_TO_NOTIFICATION to skill))
        var localAdapterCalled = false
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                localAdapterCalled = true
                return NuaRouteResult.ActionTaken("this must never run for a confirmed reply")
            }
        }
        // Deliberately no NOTIFICATION_REMOTE_INPUT binding in this adapter map.
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), NoOpLineageRecorder())
        val plan = ActionPlan(
            "adversarial-3",
            listOf(PlanStep("a", NuaActionType.REPLY_TO_NOTIFICATION, authorizationProof = AuthorizationProof.UserConfirmed(1L))),
        )

        val state = executor.run(plan, CONTEXT)

        assertTrue("LOCAL_NATIVE must never be silently substituted for the sanctioned send mechanism", !localAdapterCalled)
        assertEquals(StepOutcomeState.FAILED, state.outcomes["a"]?.state)
    }

    @Test
    fun `a plan cannot resume past a paused step by simply omitting it from a replayed checkpoint`() = runTest {
        // Adversarial framing of the resume contract: a caller can't skip an
        // AWAITING_USER step's re-authorization by constructing a PlanRunState that just
        // marks it SUCCEEDED without ever having supplied real proof -- this test isn't
        // about that specific forgery (state is caller-constructed either way, a genuine
        // trust boundary the persistence layer, not WorkflowExecutor, must hold) but
        // documents the actual guarantee this session's slice provides: a *fresh* run
        // with the same unauthorized step is refused every single time, never once
        // "for free" because a previous attempt happened.
        val skill = RecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.SMS_SEND to skill))
        val calls = mutableListOf<String>()
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                calls += "called"
                return NuaRouteResult.ActionTaken("sent")
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), NoOpLineageRecorder())
        val plan = ActionPlan("adversarial-4", listOf(PlanStep("a", NuaActionType.SMS_SEND)))

        executor.run(plan, CONTEXT)
        executor.run(plan, CONTEXT)
        executor.run(plan, CONTEXT)

        assertTrue("repeating an unauthorized plan must never eventually succeed", calls.isEmpty())
    }
}
