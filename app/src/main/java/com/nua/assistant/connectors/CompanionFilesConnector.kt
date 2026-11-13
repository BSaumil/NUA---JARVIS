package com.nua.assistant.connectors

import com.nua.assistant.presence.companion.CompanionHttpResult
import com.nua.assistant.presence.companion.CompanionHttpTransport
import com.nua.assistant.presence.companion.CompanionSigningIdentity
import com.nua.assistant.presence.companion.CompanionSignedMeta
import com.nua.assistant.presence.companion.signCompanionRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val FILES_LIST_OPERATION = "files.list"
private const val FILES_READ_OPERATION = "files.read"
private const val FILES_READ_SCOPE = "FILES_READ"

@Serializable
private data class CompanionSignedRequestWire(
    val meta: CompanionSignedMeta,
    val scopes: List<String>,
    val operation: String,
    val payload: Map<String, String>,
)

@Serializable
data class CompanionFileEntry(val name: String, val isDir: Boolean, val sizeBytes: Long)

@Serializable
private data class FilesListResponseWire(val succeeded: Boolean = false, val entries: List<CompanionFileEntry> = emptyList(), val error: String? = null)

@Serializable
private data class FilesReadResponseWire(val succeeded: Boolean = false, val contentBase64: String? = null, val error: String? = null)

/**
 * Connector library priority item #10 ("companion mounted-files connector"). Reads files
 * from directories a companion node's *owner* has explicitly configured as named mounts
 * (`NUA_COMPANION_MOUNTS`, services/companion/nua_companion/mounts.py) -- never an
 * arbitrary path, and never the companion's wider filesystem, which the directive this
 * work follows explicitly forbids ("arbitrary remote filesystem access").
 *
 * This never goes through [SafeHttpConnector]: a companion's Local API is authenticated
 * by an Ed25519 signature over a paired device's key (presence/companion/**), not a
 * bearer token, so [ConnectorAuthType.COMPANION_PAIRED] is refused there outright and
 * served here instead, reusing the exact same signing ([signCompanionRequest]) and
 * pairing/transport infrastructure workstream 2 already built and tested.
 */
@Singleton
class CompanionFilesConnector @Inject constructor(
    private val transport: CompanionHttpTransport,
    private val identity: CompanionSigningIdentity,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {
    suspend fun list(mount: String, path: String = ""): ConnectorResult {
        val payload = mapOf("mount" to mount, "path" to path)
        val response = send("/files/list", FILES_LIST_OPERATION, payload)
        val body = response.bodyOrNull() ?: return response.toConnectorResult()
        val parsed = runCatching { json.decodeFromString<FilesListResponseWire>(body) }.getOrNull()
            ?: return ConnectorResult.Failed("MALFORMED_RESPONSE")
        if (!parsed.succeeded) return ConnectorResult.Failed(parsed.error ?: "UNKNOWN_FAILURE")
        return ConnectorResult.ReadSuccess(
            connectorId = "companion-files",
            contentType = "application/json",
            body = json.encodeToString(parsed.entries),
            truncated = false,
        )
    }

    suspend fun read(mount: String, path: String): ConnectorResult {
        val payload = mapOf("mount" to mount, "path" to path)
        val response = send("/files/read", FILES_READ_OPERATION, payload)
        val body = response.bodyOrNull() ?: return response.toConnectorResult()
        val parsed = runCatching { json.decodeFromString<FilesReadResponseWire>(body) }.getOrNull()
            ?: return ConnectorResult.Failed("MALFORMED_RESPONSE")
        if (!parsed.succeeded || parsed.contentBase64 == null) return ConnectorResult.Failed(parsed.error ?: "UNKNOWN_FAILURE")
        return ConnectorResult.ReadSuccess(
            connectorId = "companion-files",
            contentType = "application/octet-stream;base64",
            body = parsed.contentBase64,
            truncated = false,
        )
    }

    private suspend fun send(path: String, operation: String, payload: Map<String, String>): CompanionHttpResult {
        val meta = signCompanionRequest(identity, operation, payload)
        val request = json.encodeToString(CompanionSignedRequestWire(meta, listOf(FILES_READ_SCOPE), operation, payload))
        return transport.post(path, request)
    }

    private fun CompanionHttpResult.bodyOrNull(): String? = when (this) {
        is CompanionHttpResult.Success -> bodyJson
        is CompanionHttpResult.Failure -> null
        is CompanionHttpResult.Unreachable -> null
    }

    private fun CompanionHttpResult.toConnectorResult(): ConnectorResult = when (this) {
        is CompanionHttpResult.Success -> ConnectorResult.Failed("MALFORMED_RESPONSE") // unreachable: bodyOrNull() already handled Success
        is CompanionHttpResult.Failure -> ConnectorResult.Refused("companion refused: HTTP_$statusCode")
        is CompanionHttpResult.Unreachable -> ConnectorResult.Failed("UNREACHABLE: $reason")
    }
}
