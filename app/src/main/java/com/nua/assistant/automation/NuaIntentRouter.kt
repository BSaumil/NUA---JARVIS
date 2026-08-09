package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.IntentClassifier
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.ai.TaskPlanner
import com.nua.assistant.briefing.MorningBriefing
import com.nua.assistant.media.MediaControlManager
import com.nua.assistant.notifications.NotificationEntry
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.smarthome.SmartHomeRepository
import com.nua.assistant.smarthome.SmartHomeResult
import com.nua.assistant.voice.NuaLanguage
import com.nua.assistant.weather.WeatherRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Below this confidence, a Claude-classified intent isn't acted on — falls through to chat instead. */
private const val CLASSIFICATION_CONFIDENCE_THRESHOLD = 0.6

sealed class NuaRouteResult {
    data class ActionTaken(val message: String) : NuaRouteResult()
    data class PlanProposed(val plan: TaskPlan) : NuaRouteResult()
    /** Sending a message on the user's behalf is sensitive — always confirmed before NotificationReplySender fires. */
    data class ReplyProposed(val notification: NotificationEntry, val message: String) : NuaRouteResult()
    data object FallThroughToChat : NuaRouteResult()
}

/**
 * Routes a user utterance to a concrete action. Keyword matching (free, no API call)
 * is tried first; only utterances it misses go to Claude-based classification
 * (IntentClassifier), so unambiguous requests never pay for a round trip.
 */
@Singleton
class NuaIntentRouter @Inject constructor(
    private val appLauncher: AppLauncher,
    private val mediaControlManager: MediaControlManager,
    private val weatherRepository: WeatherRepository,
    private val morningBriefing: MorningBriefing,
    private val notificationRepository: NotificationRepository,
    private val taskPlanner: TaskPlanner,
    private val intentClassifier: IntentClassifier,
    private val smartHomeRepository: SmartHomeRepository,
) {

    suspend fun route(utterance: String, pinnedLanguage: NuaLanguage? = null): NuaRouteResult {
        KeywordIntentMatcher.match(utterance)?.let { return dispatch(it, utterance, pinnedLanguage) }

        val classified = intentClassifier.classify(utterance) ?: return NuaRouteResult.FallThroughToChat
        if (classified.action == NuaActionType.CHAT || classified.confidence < CLASSIFICATION_CONFIDENCE_THRESHOLD) {
            return NuaRouteResult.FallThroughToChat
        }
        return dispatch(classified, utterance, pinnedLanguage)
    }

    private suspend fun dispatch(intent: ClassifiedIntent, originalUtterance: String, pinnedLanguage: NuaLanguage?): NuaRouteResult = when (intent.action) {
        NuaActionType.OPEN_APP -> {
            val app = intent.parameters["app"]
            if (app.isNullOrBlank()) {
                NuaRouteResult.FallThroughToChat
            } else if (appLauncher.launch(app)) {
                NuaRouteResult.ActionTaken(ActionCopy.appOpened(app))
            } else {
                NuaRouteResult.ActionTaken(ActionCopy.appNotFound(app))
            }
        }

        NuaActionType.PLAY_MEDIA -> {
            val query = intent.parameters["query"] ?: originalUtterance
            if (mediaControlManager.playByQuery(query)) {
                NuaRouteResult.ActionTaken(ActionCopy.mediaStarted(query))
            } else {
                NuaRouteResult.ActionTaken("No music app on here picked that up — is one actually installed?")
            }
        }

        NuaActionType.MEDIA_CONTROL -> {
            val handled = when (intent.parameters["command"]) {
                "play" -> mediaControlManager.play()
                "pause" -> mediaControlManager.pause()
                "next" -> mediaControlManager.next()
                "previous" -> mediaControlManager.previous()
                else -> false
            }
            if (handled) {
                NuaRouteResult.ActionTaken(ActionCopy.mediaControlHandled())
            } else {
                NuaRouteResult.ActionTaken("Nothing's playing right now — nothing to control.")
            }
        }

        NuaActionType.READ_NOTIFICATIONS -> {
            NuaRouteResult.ActionTaken(notificationRepository.summary().spokenSummary)
        }

        NuaActionType.REPLY_TO_NOTIFICATION -> {
            val target = intent.parameters["target"]
            val message = intent.parameters["message"]
            if (target.isNullOrBlank() || message.isNullOrBlank()) {
                NuaRouteResult.FallThroughToChat
            } else {
                when (val notification = notificationRepository.findByTarget(target)) {
                    null -> NuaRouteResult.ActionTaken("I don't see a recent notification from \"$target\" to reply to.")
                    else -> if (notification.replyAction == null) {
                        NuaRouteResult.ActionTaken("That notification from ${notification.title} doesn't have a quick-reply NUA can use.")
                    } else {
                        NuaRouteResult.ReplyProposed(notification, message)
                    }
                }
            }
        }

        NuaActionType.GET_WEATHER -> {
            val snapshot = weatherRepository.currentSnapshot().getOrNull()
            val message = snapshot?.let { ActionCopy.weather(it.condition, it.currentTempC, it.highTempC, it.precipitationChancePercent) }
                ?: "Couldn't get a weather reading — check that location access is granted."
            NuaRouteResult.ActionTaken(message)
        }

        NuaActionType.MORNING_BRIEFING -> {
            NuaRouteResult.ActionTaken(morningBriefing.generate(originalUtterance, pinnedLanguage))
        }

        NuaActionType.PLAN_TASK -> {
            val activity = intent.parameters["activity"] ?: originalUtterance
            val plan = taskPlanner.propose(activity, pinnedLanguage)
            if (plan != null) NuaRouteResult.PlanProposed(plan) else NuaRouteResult.FallThroughToChat
        }

        NuaActionType.SMART_HOME -> {
            val device = intent.parameters["device"]
            val action = intent.parameters["action"]
            if (device.isNullOrBlank() || action.isNullOrBlank()) {
                NuaRouteResult.FallThroughToChat
            } else {
                val message = when (val result = smartHomeRepository.controlDevice(device, action)) {
                    is SmartHomeResult.Success -> result.message
                    is SmartHomeResult.NotConfigured -> result.reason
                    is SmartHomeResult.Failure -> "Couldn't do that — ${result.message}"
                }
                NuaRouteResult.ActionTaken(message)
            }
        }

        NuaActionType.CHAT -> NuaRouteResult.FallThroughToChat
    }
}

