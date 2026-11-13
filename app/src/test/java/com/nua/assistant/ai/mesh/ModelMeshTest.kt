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

private class FakeAvailabilityDetector(
    private val available: Set<ModelProviderTier>,
) : ModelAvailabilityDetector {
    override suspend fun isAvailable(tier: ModelProviderTier, task: InferenceTaskType): Boolean =
        tier in available
}

private class FakeCloudCompletionProvider(
    private val result: ClaudeResult,
) : CloudCompletionProvider {
    var calls = 0
    var lastModel: String? = null
    override suspend fun complete(
        userPrompt: String,
        system: String?,
        model: String,
        maxTokens: Int,
    ): ClaudeResult {
        calls++
        lastModel = model
        return result
    }
}

private class FakePrivateOsCompletionProvider(
    private val result: ClaudeResult,
    private val available: Boolean = true,
) : PrivateOsCompletionProvider {
    var calls = 0
    override suspend fun isAvailable(task: InferenceTaskType): Boolean = available
    override suspend fun complete(
        userPrompt: String,
        system: String?,
        maxTokens: Int,
    ): ClaudeResult {
        calls++
        return result
    }
}

private fun mesh(
    cloud: FakeCloudCompletionProvider,
    privateOs: FakePrivateOsCompletionProvider = FakePrivateOsCompletionProvider(
        ClaudeResult.Failure("unused"),
        available = false,
    ),
    available: Set<ModelProviderTier>,
) = ModelMesh(cloud, privateOs, FakeAvailabilityDetector(available))

class ModelMeshTest {

