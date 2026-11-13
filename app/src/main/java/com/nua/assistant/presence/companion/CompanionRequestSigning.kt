package com.nua.assistant.presence.companion

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class CompanionSignedMeta(
    val protocolVersion: Int,
    val requestId: String,
    val clientId: String,
    val nonce: String,
    val timestampMillis: Long,
    val signatureBase64: String,
)

/**
 * Builds the signed `meta` envelope every privileged companion request carries, matching
 * the companion's own `_authorize` exactly (see [CompanionSecurity]). Shared by every
 * companion-paired client capability -- [CompanionApiClient]'s inference calls,
 * [com.nua.assistant.connectors.CompanionFilesConnector]'s file reads, and any future
 * one -- so the signing logic exists in exactly one place.
 */
fun signCompanionRequest(
    identity: CompanionSigningIdentity,
    operation: String,
    payload: Map<String, String>,
): CompanionSignedMeta {
    val device = identity.identity
    val requestId = UUID.randomUUID().toString()
    val nonce = CompanionSecurity.newNonce()
    val timestampMillis = CompanionSecurity.nowMillis()
    val payloadJson = CompanionSecurity.compactJson(payload)
    val digest = CompanionSecurity.digestRequest(
        device.protocolVersion,
        requestId,
        device.deviceId,
        nonce,
        timestampMillis,
        operation,
        payloadJson,
    )
    return CompanionSignedMeta(
        protocolVersion = device.protocolVersion,
        requestId = requestId,
        clientId = device.deviceId,
        nonce = nonce,
        timestampMillis = timestampMillis,
        signatureBase64 = identity.sign(digest),
    )
}
