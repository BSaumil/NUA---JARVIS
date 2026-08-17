package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.IntentClassifier
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.notifications.NotificationEntry
import com.nua.assistant.security.UserUtterance
import com.nua.assistant.trust.TrustRepository
import com.nua.assistant.trust.autonomyTierFor
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

/** Below this confidence, a Claude-classified intent isn't acted on — falls through to chat instead. */
private const val CLASSIFICATION_CONFIDENCE_THRESHOLD = 0.6

sealed class NuaRouteResult {
    /** [succeeded] feeds the Trust Engine's audit trail and score — see trust/TrustRepository.kt. */
    data class ActionTaken(val message: String, val succeeded: Boolean = true) : NuaRouteResult()
    data class PlanProposed(val plan: TaskPlan) : NuaRouteResult()
    /** Sending a message on the user's behalf is sensitive — always confirmed before NotificationReplySender fires. */
    data class ReplyProposed(val notification: NotificationEntry, val message: String) : NuaRouteResult()
    /** Same reasoning as ReplyProposed — always confirmed before SmsSender fires. */
    data class SmsProposed(val contactName: String, val phoneNumber: String, val message: String) : NuaRouteResult()
    data object FallThroughToChat : NuaRouteResult()
}

/**
 * Routes a user utterance to a concrete action. Keyword matching (free, no API call)
 * is tried first; only utterances it misses go to Claude-based classification
 * (IntentClassifier), so unambiguous requests never pay for a round trip. Dispatch
 * itself is a lookup into [skills] (see NuaSkill.kt) — this class knows nothing about
 * any specific Tier 1 capability, just how to pick the right one and fall back to chat.
 */
@Singleton
class NuaIntentRouter @Inject constructor(
    private val intentClassifier: IntentClassifier,
    private val skills: Map<NuaActionType, @JvmSuppressWildcards NuaSkill>,
    private val trustRepository: TrustRepository,
) {

    /**
     * Takes a [UserUtterance], not a bare String — see that type's doc comment. This is
     * the one entry point into action dispatch; nothing document/vision/notification/
     * email-derived should ever be wrapped and passed here.
     */
    suspend fun route(utterance: UserUtterance, pinnedLanguage: NuaLanguage? = null): NuaRouteResult {
        val text = utterance.text
        KeywordIntentMatcher.match(text)?.let { return dispatch(it, text, pinnedLanguage) }

        val classified = intentClassifier.classify(utterance) ?: return NuaRouteResult.FallThroughToChat
        if (classified.action == NuaActionType.CHAT || classified.confidence < CLASSIFICATION_CONFIDENCE_THRESHOLD) {
            return NuaRouteResult.FallThroughToChat
        }
        return dispatch(classified, text, pinnedLanguage)
    }

    private suspend fun dispatch(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult {
        val skill = skills[intent.action] ?: return NuaRouteResult.FallThroughToChat
        val result = skill.execute(intent, originalUtterance, pinnedLanguage)
        // Only ActionTaken resolves immediately — proposals (Plan/Reply) are logged by the
        // ViewModel once the user actually confirms or declines them.
        if (result is NuaRouteResult.ActionTaken) {
            trustRepository.recordOutcome(
                actionType = intent.action.name,
                tier = autonomyTierFor(intent.action),
                summary = result.message,
                succeeded = result.succeeded,
            )
        }
        return result
    }
}
