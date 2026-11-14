package com.nua.assistant.localapi

import com.nua.assistant.presence.protocol.ComputeJobRequest
import com.nua.assistant.presence.protocol.ComputeJobResult
import com.nua.assistant.presence.protocol.DeviceIdentity
import com.nua.assistant.presence.protocol.PresenceState
import com.nua.assistant.presence.protocol.ResearchJob
import com.nua.assistant.presence.protocol.SandboxJob
import java.security.MessageDigest
import kotlinx.serialization.Serializable

const val NUA_LOCAL_API_VERSION = 1

@Serializable
enum class LocalApiScope {
    HEALTH,
    PRESENCE_READ,
    INFERENCE,
    RESEARCH,
    SANDBOX,
    CONNECTORS_READ,
    CONNECTORS_WRITE,
    AUTHORIZATION,
}

@Serializable
data class LocalApiClientIdentity(
    val clientId: String,
    val device: DeviceIdentity,
    val grantedScopes: Set<LocalApiScope>,
    val keyFingerprint: String,
    val revoked: Boolean = false,
)

@Serializable
data class LocalApiRequestMeta(
    val protocolVersion: Int = NUA_LOCAL_API_VERSION,
    val requestId: String,
    val clientId: String,
    val nonce: String,
    val timestampMillis: Long,
    val signatureBase64: String,
)

@Serializable
data class LocalApiHealth(
    val protocolVersion: Int = NUA_LOCAL_API_VERSION,
    val appVersion: String,
    val deviceId: String,
    val ready: Boolean,
)

@Serializable
data class InferenceJobRequest(
    val jobId: String,
    val prompt: String,
    val system: String? = null,
    val taskType: String,
    val privacySensitivity: String,
    val allowCloud: Boolean,
)

@Serializable
data class InferenceJobResult(
    val jobId: String,
    val succeeded: Boolean,
    val text: String? = null,
    val provider: String? = null,
    val errorCode: String? = null,
)

@Serializable
data class LocalApiEnvelope(
    val meta: LocalApiRequestMeta,
    val operation: String,
    val payloadJson: String,
)

@Serializable
data class LocalApiSnapshot(
    val presence: PresenceState,
    val activeComputeJobs: List<ComputeJobRequest> = emptyList(),
    val recentComputeResults: List<ComputeJobResult> = emptyList(),
    val researchJobs: List<ResearchJob> = emptyList(),
    val sandboxJobs: List<SandboxJob> = emptyList(),
)

enum class LocalApiAuthDecision { ALLOW, REFUSE_VERSION, REFUSE_REVOKED, REFUSE_SCOPE, REFUSE_REPLAY, REFUSE_CLOCK_SKEW }

data class LocalApiAuthResult(
    val decision: LocalApiAuthDecision,
    val reason: String? = null,
)

fun authorizeLocalApiRequest(
    identity: LocalApiClientIdentity?,
    meta: LocalApiRequestMeta,
    requiredScope: LocalApiScope,
    seenNonces: Set<String>,
    nowMillis: Long,
    maxClockSkewMillis: Long = 120_000,
): LocalApiAuthResult {
    if (meta.protocolVersion != NUA_LOCAL_API_VERSION) return LocalApiAuthResult(LocalApiAuthDecision.REFUSE_VERSION)
    if (identity == null || identity.revoked || identity.clientId != meta.clientId) return LocalApiAuthResult(LocalApiAuthDecision.REFUSE_REVOKED)
    if (requiredScope !in identity.grantedScopes) return LocalApiAuthResult(LocalApiAuthDecision.REFUSE_SCOPE)
    if (meta.nonce in seenNonces) return LocalApiAuthResult(LocalApiAuthDecision.REFUSE_REPLAY)
    if (kotlin.math.abs(nowMillis - meta.timestampMillis) > maxClockSkewMillis) return LocalApiAuthResult(LocalApiAuthDecision.REFUSE_CLOCK_SKEW)
    return LocalApiAuthResult(LocalApiAuthDecision.ALLOW)
}

fun localApiRequestCanonicalBytes(
    protocolVersion: Int,
    requestId: String,
    clientId: String,
    nonce: String,
    timestampMillis: Long,
    operation: String,
    payloadJson: String,
): ByteArray =
    listOf(
        protocolVersion.toString(),
        requestId,
        clientId,
        nonce,
        timestampMillis.toString(),
        operation,
        payloadJson,
    ).joinToString("|").toByteArray(Charsets.UTF_8)

fun localApiRequestDigestBytes(
    protocolVersion: Int,
    requestId: String,
    clientId: String,
    nonce: String,
    timestampMillis: Long,
    operation: String,
    payloadJson: String,
): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(
        localApiRequestCanonicalBytes(
            protocolVersion,
            requestId,
            clientId,
            nonce,
            timestampMillis,
            operation,
            payloadJson,
        ),
    )

fun localApiRequestDigest(meta: LocalApiRequestMeta, operation: String, payloadJson: String): String =
    localApiRequestDigestBytes(
        meta.protocolVersion,
        meta.requestId,
        meta.clientId,
        meta.nonce,
        meta.timestampMillis,
        operation,
        payloadJson,
    ).joinToString("") { "%02x".format(it) }

/**
 * Canonical operation names. Platform services may expose these over HTTP/WebSocket,
 * named pipes or another authenticated transport, but the payload and scope semantics
 * must stay identical.
 */
object LocalApiOperations {
    const val HEALTH = "health"
    const val PRESENCE = "presence.snapshot"
    const val INFERENCE_START = "inference.start"
    const val COMPUTE_START = "compute.start"
    const val RESEARCH_START = "research.start"
    const val SANDBOX_START = "sandbox.start"
    const val JOB_STATUS = "job.status"
    const val JOB_CANCEL = "job.cancel"
    const val CONNECTOR_HEALTH = "connector.health"
    const val AUTHORIZATION_PENDING = "authorization.pending"
}
