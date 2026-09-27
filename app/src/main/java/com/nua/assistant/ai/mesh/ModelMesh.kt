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

/**
 * Wraps [ClaudeApiClient.complete] behind an interface purely so [ModelMesh] can be
 * constructed with a fake in a pure JVM test — [ClaudeApiClient] itself needs a real
 * `OkHttpClient`/`SecureKeyRepository`, unavailable outside Android, the same reason
 * `LineageRecorder`/`ActionAdapter` are interfaces rather than concrete classes.
 */
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
 * Provider-neutral entry point for inference — the Sovereign Model Mesh directive
 * (Feature 2). Domain callers ask for a [TaskContract]'s worth of work; the Mesh decides
 * *how* to satisfy it via [fallbackOrder], trying each available tier in priority order
 * until one produces a usable result. Callers never construct a
 * [com.nua.assistant.ai.ClaudeApiClient] request directly once migrated — see
 * [com.nua.assistant.ai.IntentClassifier], this round's one migrated call site.
 *
 * Scope of this slice, stated honestly: only [InferenceTaskType.INTENT_CLASSIFICATION]
 * has a genuine multi-tier path ([classifyIntent], local rules then cloud). [complete]
 * is provider-neutral in interface but, today, can only ever resolve to a cloud call —
 * [ModelProviderTier.LOCAL_MODEL]/[ModelProviderTier.PRIVATE_OS_MODEL] have no real
 * implementation yet (see [RealModelAvailabilityDetector]). Every other Claude call site
 * in this codebase (fact extraction, planning, briefings, document/vision analysis,
 * workers) is *not* migrated this round — each is a real, separately-scoped next slice,
 * not bundled into one large rewrite. Usage/cost tracking is not duplicated here:
 * [complete] delegates to [ClaudeApiClient.complete], which already calls
 * `UsageTracker.record` on every successful response — the Settings cost dashboard
 * already reads real routed usage without this class needing its own accounting.
 */
@Singleton
class ModelMesh @Inject constructor(
    private val cloudCompletionProvider: CloudCompletionProvider,
    private val availabilityDetector: ModelAvailabilityDetector,
) {
    /**
     * Provider-neutral single-turn completion. Resolves to [CloudCompletionProvider.complete]
     * on [ModelProviderTier.CLOUD_FAST] ([CLAUDE_MODEL_UTILITY]) or
     * [ModelProviderTier.CLOUD_FRONTIER] ([CLAUDE_MODEL_CONVERSATION]) depending on
     * [TaskContract.requiresFrontierCapability] — the same model choice every migrated
     * call site already made explicitly, now decided once by the contract instead of
     * re-decided per call site. Returns an honest [ClaudeResult.Failure] — never attempts
     * a network call — when no tier in [fallbackOrder] is currently available (offline,
     * no API key, or [TaskContract.requiresOffline] with no on-device model to serve it).
     */
    suspend fun complete(contract: TaskContract, userPrompt: String, system: String? = null, maxTokens: Int = 512): ClaudeResult {
        val available = ModelProviderTier.entries.filter { availabilityDetector.isAvailable(it, contract.task) }.toSet()
        for (tier in fallbackOrder(contract, available)) {
            when (tier) {
                ModelProviderTier.CLOUD_FAST ->
                    return cloudCompletionProvider.complete(userPrompt, system, model = CLAUDE_MODEL_UTILITY, maxTokens = maxTokens)
                ModelProviderTier.CLOUD_FRONTIER ->
                    return cloudCompletionProvider.complete(userPrompt, system, model = CLAUDE_MODEL_CONVERSATION, maxTokens = maxTokens)
                ModelProviderTier.LOCAL_RULES, ModelProviderTier.LOCAL_MODEL, ModelProviderTier.PRIVATE_OS_MODEL ->
                    continue // no generic text-completion behavior for these tiers today
            }
        }
        return ClaudeResult.Failure(
            "No available model provider for ${contract.task} under the current contract " +
                "(offline or unconfigured, and no on-device model is available yet).",
        )
    }

    /**
     * The one genuinely multi-tier task this slice wires end to end: tries
     * [ModelProviderTier.LOCAL_RULES] ([KeywordIntentMatcher] — real, on-device, no
     * network) first, falling through to a cloud classification call only when the local
     * rules miss this specific utterance. [classifyViaCloud] represents "the cloud path"
     * as a single unit — it doesn't distinguish CLOUD_FAST from CLOUD_FRONTIER internally
     * (intent classification only ever needs the cheap/fast tier) — so it's invoked at
     * most once per call even though [fallbackOrder] can name both cloud tiers as
     * available; without that guard, a null result (e.g. an unparseable reply) would
     * silently trigger a second, identical network call under the CLOUD_FRONTIER tier —
     * a real duplicate-side-effect defect, caught here by hand-tracing before it ever
     * reached CI, the same discipline the Guardian Lab round's own adversarial tests
     * exist to enforce. Not yet called by
     * [com.nua.assistant.automation.NuaIntentRouter] — that dispatch path keeps its own,
     * separately-tested keyword-then-classify sequence unchanged this round; see this
     * feature's "explicitly not attempted" note in docs/HISTORY.md for why migrating the
     * production router itself is deliberately deferred.
     */
    suspend fun classifyIntent(utterance: UserUtterance, classifyViaCloud: suspend (UserUtterance) -> ClassifiedIntent?): ClassifiedIntent? {
        val contract = TaskContract(task = InferenceTaskType.INTENT_CLASSIFICATION, privacySensitivity = PrivacySensitivity.MEDIUM)
        val available = ModelProviderTier.entries.filter { availabilityDetector.isAvailable(it, contract.task) }.toSet()
        var cloudAttempted = false
        for (tier in fallbackOrder(contract, available)) {
            val result = when (tier) {
                ModelProviderTier.LOCAL_RULES -> KeywordIntentMatcher.match(utterance.text)
                ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER ->
                    if (cloudAttempted) null else { cloudAttempted = true; classifyViaCloud(utterance) }
                ModelProviderTier.LOCAL_MODEL, ModelProviderTier.PRIVATE_OS_MODEL -> null
            }
            if (result != null) return result
        }
        return null
    }
}
