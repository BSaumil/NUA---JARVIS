package com.nua.assistant.automation.uaf

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.automation.NuaSkill
import com.nua.assistant.automation.SkillManifest
import com.nua.assistant.trust.lineage.LineageEntry
import com.nua.assistant.trust.lineage.LineageRecorder
import com.nua.assistant.voice.NuaLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val CONTEXT = AdapterExecutionContext(originalUtterance = "do the thing", pinnedLanguage = null)

private class FakeSkill(override val manifest: SkillManifest = SkillManifest()) : NuaSkill {
    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult =
        NuaRouteResult.ActionTaken("skill ran")
}

/** A registry over a fixed, small set of action types — real [CapabilityRegistry], fake skill map, same shape production DI assembles. */
private fun registryOf(vararg actions: NuaActionType) =
    CapabilityRegistry(actions.associateWith { FakeSkill() })

/** A scriptable adapter: records every call and returns whatever [results] says for that step id. */
private class ScriptedAdapter(
    override val type: ExecutionAdapterType,
    private val results: MutableMap<String, NuaRouteResult> = mutableMapOf(),
) : ActionAdapter {
    val calls = mutableListOf<String>()
    fun onStep(stepId: String, result: NuaRouteResult) {
        results[stepId] = result
    }
    override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
        val stepId = parameters["__stepId"] ?: error("test steps must carry __stepId")
        calls += stepId
        return results[stepId] ?: NuaRouteResult.ActionTaken("default ok")
    }
}

/** Records every entry handed to it, in order — no Room, so [WorkflowExecutorTest] can assert lineage without touching the database. */
private class FakeLineageRecorder : LineageRecorder {
    val entries = mutableListOf<LineageEntry>()
    override suspend fun record(entry: LineageEntry) {
        entries += entry
    }
}

private fun step(
    id: String,
    action: NuaActionType = NuaActionType.GET_WEATHER,
    dependsOn: List<String> = emptyList(),
    failurePolicy: FailurePolicy = FailurePolicy.STOP,
    compensationStepId: String? = null,
    authorizationProof: AuthorizationProof = AuthorizationProof.NotRequired,
) = PlanStep(id, action, parameters = mapOf("__stepId" to id), dependsOn = dependsOn, failurePolicy = failurePolicy, compensationStepId = compensationStepId, authorizationProof = authorizationProof)

/**
 * Exercises the real coroutine orchestration of [WorkflowExecutor.run] against fakes —
 * same discipline [SkillSandboxTest] already applies to [executeSandboxed]: the pure
 * decision functions in ActionPlan.kt are tested directly in [ActionPlanTest], this file
 * proves the *engine* that calls them wires authorization, adapter selection, compensation,
 * resume, and Flight Recorder lineage together correctly.
 */
class WorkflowExecutorTest {

