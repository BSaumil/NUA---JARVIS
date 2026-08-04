package com.nua.assistant.automation

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType

/**
 * Free, local keyword matching for unambiguous requests — no Claude round trip. Kept as
 * a pure function (not a class method on NuaIntentRouter) so it's unit-testable without
 * mocking every Tier 1 manager the router depends on.
 */
object KeywordIntentMatcher {

    fun match(utterance: String): ClassifiedIntent? {
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
}
