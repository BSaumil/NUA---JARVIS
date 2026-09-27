package com.nua.assistant.ai.mesh

import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.security.UserUtterance
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reports a fixed availability set, task-aware like the real detector. */
private class FakeAvailabilityDetector(private val available: Set<ModelProviderTier>) : ModelAvailabilityDetector {
    override suspend fun isAvailable(tier: ModelProviderTier, task: InferenceTaskType): Boolean = tier in available
}

private class FakeCloudCompletionProvider(private val result: ClaudeResult) : CloudCompletionProvider {
    var calls = 0
    var lastModel: String? = null
    override suspend fun complete(userPrompt: String, system: String?, model: String, maxTokens: Int): ClaudeResult {
        calls++
        lastModel = model
        return result
    }
}

/**
 * Exercises the real coroutine orchestration of [ModelMesh.complete]/[ModelMesh.classifyIntent]
 * against fakes — [fallbackOrder] itself is tested directly in [ModelProviderTierTest];
 * this file proves the Mesh actually wires availability detection and provider selection
 * together correctly, including the duplicate-cloud-call defect caught and fixed before
 * this push (see [ModelMesh.classifyIntent]'s own doc comment).
 */
class ModelMeshTest {

    @Test
    fun `complete resolves to CLOUD_FAST's model for a task with no frontier requirement`() = runTest {
        val provider = FakeCloudCompletionProvider(ClaudeResult.Success("ok"))
        val mesh = ModelMesh(provider, FakeAvailabilityDetector(setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)))
        val contract = TaskContract(InferenceTaskType.ENTITY_EXTRACTION, PrivacySensitivity.LOW)

        mesh.complete(contract, "extract entities")

        assertEquals(1, provider.calls)
        assertEquals("claude-haiku-4-5-20251001", provider.lastModel)
    }

    @Test
    fun `complete resolves to CLOUD_FRONTIER's model when the contract requires frontier capability`() = runTest {
        val provider = FakeCloudCompletionProvider(ClaudeResult.Success("ok"))
        val mesh = ModelMesh(provider, FakeAvailabilityDetector(setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)))
        val contract = TaskContract(InferenceTaskType.REASONING, PrivacySensitivity.LOW, requiresFrontierCapability = true)

        mesh.complete(contract, "reason about this")

        assertEquals(1, provider.calls)
        assertEquals("claude-sonnet-5", provider.lastModel)
    }

    @Test
    fun `complete never attempts a network call when no cloud tier is available -- offline, honestly reported`() = runTest {
        val provider = FakeCloudCompletionProvider(ClaudeResult.Success("should never be seen"))
        val mesh = ModelMesh(provider, FakeAvailabilityDetector(emptySet()))
        val contract = TaskContract(InferenceTaskType.SUMMARIZATION, PrivacySensitivity.LOW)

        val result = mesh.complete(contract, "summarize this")

        assertEquals(0, provider.calls)
        assertTrue(result is ClaudeResult.Failure)
    }

    @Test
    fun `complete never attempts a network call for an offline-required task even when cloud is technically available`() = runTest {
        val provider = FakeCloudCompletionProvider(ClaudeResult.Success("should never be seen"))
        val mesh = ModelMesh(provider, FakeAvailabilityDetector(setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)))
        val contract = TaskContract(InferenceTaskType.SENSITIVE_LOCAL_TRANSFORM, PrivacySensitivity.HIGH, requiresOffline = true)

        val result = mesh.complete(contract, "transform this locally")

        assertEquals(0, provider.calls)
        assertTrue(result is ClaudeResult.Failure)
    }

    @Test
    fun `classifyIntent resolves locally without ever invoking the cloud path when a keyword rule matches`() = runTest {
        val provider = FakeCloudCompletionProvider(ClaudeResult.Success("unused"))
        val mesh = ModelMesh(provider, FakeAvailabilityDetector(setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)))
        var cloudCalls = 0

        val result = mesh.classifyIntent(UserUtterance("open spotify")) { cloudCalls++; null }

        assertEquals(NuaActionType.OPEN_APP, result?.action)
        assertEquals("no keyword match means the cloud path must never be invoked", 0, cloudCalls)
    }

    @Test
    fun `classifyIntent falls through to the cloud path when no keyword rule matches`() = runTest {
        val provider = FakeCloudCompletionProvider(ClaudeResult.Success("unused"))
        val mesh = ModelMesh(provider, FakeAvailabilityDetector(setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)))
        val expected = ClassifiedIntent(NuaActionType.GET_WEATHER, confidence = 0.9)

        val result = mesh.classifyIntent(UserUtterance("something ambiguous entirely")) { expected }

        assertEquals(expected, result)
    }

    @Test
    fun `classifyIntent invokes the cloud path at most once, even though two cloud tiers are available -- the duplicate-call defect`() = runTest {
        val provider = FakeCloudCompletionProvider(ClaudeResult.Success("unused"))
        val mesh = ModelMesh(provider, FakeAvailabilityDetector(setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)))
        var cloudCalls = 0

        val result = mesh.classifyIntent(UserUtterance("something ambiguous entirely")) { cloudCalls++; null }

        assertNull(result)
        assertEquals("a null cloud result must not trigger a second identical call under a different nominal tier", 1, cloudCalls)
    }

    @Test
    fun `classifyIntent returns null, never crashes, when nothing resolves it`() = runTest {
        val provider = FakeCloudCompletionProvider(ClaudeResult.Success("unused"))
        val mesh = ModelMesh(provider, FakeAvailabilityDetector(emptySet()))

        val result = mesh.classifyIntent(UserUtterance("anything at all")) { error("must never be called with no available tiers") }

        assertNull(result)
    }
}
