package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.IntentClassifier
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.notifications.NotificationEntry
import com.nua.assistant.security.UserUtterance
import com.nua.assistant.trust.ActionOutcomeState
import com.nua.assistant.trust.TrustRepository
import com.nua.assistant.trust.autonomyTierFor
import com.nua.assistant.trust.idempotencyKeyFor
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

/** Below this confidence, a Claude-classified intent isn't acted on — falls through to chat instead. */
private const val CLASSIFICATION_CONFIDENCE_THRESHOLD = 0.6

/**
 * Direct-dispatch action types (resolved as [NuaRouteResult.ActionTaken] with no user
 * confirmation step) whose skill causes a real external side effect that must not repeat
 * — as opposed to GET_WEATHER, READ_NOTIFICATIONS, OPEN_APP, etc., which are harmless
 * (often desirable) to repeat. SMS_SEND/REPLY_TO_NOTIFICATION/PLAN_TASK never reach
 * [dispatch] at all — they resolve as proposals and get their own
 * TrustRepository.wasRecentlyExecuted check in NuaViewModel's confirm* functions.
 */
private val NON_REPEATABLE_DIRECT_ACTIONS = setOf(NuaActionType.CALENDAR_INVITE)

private const val DUPLICATE_DIRECT_ACTION_SUPPRESSED_MESSAGE = "Already did that a moment ago — not doing it twice."

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
    private val skillSandbox: SkillSandbox,
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
        // The skills map is a closed set assembled by Hilt (SkillModule.kt) — an action
        // with no binding simply isn't dispatchable, so there's no arbitrary execution
        // path. Everything that is dispatchable goes through the sandbox, never directly.
        val skill = skills[intent.action] ?: return NuaRouteResult.FallThroughToChat

        val idempotencyKey = if (intent.action in NON_REPEATABLE_DIRECT_ACTIONS) {
            val paramsKey = intent.parameters.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }
            idempotencyKeyFor(intent.action.name, paramsKey)
        } else {
            null
        }
        if (idempotencyKey != null && trustRepository.wasRecentlyExecuted(idempotencyKey)) {
            return NuaRouteResult.ActionTaken(DUPLICATE_DIRECT_ACTION_SUPPRESSED_MESSAGE, succeeded = true)
        }

        val result = skillSandbox.execute(skill, intent, originalUtterance, pinnedLanguage)
        // Only ActionTaken resolves immediately — proposals (Plan/Reply) are logged by the
        // ViewModel once the user actually confirms or declines them.
        if (result is NuaRouteResult.ActionTaken) {
            // Every ActionTaken(succeeded=...) site is a synchronous, in-hand confirmation
            // (a Result, a nullable check, an exception boundary) — not a fire-and-forget
            // OS call — so a direct boolean-to-terminal-state mapping is honest here. That's
            // not true of confirmPendingSms/Reply/Plan in NuaViewModel.kt; see there.
            trustRepository.recordOutcome(
                actionType = intent.action.name,
                tier = autonomyTierFor(intent.action),
                summary = result.message,
                outcome = if (result.succeeded) ActionOutcomeState.COMPLETED else ActionOutcomeState.FAILED,
                idempotencyKey = idempotencyKey,
            )
        }
        return result
    }
}
