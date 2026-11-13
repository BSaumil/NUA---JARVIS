package com.nua.assistant.remote

import com.nua.assistant.ai.executeCancellably
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val REMOTE_JSON = "application/json".toMediaType()

interface McpTransport {
    suspend fun invoke(trust: RemoteEndpointTrust, request: RemoteInvocationRequest): RemoteInvocationResult
}

interface A2ATransport {
    suspend fun invoke(trust: RemoteEndpointTrust, request: RemoteInvocationRequest): RemoteInvocationResult
}

private fun clientFor(base: OkHttpClient, trust: RemoteEndpointTrust): OkHttpClient {
    val builder = base.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
    val pin = trust.manifest.tlsPinSha256
    val host = URI(trust.manifest.baseUrl).host
    if (!pin.isNullOrBlank() && !host.isNullOrBlank()) {
        builder.certificatePinner(CertificatePinner.Builder().add(host, pin).build())
    }
    return builder.build()
}

@Singleton
class SovereignMcpClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : McpTransport {
    override suspend fun invoke(trust: RemoteEndpointTrust, request: RemoteInvocationRequest): RemoteInvocationResult =
        withContext(Dispatchers.IO) {
            val args = runCatching { json.parseToJsonElement(request.argumentsJson) }.getOrNull()
                ?: return@withContext RemoteInvocationResult(false, errorCode = "INVALID_ARGUMENTS_JSON")
            val body = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(request.requestId))
                put("method", JsonPrimitive("tools/call"))
                put("params", buildJsonObject {
                    put("name", JsonPrimitive(request.capability))
                    put("arguments", args)
                    put("_meta", buildJsonObject { put("idempotencyKey", JsonPrimitive(request.idempotencyKey)) })
                })
            }
            val m = trust.manifest
            val url = m.baseUrl.trimEnd('/') + "/" + m.invokePath.trimStart('/')
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("content-type", "application/json")
                .addHeader("x-nua-request-id", request.requestId)
                .addHeader("x-nua-idempotency-key", request.idempotencyKey)
                .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(REMOTE_JSON))
                .build()
            runCatching {
                executeCancellably(clientFor(okHttpClient, trust), httpRequest).use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use RemoteInvocationResult(false, errorCode = "HTTP_${response.code}")
                    val parsed = json.parseToJsonElement(payload).jsonObject
                    if ("error" in parsed) RemoteInvocationResult(false, errorCode = "REMOTE_ERROR")
                    else RemoteInvocationResult(true, outputText = parsed["result"]?.toString()?.take(16_384).orEmpty())
                }
            }.getOrElse { RemoteInvocationResult(false, errorCode = "TRANSPORT_FAILURE") }
        }
}

@Singleton
class SovereignA2AClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : A2ATransport {
    override suspend fun invoke(trust: RemoteEndpointTrust, request: RemoteInvocationRequest): RemoteInvocationResult =
        withContext(Dispatchers.IO) {
            val args = runCatching { json.parseToJsonElement(request.argumentsJson) }.getOrNull()
                ?: return@withContext RemoteInvocationResult(false, errorCode = "INVALID_ARGUMENTS_JSON")
            val body = buildJsonObject {
                put("protocol", JsonPrimitive("nua-a2a/1"))
                put("requestId", JsonPrimitive(request.requestId))
                put("capability", JsonPrimitive(request.capability))
                put("idempotencyKey", JsonPrimitive(request.idempotencyKey))
                put("arguments", args)
            }
            val m = trust.manifest
            val url = m.baseUrl.trimEnd('/') + "/" + m.invokePath.trimStart('/')
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("content-type", "application/json")
                .addHeader("x-nua-request-id", request.requestId)
                .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(REMOTE_JSON))
                .build()
            runCatching {
                executeCancellably(clientFor(okHttpClient, trust), httpRequest).use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use RemoteInvocationResult(false, errorCode = "HTTP_${response.code}")
                    val parsed = json.parseToJsonElement(payload).jsonObject
                    val ok = parsed["succeeded"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val output = parsed["result"]?.toString()?.take(16_384)
                    if (ok) RemoteInvocationResult(true, outputText = output)
                    else RemoteInvocationResult(false, errorCode = parsed["errorCode"]?.jsonPrimitive?.content ?: "REMOTE_ERROR")
                }
            }.getOrElse { RemoteInvocationResult(false, errorCode = "TRANSPORT_FAILURE") }
        }
}
