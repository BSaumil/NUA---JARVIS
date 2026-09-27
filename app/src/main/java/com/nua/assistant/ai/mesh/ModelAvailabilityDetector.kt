package com.nua.assistant.ai.mesh

import com.nua.assistant.memory.SecureKeyRepository
import com.nua.assistant.network.ConnectivityMonitor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a given [ModelProviderTier] can actually handle [task] right now — checked
 * dynamically, never assumed. An interface (like `EmailRepository`/`SmartHomeRepository`)
 * so [ModelMesh]'s routing tests can supply a fake instead of touching real
 * connectivity/keystore APIs.
 */
interface ModelAvailabilityDetector {
    suspend fun isAvailable(tier: ModelProviderTier, task: InferenceTaskType): Boolean
}

/**
 * The real detector. [ModelProviderTier.LOCAL_RULES] is only ever reported available for
 * [InferenceTaskType.INTENT_CLASSIFICATION] — the one task this codebase has a genuine
 * deterministic local resolver for (`automation/KeywordIntentMatcher.kt`); claiming it for
 * any other task would be fabricating a capability that doesn't exist.
 * [ModelProviderTier.LOCAL_MODEL]/[ModelProviderTier.PRIVATE_OS_MODEL] always report
 * unavailable — no on-device model ships with this app. Cloud tiers require both
 * connectivity and a configured API key, the same two checks
 * [com.nua.assistant.ai.ClaudeApiClient.sendMessage] and
 * `ui/NuaViewModel.kt`'s `replyConversationally` already make independently — this
 * detector doesn't duplicate that logic elsewhere, it's the one place the Mesh consults it.
 */
@Singleton
class RealModelAvailabilityDetector @Inject constructor(
    private val connectivityMonitor: ConnectivityMonitor,
    private val secureKeyRepository: SecureKeyRepository,
) : ModelAvailabilityDetector {
    override suspend fun isAvailable(tier: ModelProviderTier, task: InferenceTaskType): Boolean = when (tier) {
        ModelProviderTier.LOCAL_RULES -> task == InferenceTaskType.INTENT_CLASSIFICATION
        ModelProviderTier.LOCAL_MODEL, ModelProviderTier.PRIVATE_OS_MODEL -> false
        ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER ->
            connectivityMonitor.isOnline() && !secureKeyRepository.getApiKey().isNullOrBlank()
    }
}
