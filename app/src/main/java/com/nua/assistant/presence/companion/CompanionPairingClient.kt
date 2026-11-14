package com.nua.assistant.presence.companion

import com.nua.assistant.presence.protocol.PairingChallenge
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class PairChallengeRequest(val identity: IdentityWire)

@Serializable
private data class IdentityWire(
    val deviceId: String,
    val publicKey: String,
    val ownerLabel: String,
    val platform: String,
    val appVersion: String,
    val protocolVersion: Int,
)

@Serializable
private data class PairProveRequest(val challengeId: String, val signatureBase64: String)

@Serializable
private data class PairProveResponse(val verified: Boolean = false, val ownerApprovalRequired: Boolean = false)

sealed interface PairingOutcome {
    data class Proved(val challengeId: String, val ownerApprovalRequired: Boolean) : PairingOutcome
    data class SignatureRejected(val challengeId: String) : PairingOutcome
    data class Failed(val reason: String) : PairingOutcome
}

/**
 * Client side of the companion's pairing flow (POST /pair/challenge, POST
 * /pair/prove -- services/companion/nua_companion/pairing.py). Proving control of this
 * device's private key is NOT the same as being trusted: the companion requires a
 * separate, non-automatic owner_approve on its own side (e.g. via `nua-companion
 * approve`) before this device's signed requests are authorized. This client has no
 * way to grant itself that approval and does not attempt to.
 */
class CompanionPairingClient(
    private val transport: CompanionHttpTransport,
    private val identity: CompanionSigningIdentity,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {
    suspend fun requestPairing(): PairingOutcome {
        val device = identity.identity
        val requestBody = json.encodeToString(
            PairChallengeRequest(
                IdentityWire(
                    deviceId = device.deviceId,
                    publicKey = device.publicKey,
                    ownerLabel = device.ownerLabel,
                    platform = device.platform.name,
                    appVersion = device.appVersion,
                    protocolVersion = device.protocolVersion,
                ),
            ),
        )

        val challengeResult = transport.post("/pair/challenge", requestBody)
        val challengeBody = (challengeResult as? CompanionHttpResult.Success)?.bodyJson
            ?: return PairingOutcome.Failed(describeFailure(challengeResult))
        val challenge = runCatching { json.decodeFromString<PairingChallenge>(challengeBody) }.getOrNull()
            ?: return PairingOutcome.Failed("MALFORMED_CHALLENGE")

        val nonce = runCatching { Base64.getDecoder().decode(challenge.nonceBase64) }.getOrNull()
            ?: return PairingOutcome.Failed("MALFORMED_NONCE")
        val signature = identity.sign(nonce)

        val proveBody = json.encodeToString(PairProveRequest(challenge.challengeId, signature))
        val proveResult = transport.post("/pair/prove", proveBody)
        val proveResponseJson = when (proveResult) {
            is CompanionHttpResult.Success -> proveResult.bodyJson
            is CompanionHttpResult.Failure -> proveResult.bodyJson
            is CompanionHttpResult.Unreachable -> return PairingOutcome.Failed(describeFailure(proveResult))
        }
        val proveResponse = runCatching { json.decodeFromString<PairProveResponse>(proveResponseJson) }.getOrNull()
            ?: return PairingOutcome.Failed("MALFORMED_PROVE_RESPONSE")

        return if (proveResponse.verified) {
            PairingOutcome.Proved(challenge.challengeId, proveResponse.ownerApprovalRequired)
        } else {
            PairingOutcome.SignatureRejected(challenge.challengeId)
        }
    }

    private fun describeFailure(result: CompanionHttpResult): String = when (result) {
        is CompanionHttpResult.Success -> "unexpected" // unreachable: caller only passes non-Success here
        is CompanionHttpResult.Failure -> "HTTP_${result.statusCode}"
        is CompanionHttpResult.Unreachable -> "UNREACHABLE: ${result.reason}"
    }
}
