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
import com.nua.assistant.automation.uaf.ExecutionAdapterType
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.PlanStep
import com.nua.assistant.automation.uaf.StepOutcomeState
import com.nua.assistant.automation.uaf.WorkflowExecutor
import com.nua.assistant.memory.AutonomyContractEntity
import com.nua.assistant.trust.ContractDecision
import com.nua.assistant.trust.evaluateContract
import com.nua.assistant.trust.finalAutoApproveDecision
import com.nua.assistant.trust.lineage.LineageEntry
import com.nua.assistant.trust.lineage.LineageRecorder
import com.nua.assistant.voice.NuaLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val CONTEXT = AdapterExecutionContext(originalUtterance = "adversarial probe", pinnedLanguage = null)
private const val NOW = 1_000_000L

private class ContractRecordingSkill(override val manifest: SkillManifest) : NuaSkill {
    var called = false
    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        called = true
        return NuaRouteResult.ActionTaken("sent")
    }
}

private class ContractNoOpLineageRecorder : LineageRecorder {
    override suspend fun record(entry: LineageEntry) {}
}

/**
 * Reproduces exactly what `recipes/RecipeRepository.kt`'s proofFor/`ui/NuaViewModel.kt`'s
 * applyPendingEffect do with a contract's decision: construct
 * [AuthorizationProof.UserConfirmed] only when [finalAutoApproveDecision] says so, never
 * otherwise — proving the one path from "a contract exists" to "a real adapter runs" can't
 * be shortened.
 */
private fun proofFrom(legacyGrantActive: Boolean, contract: AutonomyContractEntity?, decision: ContractDecision): AuthorizationProof =
    if (finalAutoApproveDecision(legacyGrantActive, contract, decision)) AuthorizationProof.UserConfirmed(NOW) else AuthorizationProof.NotRequired

/**
 * Guardian Lab: Contextual Autonomy Contracts (Feature 5) adversarial coverage — the
 * directive's own named "autonomy-contract-bypass" scenario class, deferred at the
 * Guardian Lab baseline round pending Feature 5 existing to test against. Every case
 * composes the real [evaluateContract]/[finalAutoApproveDecision] pure functions with a
 * real [WorkflowExecutor], proving the contract's decision can only ever reach an adapter
 * through a genuine [AuthorizationProof.UserConfirmed] — never a shortcut.
 */
class AutonomyContractAdversarialTest {

    @Test
    fun `a shadow-mode contract's Permit decision can never cause a real adapter to run`() = runTest {
        val skill = ContractRecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.SMS_SEND to skill))
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                skill.called = true
                return NuaRouteResult.ActionTaken("this must never run under shadow mode")
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), ContractNoOpLineageRecorder())

        val shadowContract = AutonomyContractEntity(actionType = NuaActionType.SMS_SEND.name, shadowMode = true, expiresAt = NOW + 1000)
        val decision = evaluateContract(shadowContract, NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 0)
        assertEquals("the contract's own evaluation genuinely would permit -- shadow mode is the only thing standing in the way", ContractDecision.Permit, decision)

        val proof = proofFrom(legacyGrantActive = false, contract = shadowContract, decision = decision)
        val plan = ActionPlan("adversarial-shadow", listOf(PlanStep("a", NuaActionType.SMS_SEND, authorizationProof = proof, failurePolicy = FailurePolicy.SKIP)))
        val state = executor.run(plan, CONTEXT)

        assertTrue("a shadow contract's Permit must never reach a real adapter", !skill.called)
        assertEquals(StepOutcomeState.AUTHORIZATION_REFUSED, state.outcomes["a"]?.state)
    }

    @Test
    fun `an expired contract still marked active can never authorize execution`() = runTest {
        val skill = ContractRecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.SMS_SEND to skill))
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                skill.called = true
                return NuaRouteResult.ActionTaken("this must never run for an expired contract")
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), ContractNoOpLineageRecorder())

        // The exact RuntimeSafetySentinel "stale row" anomaly (active=true, expiresAt in
        // the past) -- a database inconsistency the drift-suspend path should have
        // prevented, but this proves it's harmless even if it somehow occurs.
        val staleContract = AutonomyContractEntity(actionType = NuaActionType.SMS_SEND.name, active = true, expiresAt = NOW - 1)
        val decision = evaluateContract(staleContract, NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 0)
        val proof = proofFrom(legacyGrantActive = false, contract = staleContract, decision = decision)

        val plan = ActionPlan("adversarial-stale", listOf(PlanStep("a", NuaActionType.SMS_SEND, authorizationProof = proof, failurePolicy = FailurePolicy.SKIP)))
        val state = executor.run(plan, CONTEXT)

        assertTrue("a stale-expired contract must never authorize execution", !skill.called)
        assertEquals(StepOutcomeState.AUTHORIZATION_REFUSED, state.outcomes["a"]?.state)
    }

    @Test
    fun `a contract scoped to a different recipient can never authorize this one`() = runTest {
        val skill = ContractRecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.SMS_SEND to skill))
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                skill.called = true
                return NuaRouteResult.ActionTaken("this must never run for the wrong recipient")
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), ContractNoOpLineageRecorder())

        val scopedContract = AutonomyContractEntity(actionType = NuaActionType.SMS_SEND.name, recipient = "+15551234567", expiresAt = NOW + 1000)
        // The recipe/proposal being authorized is actually for a *different* number.
        val decision = evaluateContract(scopedContract, NuaActionType.SMS_SEND, recipient = "+19998887777", now = NOW, recentCommittedCountInWindow = 0)
        val proof = proofFrom(legacyGrantActive = false, contract = scopedContract, decision = decision)

        val plan = ActionPlan("adversarial-recipient", listOf(PlanStep("a", NuaActionType.SMS_SEND, authorizationProof = proof, failurePolicy = FailurePolicy.SKIP)))
        val state = executor.run(plan, CONTEXT)

        assertTrue("a recipient-scoped contract must never authorize a different recipient", !skill.called)
        assertEquals(StepOutcomeState.AUTHORIZATION_REFUSED, state.outcomes["a"]?.state)
    }

    @Test
    fun `a live, correctly-scoped, non-shadow contract does authorize execution -- the control case`() = runTest {
        // Proves the above three tests fail for the *specific* reason claimed, not because
        // this whole path is broken and nothing would ever execute regardless.
        val skill = ContractRecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.SMS_SEND to skill))
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                skill.called = true
                return NuaRouteResult.ActionTaken("sent")
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), ContractNoOpLineageRecorder())

        val liveContract = AutonomyContractEntity(actionType = NuaActionType.SMS_SEND.name, expiresAt = NOW + 1000)
        val decision = evaluateContract(liveContract, NuaActionType.SMS_SEND, recipient = null, now = NOW, recentCommittedCountInWindow = 0)
        val proof = proofFrom(legacyGrantActive = false, contract = liveContract, decision = decision)

        val plan = ActionPlan("adversarial-control", listOf(PlanStep("a", NuaActionType.SMS_SEND, authorizationProof = proof, failurePolicy = FailurePolicy.SKIP)))
        val state = executor.run(plan, CONTEXT)

        assertTrue("a genuinely live, unscoped, non-shadow contract must authorize normally", skill.called)
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["a"]?.state)
    }
}
