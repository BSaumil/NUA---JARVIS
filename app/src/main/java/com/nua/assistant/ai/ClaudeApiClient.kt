package com.nua.assistant.ai

import com.nua.assistant.memory.SecureKeyRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

private const val ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

sealed class ClaudeResult {
    data class Success(val text: String) : ClaudeResult()
    data class Failure(val message: String, val cause: Throwable? = null) : ClaudeResult()
}

/**
 * Thin wrapper over the Anthropic Messages API. Every capability in NUA that needs
 * Claude (chat replies, intent classification, fact extraction, planning, notification
 * prioritization) goes through this one client so the API key handling and error
 * surface stays in one place.
 */
@Singleton
class ClaudeApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val secureKeyRepository: SecureKeyRepository,
    private val usageTracker: UsageTracker,
) {

    suspend fun sendMessage(
        messages: List<ClaudeMessage>,
        system: String? = null,
        model: String = CLAUDE_MODEL_CONVERSATION,
        maxTokens: Int = 1024,
        temperature: Double? = null,
    ): ClaudeResult = withContext(Dispatchers.IO) {
        val apiKey = secureKeyRepository.getApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext ClaudeResult.Failure("No Claude API key configured yet.")
        }

        val requestBody = buildRequest(messages, system, model, maxTokens, temperature, stream = false)
        val request = requestBuilder(apiKey, requestBody).build()

        try {
            executeCancellably(okHttpClient, request).use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ClaudeResult.Failure(parseErrorMessage(bodyString, response.code))
                }
                val parsed = json.decodeFromString(ClaudeResponse.serializer(), bodyString)
                usageTracker.record(model, parsed.usage)
                ClaudeResult.Success(parsed.text())
            }
        } catch (t: Exception) {
            ClaudeResult.Failure(t.message ?: "Claude request failed", t)
        }
    }

    /** Convenience for one-off, single-turn utility calls (classification, extraction, planning). */
    suspend fun complete(
        userPrompt: String,
        system: String? = null,
        model: String = CLAUDE_MODEL_UTILITY,
        maxTokens: Int = 512,
    ): ClaudeResult = sendMessage(
        messages = listOf(ClaudeMessage(role = "user", content = userPrompt)),
        system = system,
        model = model,
        maxTokens = maxTokens,
        temperature = 0.0,
    )

    /**
     * Streams the reply as it's generated instead of waiting for the full response —
     * used for the main chat turn so NUA can start speaking/displaying the first
     * sentence while the rest is still generating. Emits [ClaudeStreamEvent.TextDelta]
     * per chunk, a final [ClaudeStreamEvent.Done] with the full text, or
     * [ClaudeStreamEvent.Error] on failure. The flow completes after Done or Error.
     */
    fun streamMessage(
        messages: List<ClaudeMessage>,
        system: String? = null,
        model: String = CLAUDE_MODEL_CONVERSATION,
        maxTokens: Int = 1024,
        temperature: Double? = null,
    ): Flow<ClaudeStreamEvent> = callbackFlow {
        val apiKey = secureKeyRepository.getApiKey()
        if (apiKey.isNullOrBlank()) {
            trySend(ClaudeStreamEvent.Error("No Claude API key configured yet."))
            close()
            return@callbackFlow
        }

        val requestBody = buildRequest(messages, system, model, maxTokens, temperature, stream = true)
        val request = requestBuilder(apiKey, requestBody).build()
        val fullText = StringBuilder()
        var inputTokens = 0
        var outputTokens = 0
        val producerScope = this

        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    when (type) {
                        "message_start" -> {
                            inputTokens = runCatching {
                                json.decodeFromString(ClaudeStreamMessageStart.serializer(), data)
                            }.getOrNull()?.message?.usage?.inputTokens ?: 0
                        }
                        "message_delta" -> {
                            outputTokens = runCatching {
                                json.decodeFromString(ClaudeStreamMessageDelta.serializer(), data)
                            }.getOrNull()?.usage?.outputTokens ?: outputTokens
                        }
                        "content_block_delta" -> {
                            val delta = runCatching {
                                json.decodeFromString(ClaudeStreamEventBody.serializer(), data)
                            }.getOrNull()?.delta?.text
                            if (!delta.isNullOrEmpty()) {
                                fullText.append(delta)
                                trySend(ClaudeStreamEvent.TextDelta(delta))
                            }
                        }
                        "message_stop" -> {
                            producerScope.launch { usageTracker.record(model, ClaudeUsage(inputTokens, outputTokens)) }
                            trySend(ClaudeStreamEvent.Done(fullText.toString()))
                            close()
                        }
                        "error" -> {
                            trySend(ClaudeStreamEvent.Error(parseErrorMessage(data, -1)))
                            close()
                        }
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val message = t?.message ?: response?.let { "HTTP ${it.code}" } ?: "Stream failed"
                    trySend(ClaudeStreamEvent.Error(message))
                    close()
                }
            },
        )

        awaitClose { eventSource.cancel() }
    }.flowOn(Dispatchers.IO)

    /**
     * Sends a single image plus a text prompt to Claude's vision. A separate request
     * shape from [sendMessage] (its `ClaudeMessage.content` is a plain string, no room
     * for an image block) rather than widening that type for every text-only caller.
     */
    suspend fun describeImage(
        imageBase64: String,
        mediaType: String,
        prompt: String,
        system: String? = null,
    ): ClaudeResult = withContext(Dispatchers.IO) {
        val apiKey = secureKeyRepository.getApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext ClaudeResult.Failure("No Claude API key configured yet.")
        }

        val body = ClaudeMultimodalRequest(
            model = CLAUDE_MODEL_CONVERSATION,
            maxTokens = 1024,
            system = system?.takeIf { it.isNotBlank() }?.let { listOf(ClaudeSystemBlock(text = it)) },
            messages = listOf(
                ClaudeMultimodalMessage(
                    role = "user",
                    content = listOf(
                        ClaudeMultimodalBlock(type = "image", source = ClaudeImageSource(mediaType = mediaType, data = imageBase64)),
                        ClaudeMultimodalBlock(type = "text", text = prompt),
                    ),
                ),
            ),
        )
        val request = requestBuilder(apiKey, json.encodeToString(ClaudeMultimodalRequest.serializer(), body)).build()

        try {
            executeCancellably(okHttpClient, request).use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ClaudeResult.Failure(parseErrorMessage(bodyString, response.code))
                }
                val parsed = json.decodeFromString(ClaudeResponse.serializer(), bodyString)
                usageTracker.record(CLAUDE_MODEL_CONVERSATION, parsed.usage)
                ClaudeResult.Success(parsed.text())
            }
        } catch (t: Exception) {
            ClaudeResult.Failure(t.message ?: "Claude request failed", t)
        }
    }

    private fun buildRequest(
        messages: List<ClaudeMessage>,
        system: String?,
        model: String,
        maxTokens: Int,
        temperature: Double?,
        stream: Boolean,
    ) = ClaudeRequest(
        model = model,
        maxTokens = maxTokens,
        system = system?.takeIf { it.isNotBlank() }?.let { listOf(ClaudeSystemBlock(text = it)) },
        messages = messages,
        temperature = temperature,
        stream = stream,
    )

    private fun requestBuilder(apiKey: String, body: ClaudeRequest) =
        requestBuilder(apiKey, json.encodeToString(ClaudeRequest.serializer(), body))

    private fun requestBuilder(apiKey: String, bodyJson: String) = Request.Builder()
        .url(ANTHROPIC_ENDPOINT)
        .addHeader("x-api-key", apiKey)
        .addHeader("anthropic-version", ANTHROPIC_VERSION)
        .addHeader("anthropic-beta", "prompt-caching-2024-07-31")
        .addHeader("content-type", "application/json")
        .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))

    private fun parseErrorMessage(body: String, httpCode: Int): String = runCatching {
        json.decodeFromString(ClaudeErrorEnvelope.serializer(), body).error.message
    }.getOrDefault(if (httpCode >= 0) "HTTP $httpCode" else "Stream error")
}
