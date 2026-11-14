package com.nua.assistant.presence.companion

import com.nua.assistant.ai.executeCancellably
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

sealed interface CompanionHttpResult {
    data class Success(val bodyJson: String) : CompanionHttpResult
    data class Failure(val statusCode: Int, val bodyJson: String) : CompanionHttpResult
    data class Unreachable(val reason: String) : CompanionHttpResult
}

/**
 * Real-vs-fake inversion for the one call this package can't make itself: reaching a
 * companion node over the network. [CompanionPairingClient] and [CompanionApiClient]'s own
 * pairing/signing/authorization logic is fully unit-tested against a fake implementing
 * this; [CompanionOkHttpTransport] is the real implementation, itself exercised against a
 * real HTTP server (MockWebServer) in CompanionOkHttpTransportTest.
 */
interface CompanionHttpTransport {
    suspend fun get(path: String): CompanionHttpResult
    suspend fun post(path: String, bodyJson: String): CompanionHttpResult
}

class CompanionOkHttpTransport(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) : CompanionHttpTransport {

    override suspend fun get(path: String): CompanionHttpResult {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + path).get().build()
        return execute(request)
    }

    override suspend fun post(path: String, bodyJson: String): CompanionHttpResult {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): CompanionHttpResult {
        return try {
            executeCancellably(client, request).use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    CompanionHttpResult.Success(body)
                } else {
                    CompanionHttpResult.Failure(response.code, body)
                }
            }
        } catch (error: IOException) {
            CompanionHttpResult.Unreachable(error.message ?: "companion unreachable")
        }
    }
}
