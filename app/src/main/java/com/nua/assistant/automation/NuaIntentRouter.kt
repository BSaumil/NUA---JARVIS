package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.IntentClassifier
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.ai.TaskPlanner
import com.nua.assistant.briefing.MorningBriefing
import com.nua.assistant.media.MediaControlManager
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.weather.WeatherRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Below this confidence, a Claude-classified intent isn't acted on — falls through to chat instead. */
private const val CLASSIFICATION_CONFIDENCE_THRESHOLD = 0.6

sealed class NuaRouteResult {
    data class ActionTaken(val message: String) : NuaRouteResult()
    data class PlanProposed(val plan: TaskPlan) : NuaRouteResult()
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
) {

    suspend fun route(utterance: String): NuaRouteResult {
        matchKeywords(utterance)?.let { return dispatch(it, utterance) }

        val classified = intentClassifier.classify(utterance) ?: return NuaRouteResult.FallThroughToChat
        if (classified.action == NuaActionType.CHAT || classified.confidence < CLASSIFICATION_CONFIDENCE_THRESHOLD) {
            return NuaRouteResult.FallThroughToChat
        }
        return dispatch(classified, utterance)
    }

    private fun matchKeywords(utterance: String): ClassifiedIntent? {
        val lower = utterance.trim().lowercase()
        return when {
            lower.startsWith("open ") -> ClassifiedIntent(NuaActionType.OPEN_APP, 1.0, mapOf("app" to lower.removePrefix("open ").trim()))
            lower.startsWith("launch ") -> ClassifiedIntent(NuaActionType.OPEN_APP, 1.0, mapOf("app" to lower.removePrefix("launch ").trim()))
            lower.contains("pause") -> ClassifiedIntent(NuaActionType.MEDIA_CONTROL, 1.0, mapOf("command" to "pause"))
            "resume" in lower || lower == "play" -> ClassifiedIntent(NuaActionType.MEDIA_CONTROL, 1.0, mapOf("command" to "play"))
            "skip" in lower || "next song" in lower || "next track" in lower ->
                ClassifiedIntent(NuaActionType.MEDIA_CONTROL, 1.0, mapOf("command" to "next"))
            "previous song" in lower || "last track" in lower || "go back" in lower ->
                ClassifiedIntent(NuaActionType.MEDIA_CONTROL, 1.0, mapOf("command" to "previous"))
            "weather" in lower -> ClassifiedIntent(NuaActionType.GET_WEATHER, 1.0)
            "notification" in lower -> ClassifiedIntent(NuaActionType.READ_NOTIFICATIONS, 1.0)
            "morning briefing" in lower || "brief me" in lower -> ClassifiedIntent(NuaActionType.MORNING_BRIEFING, 1.0)
            else -> null
        }
    }

    private suspend fun dispatch(intent: ClassifiedIntent, originalUtterance: String): NuaRouteResult = when (intent.action) {
        NuaActionType.OPEN_APP -> {
            val app = intent.parameters["app"]
            if (app.isNullOrBlank()) {
                NuaRouteResult.FallThroughToChat
            } else if (appLauncher.launch(app)) {
                NuaRouteResult.ActionTaken("Opening $app.")
            } else {
                NuaRouteResult.ActionTaken("I couldn't find an app matching \"$app\" installed.")
            }
        }

        NuaActionType.PLAY_MEDIA -> {
            val query = intent.parameters["query"] ?: originalUtterance
            if (mediaControlManager.playByQuery(query)) {
                NuaRouteResult.ActionTaken("Starting something for \"$query\".")
            } else {
                NuaRouteResult.ActionTaken("I don't have a music app installed that can handle that.")
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
                NuaRouteResult.ActionTaken("Done.")
            } else {
                NuaRouteResult.ActionTaken("Nothing seems to be playing right now.")
            }
        }

        NuaActionType.READ_NOTIFICATIONS -> {
            NuaRouteResult.ActionTaken(notificationRepository.summary().spokenSummary)
        }

        NuaActionType.GET_WEATHER -> {
            val snapshot = weatherRepository.currentSnapshot().getOrNull()
            val message = snapshot?.let {
                "It's ${it.condition} and ${it.currentTempC.toInt()}°C, high of ${it.highTempC.toInt()}°C today with a ${it.precipitationChancePercent}% chance of rain."
            } ?: "I couldn't get a weather reading — check that location access is granted."
            NuaRouteResult.ActionTaken(message)
        }

        NuaActionType.MORNING_BRIEFING -> {
            NuaRouteResult.ActionTaken(morningBriefing.generate())
        }

        NuaActionType.PLAN_TASK -> {
            val activity = intent.parameters["activity"] ?: originalUtterance
            val plan = taskPlanner.propose(activity)
            if (plan != null) NuaRouteResult.PlanProposed(plan) else NuaRouteResult.FallThroughToChat
        }

        NuaActionType.CHAT -> NuaRouteResult.FallThroughToChat
    }
}
