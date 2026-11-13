package com.nua.assistant.connectors

import com.nua.assistant.security.egress.DataCategory
import kotlinx.serialization.Serializable

@Serializable
enum class ConnectorAuthType { NONE, BEARER_TOKEN, OAUTH2_EXTERNAL }

@Serializable
enum class ConnectorRisk { READ_ONLY, WRITE_CAPABLE }

@Serializable
data class ConnectorManifest(
    val id: String,
    val displayName: String,
    val version: String,
    val authType: ConnectorAuthType,
    val allowedHosts: Set<String>,
    val readCapabilities: Set<String>,
    val writeCapabilities: Set<String> = emptySet(),
    val dataCategories: Set<DataCategory> = emptySet(),
    val remoteEndpointIdForWrites: String? = null,
)

data class ConnectorRequest(
    val capability: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
)

sealed interface ConnectorResult {
    data class ReadSuccess(
        val connectorId: String,
        val contentType: String,
        val body: String,
        val truncated: Boolean,
    ) : ConnectorResult

    data class RequiresUaf(
        val actionName: String,
        val parameters: Map<String, String>,
        val reason: String,
    ) : ConnectorResult

    data class Refused(val reason: String) : ConnectorResult
    data class Failed(val code: String) : ConnectorResult
}

data class ConnectorValidation(val allowed: Boolean, val reason: String? = null)

private val CONNECTOR_ID = Regex("""[a-z0-9][a-z0-9._-]{1,63}""")

fun validateConnectorManifest(manifest: ConnectorManifest): ConnectorValidation {
    if (!CONNECTOR_ID.matches(manifest.id)) return ConnectorValidation(false, "invalid connector id")
    if (manifest.displayName.isBlank()) return ConnectorValidation(false, "display name required")
    if (manifest.allowedHosts.isEmpty()) return ConnectorValidation(false, "at least one exact host is required")
    if (manifest.allowedHosts.any { it.isBlank() || '/' in it || ':' in it || '@' in it }) {
        return ConnectorValidation(false, "allowed hosts must be exact DNS host names without scheme/port/path")
    }
    if (manifest.readCapabilities.isEmpty() && manifest.writeCapabilities.isEmpty()) {
        return ConnectorValidation(false, "connector declares no capabilities")
    }
    if (manifest.writeCapabilities.isNotEmpty() && manifest.remoteEndpointIdForWrites.isNullOrBlank()) {
        return ConnectorValidation(false, "write-capable connector requires a governed remote endpoint")
    }
    return ConnectorValidation(true)
}

fun writeProposal(
    manifest: ConnectorManifest,
    capability: String,
    argumentsJson: String,
    privacyCapsuleJson: String,
    idempotencyKey: String,
): ConnectorResult {
    if (capability !in manifest.writeCapabilities) return ConnectorResult.Refused("write capability not declared")
    val endpoint = manifest.remoteEndpointIdForWrites
        ?: return ConnectorResult.Refused("no governed remote endpoint configured")
    return ConnectorResult.RequiresUaf(
        actionName = "REMOTE_TOOL",
        parameters = mapOf(
            "endpointId" to endpoint,
            "protocol" to "MCP",
            "tool" to capability,
            "argumentsJson" to argumentsJson,
            "privacyCapsuleJson" to privacyCapsuleJson,
            "idempotencyKey" to idempotencyKey,
        ),
        reason = "Connector writes execute only through UAF REMOTE_TOOL.",
    )
}