    @Test
    fun `a two-step plan runs both steps to SUCCEEDED in dependency order`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        val executor = WorkflowExecutor(registryOf(NuaActionType.GET_WEATHER), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), FakeLineageRecorder())
        val plan = ActionPlan("p1", listOf(step("a"), step("b", dependsOn = listOf("a"))))

        val state = executor.run(plan, CONTEXT)

        assertEquals(listOf("a", "b"), local.calls)
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["a"]?.state)
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["b"]?.state)
        assertTrue(isPlanComplete(plan, state))
    }

    @Test
    fun `a plan can use two different adapter types for two different steps`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        val notification = ScriptedAdapter(ExecutionAdapterType.NOTIFICATION_REMOTE_INPUT)
        val registry = registryOf(NuaActionType.GET_WEATHER, NuaActionType.REPLY_TO_NOTIFICATION)
        val executor = WorkflowExecutor(
            registry,
            mapOf(ExecutionAdapterType.LOCAL_NATIVE to local, ExecutionAdapterType.NOTIFICATION_REMOTE_INPUT to notification),
            FakeLineageRecorder(),
        )
        val plan = ActionPlan(
            "p1",
            listOf(
                step("a", action = NuaActionType.GET_WEATHER),
                step(
                    "b",
                    action = NuaActionType.REPLY_TO_NOTIFICATION,
                    dependsOn = listOf("a"),
                    authorizationProof = AuthorizationProof.UserConfirmed(1L),
                ),
            ),
        )

        val state = executor.run(plan, CONTEXT)

        assertEquals(listOf("a"), local.calls)
        assertEquals(listOf("b"), notification.calls)
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["a"]?.state)
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["b"]?.state)
    }

    @Test
    fun `a CONFIRM_BEFORE_EXECUTE step with no authorization proof is refused, never reaching an adapter`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        val executor = WorkflowExecutor(registryOf(NuaActionType.SMS_SEND), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), FakeLineageRecorder())
        val plan = ActionPlan("p1", listOf(step("a", action = NuaActionType.SMS_SEND)))

        val state = executor.run(plan, CONTEXT)

        assertTrue("an unauthorized sensitive step must never reach an adapter", local.calls.isEmpty())
        assertEquals(StepOutcomeState.AUTHORIZATION_REFUSED, state.outcomes["a"]?.state)
    }

    @Test
    fun `STOP halts the whole plan -- a later independent step never runs`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        local.onStep("a", NuaRouteResult.ActionTaken("nope", succeeded = false))
        val executor = WorkflowExecutor(registryOf(NuaActionType.GET_WEATHER), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), FakeLineageRecorder())
        val plan = ActionPlan("p1", listOf(step("a", failurePolicy = FailurePolicy.STOP), step("b")))

        val state = executor.run(plan, CONTEXT)

        assertEquals(StepOutcomeState.FAILED, state.outcomes["a"]?.state)
        assertNull("an independent step must not run once STOP halts the plan", state.outcomes["b"])
        assertTrue("b" !in local.calls)
    }

    @Test
    fun `SKIP lets an independent later step run, but a dependent of the failed step stays unattempted`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        local.onStep("a", NuaRouteResult.ActionTaken("nope", succeeded = false))
        val executor = WorkflowExecutor(registryOf(NuaActionType.GET_WEATHER), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), FakeLineageRecorder())
        val plan = ActionPlan(
            "p1",
            listOf(step("a", failurePolicy = FailurePolicy.SKIP), step("independent"), step("dependent", dependsOn = listOf("a"))),
        )

        val state = executor.run(plan, CONTEXT)

        assertEquals(StepOutcomeState.FAILED, state.outcomes["a"]?.state)
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["independent"]?.state)
        assertNull("a step depending on a failed one must never run against it", state.outcomes["dependent"])
    }

    @Test
    fun `ASK_USER pauses the run rather than failing it, and resuming with proof continues past it`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        val registry = registryOf(NuaActionType.SMS_SEND, NuaActionType.GET_WEATHER)
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), FakeLineageRecorder())
        val plan = ActionPlan(
            "p1",
            listOf(step("a", action = NuaActionType.SMS_SEND, failurePolicy = FailurePolicy.ASK_USER), step("b", action = NuaActionType.GET_WEATHER, dependsOn = listOf("a"))),
        )

        val pausedState = executor.run(plan, CONTEXT)
        assertEquals(StepOutcomeState.AWAITING_USER, pausedState.outcomes["a"]?.state)
        assertTrue(local.calls.isEmpty())
        assertTrue(isPlanAwaitingUser(plan, pausedState))

        // The caller re-supplies the same plan with step "a" now carrying real proof --
        // this is the resume path: run() recomputes from a fresh PlanRunState with no
        // memory of the paused attempt, exactly what a process-death restart would hand it.
        val resumedPlan = plan.copy(steps = plan.steps.map { if (it.id == "a") it.copy(authorizationProof = AuthorizationProof.UserConfirmed(1L)) else it })
        val finalState = executor.run(resumedPlan, CONTEXT, initialState = PlanRunState("p1"))

        assertEquals(StepOutcomeState.SUCCEEDED, finalState.outcomes["a"]?.state)
        assertEquals(StepOutcomeState.SUCCEEDED, finalState.outcomes["b"]?.state)
    }

    @Test
    fun `resuming from a partially-completed checkpoint never re-runs an already-succeeded step`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        val executor = WorkflowExecutor(registryOf(NuaActionType.GET_WEATHER), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), FakeLineageRecorder())
        val plan = ActionPlan("p1", listOf(step("a"), step("b", dependsOn = listOf("a"))))

        val checkpoint = PlanRunState("p1").withOutcome(StepOutcome("a", StepOutcomeState.SUCCEEDED))
        val state = executor.run(plan, CONTEXT, initialState = checkpoint)

        assertEquals(listOf("b"), local.calls)
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["a"]?.state)
        assertEquals(StepOutcomeState.SUCCEEDED, state.outcomes["b"]?.state)
    }

    @Test
    fun `COMPENSATE runs the named compensation step once, records COMPENSATED, and still halts the plan`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        local.onStep("a", NuaRouteResult.ActionTaken("nope", succeeded = false))
        val executor = WorkflowExecutor(registryOf(NuaActionType.GET_WEATHER), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), FakeLineageRecorder())
        val plan = ActionPlan(
            "p1",
            listOf(
                step("a", failurePolicy = FailurePolicy.COMPENSATE, compensationStepId = "undo-a"),
                step("undo-a"),
                step("later"),
            ),
        )

        val state = executor.run(plan, CONTEXT)

        assertEquals(listOf("a", "undo-a"), local.calls)
        assertEquals(StepOutcomeState.FAILED, state.outcomes["a"]?.state)
        assertEquals(StepOutcomeState.COMPENSATED, state.outcomes["undo-a"]?.state)
        assertNull("the plan must halt after compensating, never continuing to an unrelated later step", state.outcomes["later"])
    }

    @Test
    fun `a cyclic plan is rejected before any step runs`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        val executor = WorkflowExecutor(registryOf(NuaActionType.GET_WEATHER), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), FakeLineageRecorder())
        val plan = ActionPlan("p1", listOf(step("a", dependsOn = listOf("b")), step("b", dependsOn = listOf("a"))))

        try {
            executor.run(plan, CONTEXT)
            org.junit.Assert.fail("a plan with a dependency cycle must never execute")
        } catch (expected: IllegalStateException) {
            // expected
        }
        assertTrue(local.calls.isEmpty())
    }

    @Test
    fun `a step whose action has no registered capability fails cleanly, never crashing the run`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        val executor = WorkflowExecutor(registryOf(), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), FakeLineageRecorder())
        val plan = ActionPlan("p1", listOf(step("a")))

        val state = executor.run(plan, CONTEXT)

        assertEquals(StepOutcomeState.FAILED, state.outcomes["a"]?.state)
        assertTrue(local.calls.isEmpty())
    }

    @Test
    fun `every step -- including a compensation step -- is recorded to the Flight Recorder with a matching outcome`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        local.onStep("a", NuaRouteResult.ActionTaken("nope", succeeded = false))
        val lineage = FakeLineageRecorder()
        val executor = WorkflowExecutor(registryOf(NuaActionType.GET_WEATHER), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), lineage)
        val plan = ActionPlan("run-42", listOf(step("a", failurePolicy = FailurePolicy.COMPENSATE, compensationStepId = "undo-a"), step("undo-a")))

        executor.run(plan, CONTEXT)

        assertEquals(2, lineage.entries.size)
        assertEquals("run-42", lineage.entries[0].runId)
        assertEquals("a", lineage.entries[0].stepId)
        assertEquals(StepOutcomeState.FAILED.name, lineage.entries[0].outcomeState)
        assertEquals("undo-a", lineage.entries[1].stepId)
        assertEquals(
            "a step recorded as the plan's official outcome (COMPENSATED) must be recorded identically in its own lineage entry",
            StepOutcomeState.COMPENSATED.name,
            lineage.entries[1].outcomeState,
        )
    }

    @Test
    fun `a step refused for missing authorization is still recorded to the Flight Recorder`() = runTest {
        val local = ScriptedAdapter(ExecutionAdapterType.LOCAL_NATIVE)
        val lineage = FakeLineageRecorder()
        val executor = WorkflowExecutor(registryOf(NuaActionType.SMS_SEND), mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), lineage)
        val plan = ActionPlan("p1", listOf(step("a", action = NuaActionType.SMS_SEND)))

        executor.run(plan, CONTEXT)

        assertEquals(1, lineage.entries.size)
        assertEquals(StepOutcomeState.AUTHORIZATION_REFUSED.name, lineage.entries[0].outcomeState)
        assertEquals("NotRequired", lineage.entries[0].authorizationKind)
    }
}