    @Test
    fun `private OS model wins before cloud for supported text completion`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("cloud"))
        val local = FakePrivateOsCompletionProvider(ClaudeResult.Success("local"))
        val mesh = mesh(
            cloud,
            local,
            setOf(ModelProviderTier.PRIVATE_OS_MODEL, ModelProviderTier.CLOUD_FAST),
        )

        val result = mesh.complete(
            TaskContract(InferenceTaskType.SUMMARIZATION, PrivacySensitivity.LOW),
            "summarize",
        )

        assertEquals("local", (result as ClaudeResult.Success).text)
        assertEquals(1, local.calls)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun `local failure can fall through to cloud when contract permits cloud`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("cloud"))
        val local = FakePrivateOsCompletionProvider(ClaudeResult.Failure("local failed"))
        val mesh = mesh(
            cloud,
            local,
            setOf(ModelProviderTier.PRIVATE_OS_MODEL, ModelProviderTier.CLOUD_FAST),
        )

        val result = mesh.complete(
            TaskContract(InferenceTaskType.SUMMARIZATION, PrivacySensitivity.LOW),
            "summarize",
        )

        assertEquals("cloud", (result as ClaudeResult.Success).text)
        assertEquals(1, local.calls)
        assertEquals(1, cloud.calls)
    }


    @Test
    fun `validator rejection falls through from private OS to cloud without relaxing contract`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("{\"ok\":true}"))
        val local = FakePrivateOsCompletionProvider(ClaudeResult.Success("not-json"))
        val mesh = mesh(
            cloud,
            local,
            setOf(ModelProviderTier.PRIVATE_OS_MODEL, ModelProviderTier.CLOUD_FAST),
        )

        val result = mesh.completeValidated(
            contract = TaskContract(InferenceTaskType.ENTITY_EXTRACTION, PrivacySensitivity.LOW),
            userPrompt = "extract",
            validator = { it.startsWith("{") && it.endsWith("}") },
        )

        assertEquals("{\"ok\":true}", (result as ClaudeResult.Success).text)
        assertEquals(1, local.calls)
        assertEquals(1, cloud.calls)
    }

    @Test
    fun `validator rejection cannot cause HIGH privacy cloud egress`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("{\"ok\":true}"))
        val local = FakePrivateOsCompletionProvider(ClaudeResult.Success("not-json"))
        val mesh = mesh(
            cloud,
            local,
            setOf(ModelProviderTier.PRIVATE_OS_MODEL, ModelProviderTier.CLOUD_FAST),
        )

        val result = mesh.completeValidated(
            contract = TaskContract(InferenceTaskType.SENSITIVE_LOCAL_TRANSFORM, PrivacySensitivity.HIGH),
            userPrompt = "sensitive",
            validator = { it.startsWith("{") && it.endsWith("}") },
        )

        assertTrue(result is ClaudeResult.Failure)
        assertEquals(1, local.calls)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun `HIGH privacy refuses cloud by default even when cloud is available`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("must not run"))
        val local = FakePrivateOsCompletionProvider(ClaudeResult.Failure("local unavailable"))
        val mesh = mesh(
            cloud,
            local,
            setOf(ModelProviderTier.PRIVATE_OS_MODEL, ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER),
        )

        val result = mesh.complete(
            TaskContract(InferenceTaskType.SENSITIVE_LOCAL_TRANSFORM, PrivacySensitivity.HIGH),
            "sensitive",
        )

        assertTrue(result is ClaudeResult.Failure)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun `caller can explicitly allow cloud for HIGH privacy after policy approval`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("approved cloud"))
        val local = FakePrivateOsCompletionProvider(ClaudeResult.Failure("local unavailable"))
        val mesh = mesh(
            cloud,
            local,
            setOf(ModelProviderTier.PRIVATE_OS_MODEL, ModelProviderTier.CLOUD_FAST),
        )

        val result = mesh.complete(
            TaskContract(
                InferenceTaskType.SENSITIVE_LOCAL_TRANSFORM,
                PrivacySensitivity.HIGH,
                allowCloud = true,
            ),
            "sensitive but approved",
        )

        assertEquals("approved cloud", (result as ClaudeResult.Success).text)
        assertEquals(1, cloud.calls)
    }

    @Test
    fun `complete resolves to CLOUD_FAST model for utility task when local is absent`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("ok"))
        val mesh = mesh(
            cloud,
            available = setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER),
        )

        mesh.complete(
            TaskContract(InferenceTaskType.ENTITY_EXTRACTION, PrivacySensitivity.LOW),
            "extract entities",
        )

        assertEquals(1, cloud.calls)
        assertEquals("claude-haiku-4-5-20251001", cloud.lastModel)
    }

    @Test
    fun `frontier contract routes to CLOUD_FRONTIER when local is absent`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("ok"))
        val mesh = mesh(
            cloud,
            available = setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER),
        )

        mesh.complete(
            TaskContract(
                InferenceTaskType.REASONING,
                PrivacySensitivity.LOW,
                requiresFrontierCapability = true,
            ),
            "reason",
        )

        assertEquals("claude-sonnet-5", cloud.lastModel)
    }

    @Test
    fun `offline-required task never attempts cloud`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("must not run"))
        val mesh = mesh(
            cloud,
            available = setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER),
        )

        val result = mesh.complete(
            TaskContract(
                InferenceTaskType.SENSITIVE_LOCAL_TRANSFORM,
                PrivacySensitivity.HIGH,
                requiresOffline = true,
            ),
            "local only",
        )

        assertTrue(result is ClaudeResult.Failure)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun `classifyIntent resolves local rule without cloud`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("unused"))
        val mesh = mesh(
            cloud,
            available = setOf(
                ModelProviderTier.LOCAL_RULES,
                ModelProviderTier.PRIVATE_OS_MODEL,
                ModelProviderTier.CLOUD_FAST,
            ),
        )
        var cloudCalls = 0

        val result = mesh.classifyIntent(UserUtterance("open spotify")) {
            cloudCalls++
            null
        }

        assertEquals(NuaActionType.OPEN_APP, result?.action)
        assertEquals(0, cloudCalls)
    }

    @Test
    fun `generic private text model is not guessed into a typed ClassifiedIntent`() = runTest {
        val cloud = FakeCloudCompletionProvider(ClaudeResult.Success("unused"))
        val mesh = mesh(
            cloud,
            available = setOf(ModelProviderTier.PRIVATE_OS_MODEL),
        )
        var cloudCalls = 0

        val result = mesh.classifyIntent(UserUtterance("ambiguous")) {
            cloudCalls++
            ClassifiedIntent(NuaActionType.GET_WEATHER, confidence = 0.9)
        }

        assertNull(result)
        assertEquals(0, cloudCalls)
    }
}
