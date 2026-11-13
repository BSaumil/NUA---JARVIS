package com.nua.assistant.remote

import com.nua.assistant.security.egress.PrivacyCapsule
import com.nua.assistant.security.egress.capsuleAuthorizes
import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
enum class RemoteProtocol { MCP, A2A }

@Serializable
data class RemoteEndpointManifest(
    val id: String,
    val displayName: String,
    val protocol: RemoteProtocol,
    val baseUrl: String,
    val invokePath: String = if (protocol == RemoteProtocol.MCP) "/mcp" else "/a2a",
    val allowedCapabilities: Set<String>,
    val tlsPinSha256: String? = null,
    val version: Int = 1,
)

@Serializable
data class RemoteEndpointTrust(
    val manifest: RemoteEndpointManifest,
    val approvedFingerprint: String,
    val approvedAtMillis: Long,
    val revokedAtMillis: Long? = null,
) {
    val isRevoked: Boolean get() = revokedAtMillis != null
}

@Serializable
data class RemoteInvocationRequest(
    val requestId: String,
    val endpointId: String,
    val capability: String,
    val argumentsJson: String,
    val idempotencyKey: String,
)

@Serializable
data class RemoteInvocationResult(
    val succeeded: Boolean,
    val outputText: String? = null,
    val errorCode: String? = null,
)

data class RemoteInvocationValidation(val allowed: Boolean, val reason: String? = null)

fun remoteEndpointFingerprint(manifest: RemoteEndpointManifest): String {
    val canonical = listOf(
        manifest.id,
        manifest.protocol.name,
        manifest.baseUrl.trimEnd('/'),
        manifest.invokePath,
        manifest.allowedCapabilities.sorted().joinToString(","),
        manifest.tlsPinSha256.orEmpty(),
        manifest.version.toString(),
    ).joinToString("|")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private val SECRET_FIELD = Regex(
    """(?i)"[^"]*(api.?key|access.?token|refresh.?token|password|secret|private.?key)[^"]*"\s*:""",
)

fun validateRemoteInvocation(
    trust: RemoteEndpointTrust?,
    expectedProtocol: RemoteProtocol,
    capability: String,
    argumentsJson: String,
    capsule: PrivacyCapsule?,
    nowMillis: Long = System.currentTimeMillis(),
): RemoteInvocationValidation {
    if (trust == null) return RemoteInvocationValidation(false, "endpoint is not approved")
    if (trust.isRevoked) return RemoteInvocationValidation(false, "endpoint is revoked")
    val manifest = trust.manifest
    if (manifest.protocol != expectedProtocol) return RemoteInvocationValidation(false, "protocol mismatch")
    if (remoteEndpointFingerprint(manifest) != trust.approvedFingerprint) return RemoteInvocationValidation(false, "endpoint identity changed after approval")
    if (capability !in manifest.allowedCapabilities) return RemoteInvocationValidation(false, "capability is not approved for this endpoint")
    if (argumentsJson.toByteArray().size > 64 * 1024) return RemoteInvocationValidation(false, "remote payload exceeds 64 KiB")
    if (SECRET_FIELD.containsMatchIn(argumentsJson)) return RemoteInvocationValidation(false, "payload appears to contain a credential or secret")

    val uri = runCatching { URI(manifest.baseUrl) }.getOrNull()
        ?: return RemoteInvocationValidation(false, "invalid endpoint URL")
    val localHost = uri.host in setOf("127.0.0.1", "localhost", "::1")
    if (uri.scheme != "https" && !(localHost && uri.scheme == "http")) {
        return RemoteInvocationValidation(false, "remote endpoint must use HTTPS; HTTP is localhost-only")
    }
    if (!localHost && manifest.tlsPinSha256.isNullOrBlank()) {
        return RemoteInvocationValidation(false, "non-local endpoint requires an approved TLS pin")
    }

    if (capsule == null) return RemoteInvocationValidation(false, "network egress requires a privacy capsule")
    if (capsule.recipientProvider != manifest.id) return RemoteInvocationValidation(false, "privacy capsule recipient does not match endpoint")
    if (!capsule.networkEgressPermitted) return RemoteInvocationValidation(false, "privacy capsule forbids network egress")
    if (capsule.permittedCategories.isEmpty()) return RemoteInvocationValidation(false, "privacy capsule permits no data categories")
    if (capsule.permittedCategories.none { capsuleAuthorizes(capsule, it, nowMillis) }) {
        return RemoteInvocationValidation(false, "privacy capsule is expired or invalid")
    }
    return RemoteInvocationValidation(true)
}