/**
 * Varied phrasing for the fast keyword/classification path, so action confirmations
 * carry a bit of NUA's voice without paying for a Claude call just to phrase "Done."
 * Picking randomly among a few options also keeps repeated actions from reading as
 * canned — the fast path can't evolve tone with familiarity the way chat replies do
 * (see PersonalityEngine), but it doesn't have to sound like a fixed script either.
 */
private object ActionCopy {
    fun appOpened(app: String): String = listOf(
        "Opening $app.",
        "On it — launching $app.",
        "$app, coming right up.",
    ).random()

    fun appNotFound(app: String): String =
        "Couldn't find anything called \"$app\" on here — mistyped, or not installed?"

    fun mediaStarted(query: String): String = listOf(
        "Cueing up \"$query\" for you.",
        "Starting something for \"$query\".",
    ).random()

    fun mediaControlHandled(): String = listOf("Done.", "Handled.", "There you go.").random()

    fun weather(condition: String, currentTempC: Double, highTempC: Double, precipitationChancePercent: Int): String {
        val base = "It's $condition and ${currentTempC.toInt()}°C, high of ${highTempC.toInt()}°C today with a $precipitationChancePercent% chance of rain."
        val remark = when {
            precipitationChancePercent >= 60 -> " Bring an umbrella."
            precipitationChancePercent <= 10 && currentTempC >= 22 -> " Good excuse to get outside."
            else -> ""
        }
        return base + remark
    }
}
