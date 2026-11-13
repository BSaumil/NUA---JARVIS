package com.nua.assistant.ai.mesh

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.nua.assistant.ai.ClaudeResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text-completion boundary for an OS-managed private model. It intentionally mirrors
 * [CloudCompletionProvider] rather than exposing ML Kit/AICore types to domain callers.
 *
 * NUA never treats "downloadable" as "available": model download is a user/device
 * provisioning concern, and scheduled/background work must not unexpectedly start a
 * large system-model download. The Mesh only routes here when [isAvailable] says the
 * model is already ready.
 */
interface PrivateOsCompletionProvider {
    suspend fun isAvailable(task: InferenceTaskType): Boolean
    suspend fun complete(
        userPrompt: String,
        system: String?,
        maxTokens: Int,
    ): ClaudeResult
}

/**
 * Gemini Nano through Android AICore / ML Kit Prompt API.
 *
 * This is NUA's first real [ModelProviderTier.PRIVATE_OS_MODEL] implementation. Prompt
 * and response remain on-device. Availability probes are wrapped in runCatching because
 * AICore implementations on some OEM builds have historically thrown unexpected runtime
 * exceptions from status checks; NUA must fail closed instead of crashing the assistant.
 *
 * The provider is deliberately text-only for this slice. Vision and embeddings keep
 * reporting unavailable until NUA has a verified local implementation for those exact
 * task contracts.
 */
@Singleton
class GeminiNanoPrivateOsCompletionProvider @Inject constructor() : PrivateOsCompletionProvider {

    override suspend fun isAvailable(task: InferenceTaskType): Boolean {
        if (!supports(task)) return false
        val model = Generation.getClient()
        return try {
            model.checkStatus() == FeatureStatus.AVAILABLE
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { model.close() }
        }
    }

    override suspend fun complete(
        userPrompt: String,
        system: String?,
        maxTokens: Int,
    ): ClaudeResult {
        val model = Generation.getClient()
        return try {
            if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                return ClaudeResult.Failure("Gemini Nano is not currently available on this device.")
            }

            // Direct-model access has its own device/model output limits. maxTokens remains
            // part of NUA's provider-neutral contract; this beta API's simple text overload
            // does not expose that limit without coupling the Mesh to SDK request builders.
            @Suppress("UNUSED_VARIABLE")
            val requestedMaxTokens = maxTokens
            val prompt = buildString {
                if (!system.isNullOrBlank()) {
                    append("NUA instructions:\n")
                    append(system.trim())
                    append("\n\n")
                }
                append("User request:\n")
                append(userPrompt)
            }
            val response = model.generateContent(prompt)
            val text = response.text.trim()
            if (text.isBlank()) ClaudeResult.Failure("Gemini Nano returned no usable text.")
            else ClaudeResult.Success(text)
        } catch (t: Throwable) {
            ClaudeResult.Failure("On-device Gemini Nano inference failed.", t)
        } finally {
            runCatching { model.close() }
        }
    }

    private fun supports(task: InferenceTaskType): Boolean = when (task) {
        InferenceTaskType.EMBEDDING_RETRIEVAL,
        InferenceTaskType.VISION,
        -> false
        else -> true
    }
}
