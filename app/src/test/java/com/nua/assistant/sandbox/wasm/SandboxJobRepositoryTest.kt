package com.nua.assistant.sandbox.wasm

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.automation.NuaSkill
import com.nua.assistant.automation.SkillManifest
import com.nua.assistant.automation.uaf.ActionAdapter
import com.nua.assistant.automation.uaf.AdapterExecutionContext
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.CapabilityRegistry
import com.nua.assistant.automation.uaf.ExecutionAdapterType
import com.nua.assistant.automation.uaf.WorkflowExecutor
import com.nua.assistant.presence.protocol.ComputeJobRequest
import com.nua.assistant.presence.protocol.ComputeJobResult
import com.nua.assistant.trust.lineage.LineageEntry
import com.nua.assistant.trust.lineage.LineageRecorder
import com.nua.assistant.voice.NuaLanguage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val CONTEXT = AdapterExecutionContext(originalUtterance = "sandbox job", pinnedLanguage = null)
private val TEST_JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }

private class RecordingSkill(override val manifest: SkillManifest) : NuaSkill {
    var called = false
    override suspend fun execute(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        called = true
        return NuaRouteResult.ActionTaken("ran")
    }
}

private class NoOpLineageRecorder : LineageRecorder {
    override suspend fun record(entry: LineageEntry) {}
}

private class FakeSandboxTransport(private val result: ComputeJobResult) : SandboxTransport {
    var lastRequest: ComputeJobRequest? = null
    override suspend fun submit(request: ComputeJobRequest): ComputeJobResult {
        lastRequest = request
        return result
    }
}

private fun jobResult(payload: WasmJobResultPayload) = ComputeJobResult(
    jobId = "job-1",
    succeeded = true,
    resultJson = TEST_JSON.encodeToString(payload),
)

/**
 * OpenJarvis Final 10, workstream 7 — proves the one security-relevant boundary this
 * package exists to hold: a WASM-proposed plan can only ever reach already-governed
 * [CapabilityRegistry] actions, through the real [WorkflowExecutor], never a fabricated
 * or unregistered capability, and never bypassing that executor's own authorization gate.
 */
class SandboxJobRepositoryTest {

