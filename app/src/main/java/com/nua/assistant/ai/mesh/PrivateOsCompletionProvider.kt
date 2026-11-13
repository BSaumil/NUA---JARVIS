package com.nua.assistant.ai.mesh

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
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

            val outputBudget = maxTokens.coerceIn(1, 4096)
            val request = if (system.isNullOrBlank()) {
                generateContentRequest(TextPart(userPrompt)) {
                    maxOutputTokens = outputBudget
                }
            } else {
                generateContentRequest(SystemInstruction(system), TextPart(userPrompt)) {
                    maxOutputTokens = outputBudget
                }
            }
            val response = model.generateContent(request)
            val text = response.candidates.firstOrNull()?.text?.trim().orEmpty()
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
