package com.nua.assistant.ai.mesh

import com.nua.assistant.ai.CLAUDE_MODEL_CONVERSATION
import com.nua.assistant.ai.CLAUDE_MODEL_UTILITY
import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.automation.KeywordIntentMatcher
import com.nua.assistant.security.UserUtterance
import javax.inject.Inject
import javax.inject.Singleton

interface CloudCompletionProvider {
    suspend fun complete(userPrompt: String, system: String?, model: String, maxTokens: Int): ClaudeResult
}

@Singleton
class ClaudeApiCloudCompletionProvider @Inject constructor(
    private val claudeApiClient: ClaudeApiClient,
) : CloudCompletionProvider {
    override suspend fun complete(userPrompt: String, system: String?, model: String, maxTokens: Int): ClaudeResult =
        claudeApiClient.complete(userPrompt, system, model, maxTokens)
}

/**
 * Provider-neutral entry point for NUA Sovereign inference.
 *
 * Unlike the first Model Mesh slice, [ModelProviderTier.PRIVATE_OS_MODEL] is now real:
 * supported text tasks can execute through Gemini Nano/AICore without sending the prompt
 * off-device. A local-provider failure is not terminal unless the contract forbids cloud;
 * the Mesh proceeds through the remaining allowed tiers, preserving truthful fallback.
 *
 * The routing contract is privacy-aware: HIGH-sensitivity [TaskContract]s disallow cloud
 * by default, and [TaskContract.requiresOffline] always excludes it. A caller must make
 * an explicit contract decision to permit cloud for sensitive data.
 */
@Singleton
class ModelMesh @Inject constructor(
    private val cloudCompletionProvider: CloudCompletionProvider,
    private val privateOsCompletionProvider: PrivateOsCompletionProvider,
    private val availabilityDetector: ModelAvailabilityDetector,
) {
    suspend fun complete(
        contract: TaskContract,
        userPrompt: String,
        system: String? = null,
        maxTokens: Int = 512,
    ): ClaudeResult {
        val available = ModelProviderTier.entries
            .filter { availabilityDetector.isAvailable(it, contract.task) }
            .toSet()

        var lastFailure: ClaudeResult.Failure? = null
        for (tier in fallbackOrder(contract, available)) {
            val result = when (tier) {
                ModelProviderTier.PRIVATE_OS_MODEL ->
                    privateOsCompletionProvider.complete(userPrompt, system, maxTokens)
                ModelProviderTier.CLOUD_FAST ->
                    cloudCompletionProvider.complete(
                        userPrompt,
                        system,
                        model = CLAUDE_MODEL_UTILITY,
                        maxTokens = maxTokens,
                    )
                ModelProviderTier.CLOUD_FRONTIER ->
                    cloudCompletionProvider.complete(
                        userPrompt,
                        system,
                        model = CLAUDE_MODEL_CONVERSATION,
                        maxTokens = maxTokens,
                    )
                ModelProviderTier.LOCAL_RULES,
                ModelProviderTier.LOCAL_MODEL,
                -> continue
            }
            when (result) {
                is ClaudeResult.Success -> return result
                is ClaudeResult.Failure -> lastFailure = result
            }
        }

        return lastFailure ?: ClaudeResult.Failure(
            "No available model provider for ${contract.task} under the current privacy/offline contract.",
        )
    }

    suspend fun classifyIntent(
        utterance: UserUtterance,
        classifyViaCloud: suspend (UserUtterance) -> ClassifiedIntent?,
    ): ClassifiedIntent? {
        val contract = TaskContract(
            task = InferenceTaskType.INTENT_CLASSIFICATION,
            privacySensitivity = PrivacySensitivity.MEDIUM,
        )
        val available = ModelProviderTier.entries
            .filter { availabilityDetector.isAvailable(it, contract.task) }
            .toSet()
        var cloudAttempted = false
        for (tier in fallbackOrder(contract, available)) {
            val result = when (tier) {
                ModelProviderTier.LOCAL_RULES -> KeywordIntentMatcher.match(utterance.text)
                ModelProviderTier.CLOUD_FAST,
                ModelProviderTier.CLOUD_FRONTIER,
                -> if (cloudAttempted) null else {
                    cloudAttempted = true
                    classifyViaCloud(utterance)
                }
                // Intent classification has a typed output contract. Until NUA adds a
                // structured local parser, generic text completion must not be guessed
                // back into a ClassifiedIntent.
                ModelProviderTier.LOCAL_MODEL,
                ModelProviderTier.PRIVATE_OS_MODEL,
                -> null
            }
            if (result != null) return result
        }
        return null
    }
}