    @Test
    fun `a sandbox result with no proposed plan is reported as computed-only, never touching the executor`() = runTest {
        val openSkill = RecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.OPEN_APP to openSkill))
        val executor = WorkflowExecutor(registry, emptyMap(), NoOpLineageRecorder())
        val transport = FakeSandboxTransport(jobResult(WasmJobResultPayload(succeeded = true, result = 42.0)))
        val repository = SandboxJobRepository(transport, registry, executor, TEST_JSON)

        val outcome = repository.submit(program = byteArrayOf(0), expectedSha256 = "abc", context = CONTEXT)

        assertEquals(SandboxJobOutcome.ComputedOnly(42.0), outcome)
        assertTrue("no action must run when nothing was proposed", !openSkill.called)
    }

    @Test
    fun `a proposed plan naming a real, registered action actually runs through the real executor`() = runTest {
        val openSkill = RecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.OPEN_APP to openSkill))
        var adapterCalled = false
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                adapterCalled = true
                return NuaRouteResult.ActionTaken("opened")
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), NoOpLineageRecorder())
        val proposedPlan = ProposedPlanPayload(steps = listOf(ProposedStepPayload(action = "OPEN_APP", parameters = mapOf("app" to "spotify"))))
        val transport = FakeSandboxTransport(jobResult(WasmJobResultPayload(succeeded = true, proposedPlan = proposedPlan)))
        val repository = SandboxJobRepository(transport, registry, executor, TEST_JSON)

        val outcome = repository.submit(program = byteArrayOf(0), expectedSha256 = "abc", context = CONTEXT)

        assertTrue("a plan naming a real, registered, NONE_REQUIRED action must actually execute", adapterCalled)
        // The plan's id is the *request's own* generated id (its own idempotency/tracking
        // id), not the fake transport's canned response jobId -- so this reads it back
        // from what was actually sent, rather than guessing a fixed string.
        val sentJobId = transport.lastRequest?.jobId
        assertEquals(SandboxJobOutcome.PlanSubmitted(null, sentJobId!!, 1), outcome)
    }

    @Test
    fun `a proposed plan naming a confirm-required action pauses, never silently executes`() = runTest {
        val smsSkill = RecordingSkill(SkillManifest())
        val registry = CapabilityRegistry(mapOf(NuaActionType.SMS_SEND to smsSkill))
        var adapterCalled = false
        val local = object : ActionAdapter {
            override val type = ExecutionAdapterType.LOCAL_NATIVE
            override suspend fun execute(descriptor: CapabilityDescriptor, parameters: Map<String, String>, context: AdapterExecutionContext): NuaRouteResult {
                adapterCalled = true
                return NuaRouteResult.ActionTaken("this must never run from an unauthorized sandbox proposal")
            }
        }
        val executor = WorkflowExecutor(registry, mapOf(ExecutionAdapterType.LOCAL_NATIVE to local), NoOpLineageRecorder())
        val proposedPlan = ProposedPlanPayload(steps = listOf(ProposedStepPayload(action = "SMS_SEND", parameters = mapOf("phoneNumber" to "+10000000000", "message" to "hi"))))
        val transport = FakeSandboxTransport(jobResult(WasmJobResultPayload(succeeded = true, proposedPlan = proposedPlan)))
        val repository = SandboxJobRepository(transport, registry, executor, TEST_JSON)

        repository.submit(program = byteArrayOf(0), expectedSha256 = "abc", context = CONTEXT)

        assertTrue("SMS_SEND requires confirmation NUA never gave -- it must never run", !adapterCalled)
    }

    @Test
    fun `a proposed plan naming an action that isn't a real NuaActionType at all is refused wholesale`() = runTest {
        val registry = CapabilityRegistry(emptyMap())
        val executor = WorkflowExecutor(registry, emptyMap(), NoOpLineageRecorder())
        val proposedPlan = ProposedPlanPayload(steps = listOf(ProposedStepPayload(action = "DELETE_EVERYTHING", parameters = emptyMap())))
        val transport = FakeSandboxTransport(jobResult(WasmJobResultPayload(succeeded = true, proposedPlan = proposedPlan)))
        val repository = SandboxJobRepository(transport, registry, executor, TEST_JSON)

        val outcome = repository.submit(program = byteArrayOf(0), expectedSha256 = "abc", context = CONTEXT)

        assertTrue(outcome is SandboxJobOutcome.PlanRefused)
    }

    @Test
    fun `a proposed plan naming a real action with no registered capability is refused, never partially run`() = runTest {
        // CALENDAR_INVITE is a real NuaActionType, but this registry never registers it --
        // the whole plan must be refused, not silently skipped.
        val registry = CapabilityRegistry(emptyMap())
        val executor = WorkflowExecutor(registry, emptyMap(), NoOpLineageRecorder())
        val proposedPlan = ProposedPlanPayload(steps = listOf(ProposedStepPayload(action = "CALENDAR_INVITE")))
        val transport = FakeSandboxTransport(jobResult(WasmJobResultPayload(succeeded = true, proposedPlan = proposedPlan)))
        val repository = SandboxJobRepository(transport, registry, executor, TEST_JSON)

        val outcome = repository.submit(program = byteArrayOf(0), expectedSha256 = "abc", context = CONTEXT)

        assertTrue(outcome is SandboxJobOutcome.PlanRefused)
    }

    @Test
    fun `a transport-level failure is reported honestly, never as a successful computation`() = runTest {
        val registry = CapabilityRegistry(emptyMap())
        val executor = WorkflowExecutor(registry, emptyMap(), NoOpLineageRecorder())
        val transport = FakeSandboxTransport(ComputeJobResult(jobId = "job-1", succeeded = false, errorCode = "COMPANION_UNREACHABLE"))
        val repository = SandboxJobRepository(transport, registry, executor, TEST_JSON)

        val outcome = repository.submit(program = byteArrayOf(0), expectedSha256 = "abc", context = CONTEXT)

        assertEquals(SandboxJobOutcome.Failed("COMPANION_UNREACHABLE"), outcome)
    }

    @Test
    fun `the submitted request carries a deterministic idempotency key, not a random one`() = runTest {
        val registry = CapabilityRegistry(emptyMap())
        val executor = WorkflowExecutor(registry, emptyMap(), NoOpLineageRecorder())
        val transport = FakeSandboxTransport(jobResult(WasmJobResultPayload(succeeded = true, result = 1.0)))
        val repository = SandboxJobRepository(transport, registry, executor, TEST_JSON)

        repository.submit(program = byteArrayOf(1, 2, 3), expectedSha256 = "digest-a", export = "run", inputValue = 7, context = CONTEXT)
        val firstKey = transport.lastRequest?.idempotencyKey

        repository.submit(program = byteArrayOf(9, 9, 9), expectedSha256 = "digest-a", export = "run", inputValue = 7, context = CONTEXT)
        val secondKey = transport.lastRequest?.idempotencyKey

        assertEquals("the key is derived from the digest/export/input, not the raw bytes or a random id", firstKey, secondKey)
    }
}
