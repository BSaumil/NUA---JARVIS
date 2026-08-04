package com.nua.assistant.ai

import com.nua.assistant.memory.SecureKeyRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

        val requestBody = ClaudeRequest(
            model = model,
            maxTokens = maxTokens,
            system = system,
            messages = messages,
            temperature = temperature,
        )

        val request = Request.Builder()
            .url(ANTHROPIC_ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .post(json.encodeToString(ClaudeRequest.serializer(), requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        json.decodeFromString(ClaudeErrorEnvelope.serializer(), bodyString).error.message
                    }.getOrDefault("HTTP ${response.code}")
                    return@withContext ClaudeResult.Failure(message)
                }
                val parsed = json.decodeFromString(ClaudeResponse.serializer(), bodyString)
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
}
