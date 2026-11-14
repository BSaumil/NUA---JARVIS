package com.nua.assistant.presence.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class NuaPlatform { ANDROID_PHONE, ANDROID_TABLET, WEAR_OS, ANDROID_AUTO, WINDOWS, MACOS, LINUX, IOS, IPADOS, UNKNOWN }

@Serializable
enum class PresenceTrustState { PENDING, APPROVED, REVOKED, EXPIRED }

@Serializable
data class DeviceIdentity(
    val deviceId: String,
    val publicKey: String,
    val ownerLabel: String,
    val platform: NuaPlatform,
    val appVersion: String,
    val keyAlgorithm: String = "ED25519",
    val publicKeyEncoding: String = "RAW",
    val protocolVersion: Int = 1,
)

@Serializable
data class DeviceCapability(
    val id: String,
    val version: Int = 1,
    val readOnly: Boolean,
    val requiresOwnerApproval: Boolean,
)

@Serializable
data class ModelAdvertisement(
    val id: String,
    val provider: String,
    val localToNode: Boolean,
    val supportsVision: Boolean = false,
    val supportsStructuredOutput: Boolean = false,
    val maxContextTokens: Int? = null,
)

@Serializable
data class ConnectorAdvertisement(
    val id: String,
    val version: String,
    val readCapabilities: Set<String>,
    val writeCapabilities: Set<String>,
)

@Serializable
data class PresenceState(
    val identity: DeviceIdentity,
    val trustState: PresenceTrustState,
    val capabilities: List<DeviceCapability>,
    val models: List<ModelAdvertisement> = emptyList(),
    val connectors: List<ConnectorAdvertisement> = emptyList(),
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val thermalClass: String? = null,
    val networkClass: String? = null,
    val lastVerifiedAtMillis: Long,
)

@Serializable
data class PairingChallenge(
    val challengeId: String,
    val challenger: DeviceIdentity,
    val nonceBase64: String,
    val expiresAtMillis: Long,
)

@Serializable
data class PairingApproval(
    val challengeId: String,
    val approvedDeviceId: String,
    val signatureBase64: String,
    val approvedAtMillis: Long,
)

@Serializable
data class RevocationRecord(
    val deviceId: String,
    val revokedAtMillis: Long,
    val reason: String,
)

@Serializable
data class ComputeJobRequest(
    val jobId: String,
    val capability: String,
    val payloadJson: String,
    val deadlineMillis: Long? = null,
    val idempotencyKey: String,
)

@Serializable
data class ComputeJobResult(
    val jobId: String,
    val succeeded: Boolean,
    val resultJson: String? = null,
    val errorCode: String? = null,
)

@Serializable
data class ResearchJob(val jobId: String, val question: String, val mode: String, val status: String)

@Serializable
data class SandboxJob(
    val jobId: String,
    val runtime: String,
    val programDigest: String,
    val inputManifestJson: String,
    val status: String,
)
