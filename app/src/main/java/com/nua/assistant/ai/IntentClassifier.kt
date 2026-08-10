package com.nua.assistant.ai

import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The concrete actions NUA can take. Kept in sync with what NuaIntentRouter can dispatch. */
enum class NuaActionType {
    OPEN_APP,
    PLAY_MEDIA,
    MEDIA_CONTROL,
    READ_NOTIFICATIONS,
    REPLY_TO_NOTIFICATION,
    GET_WEATHER,
    MORNING_BRIEFING,
    PLAN_TASK,
    SMART_HOME,
    EMAIL,
    /** No concrete action fits — fall through to a normal conversational reply. */
    CHAT,
}

data class ClassifiedIntent(
    val action: NuaActionType,
    val confidence: Double,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
private data class IntentClassificationDto(
    val action: String = "CHAT",
    val confidence: Double = 0.0,
    val parameters: Map<String, String> = emptyMap(),
)

private val CLASSIFIER_SYSTEM_PROMPT = """
    You classify a user's spoken or typed request into exactly one NUA action. Reply
    with JSON only, no prose, matching this shape:
    {"action": "<ACTION>", "confidence": <0.0-1.0>, "parameters": {"...": "..."}}

    Valid actions:
    - OPEN_APP: user wants an app opened. parameters: {"app": "<app name>"}
    - PLAY_MEDIA: user wants music/audio matching a mood or activity started.
      parameters: {"query": "<what to play, e.g. 'energetic workout music'>"}
    - MEDIA_CONTROL: play/pause/skip/volume on whatever is already playing.
      parameters: {"command": "play|pause|next|previous"}
    - READ_NOTIFICATIONS: user wants their notifications read or summarized.
    - REPLY_TO_NOTIFICATION: user wants to reply to a specific message notification
      (e.g. "reply to Sam saying I'm on my way"). parameters: {"target": "<who/what the
      notification is from, e.g. a name or app>", "message": "<reply text, in the same
      language/wording the user used>"}. Only use this when there's a clear target and
      message; otherwise CHAT.
    - GET_WEATHER: user wants a weather check.
    - MORNING_BRIEFING: user wants their morning rundown.
    - PLAN_TASK: user described an upcoming activity/trip/event that would benefit
      from a short plan (e.g. "I'm going camping tomorrow"). parameters: {"activity": "..."}
    - SMART_HOME: user wants to control a smart-home device (lights, thermostat, plugs,
      locks). parameters: {"device": "<device/room name>", "action": "on|off|<other>"}
    - EMAIL: user wants their email checked or read ("any new emails?", "check my inbox").
    - CHAT: nothing above fits, or the request is purely conversational — this includes
      mood/vibe statements with no obvious action ("I'm bored", "I had a rough day")
      unless they clearly imply one of the actions above (e.g. "I'm bored" alone is
      CHAT, but "I'm bored, put some music on" is PLAY_MEDIA).

    If you're not reasonably confident (>0.6), prefer CHAT with a low confidence score
    rather than guessing at an action.

    The request may be in any of: ${NuaLanguage.supportedNames()} — understand it
    regardless of language. Keep any extracted text parameters ("app", "query",
    "activity") in the same language and wording the user used; don't translate them.
""".trimIndent()

/**
 * Claude-based fallback for ambiguous or conversational requests that
 * NuaIntentRouter's keyword fast path doesn't match. Only called when the free,
 * local keyword match misses — every call here is a paid API round trip.
 */
@Singleton
class IntentClassifier @Inject constructor(
    private val claudeApiClient: ClaudeApiClient,
    private val json: Json,
) {

    suspend fun classify(utterance: String): ClassifiedIntent? {
        val result = claudeApiClient.complete(
            userPrompt = utterance,
            system = CLASSIFIER_SYSTEM_PROMPT,
            model = CLAUDE_MODEL_UTILITY,
            maxTokens = 256,
        )

        val text = (result as? ClaudeResult.Success)?.text ?: return null

        val dto = runCatching {
            json.decodeFromString(IntentClassificationDto.serializer(), extractJsonPayload(text))
        }.getOrNull() ?: return null

        val action = runCatching { NuaActionType.valueOf(dto.action) }.getOrDefault(NuaActionType.CHAT)
        return ClassifiedIntent(action = action, confidence = dto.confidence, parameters = dto.parameters)
    }
}
