package com.nua.assistant.presence.companion

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val INFERENCE_OPERATION = "inference.start"
private const val INFERENCE_SCOPE = "INFERENCE"

@Serializable
private data class InferenceRequestWire(
    val meta: CompanionSignedMeta,
    val scopes: List<String>,
    val operation: String,
    val payload: Map<String, String>,
    val payloadJson: String,
)

@Serializable
private data class InferenceResponseWire(
    val succeeded: Boolean = false,
    val text: String? = null,
    val provider: String? = null,
    val error: String? = null,
    val errorCode: String? = null,
)

sealed interface InferenceOutcome {
    data class Succeeded(val text: String, val provider: String?) : InferenceOutcome
    data class Refused(val reason: String) : InferenceOutcome
}

/**
 * Authenticated calls against a paired companion node. Every privileged request carries
 * a `meta` envelope signed over the exact canonical string
 * services/companion/nua_companion/security.py's `_authorize` reconstructs
 * (see [CompanionSecurity]) -- this is what the companion checks before honoring any
 * request, so a request this client did not sign with an approved device's key is
 * refused by the companion itself, not merely by convention on this side.
 */
class CompanionApiClient(
    private val transport: CompanionHttpTransport,
    private val identity: CompanionSigningIdentity,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {
    suspend fun inference(prompt: String, modelId: String = ""): InferenceOutcome {
        val payload = mapOf("modelId" to modelId, "prompt" to prompt)
        val signed = signCompanionRequest(identity, INFERENCE_OPERATION, payload)
        val body = json.encodeToString(
            InferenceRequestWire(signed.meta, listOf(INFERENCE_SCOPE), INFERENCE_OPERATION, payload, signed.payloadJson),
        )

        val result = transport.post("/inference", body)
        val responseJson = when (result) {
            is CompanionHttpResult.Success -> result.bodyJson
            is CompanionHttpResult.Failure -> result.bodyJson
            is CompanionHttpResult.Unreachable -> return InferenceOutcome.Refused("UNREACHABLE: ${result.reason}")
        }
        val response = runCatching { json.decodeFromString<InferenceResponseWire>(responseJson) }.getOrNull()
            ?: return InferenceOutcome.Refused("MALFORMED_RESPONSE")

        if (!response.succeeded) {
            return InferenceOutcome.Refused(response.errorCode ?: response.error ?: "UNKNOWN_FAILURE")
        }
        return InferenceOutcome.Succeeded(response.text.orEmpty(), response.provider)
    }
}
