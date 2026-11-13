package com.nua.assistant.presence

import android.os.Build
import android.util.Base64
import com.nua.assistant.localapi.LocalApiRequestMeta
import com.nua.assistant.localapi.NUA_LOCAL_API_VERSION
import com.nua.assistant.presence.protocol.DeviceIdentity
import com.nua.assistant.presence.protocol.PairingChallenge
import com.nua.assistant.presence.protocol.PresenceState
import java.net.URI
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA = "application/json".toMediaType()

@Serializable
private data class CompanionIdentityProof(
    val identity: DeviceIdentity,
    val challengeSignatureBase64: String? = null,
)

@Serializable
private data class PairProofResponse(
    val verified: Boolean,
    val ownerApprovalRequired: Boolean,
)

sealed interface PairingResult {
    data class PendingOwnerApproval(
        val challengeId: String,
        val companion: DeviceIdentity,
    ) : PairingResult

    data class Refused(val reason: String) : PairingResult
}

data class PresenceEndpointValidation(
    val allowed: Boolean,
    val local: Boolean = false,
    val reason: String? = null,
)

fun validatePresenceEndpoint(baseUrl: String, tlsPinSha256: String?): PresenceEndpointValidation {
    val uri = runCatching { URI(baseUrl) }.getOrNull()
        ?: return PresenceEndpointValidation(false, reason = "invalid endpoint")
    val host = uri.host?.lowercase() ?: return PresenceEndpointValidation(false, reason = "host required")
    val local = host in setOf("localhost", "127.0.0.1", "::1")
    if (local && uri.scheme == "http") return PresenceEndpointValidation(true, local = true)
    if (uri.scheme != "https") return PresenceEndpointValidation(false, reason = "non-local Presence requires HTTPS")
    if (!local && tlsPinSha256.isNullOrBlank()) {
        return PresenceEndpointValidation(false, reason = "non-local Presence requires certificate pin")
    }
    return PresenceEndpointValidation(true, local = local)
}

fun canonicalJson(element: JsonElement): String = when (element) {
    is JsonObject -> element.entries.sortedBy { it.key }.joinToString(
        prefix = "{",
        postfix = "}",
        separator = ",",
    ) { (key, value) -> Json.encodeToString(key) + ":" + canonicalJson(value) }
    is kotlinx.serialization.json.JsonArray -> element.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ",",
    ) { canonicalJson(it) }
    else -> element.toString()
}

private fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

private fun remoteRequestDigest(
    meta: LocalApiRequestMeta,
    operation: String,
    payloadJson: String,
): ByteArray {
    val canonical = listOf(
        meta.protocolVersion.toString(),
        meta.requestId,
        meta.clientId,
        meta.nonce,
        meta.timestampMillis.toString(),
        operation,
        payloadJson,
    ).joinToString("|")
    return sha256(canonical.toByteArray(Charsets.UTF_8))
}

@Singleton
class PresencePairingClient @Inject constructor(
    private val baseClient: OkHttpClient,
    private val json: Json,
    private val identityStore: AndroidPresenceIdentity,
) {
    suspend fun pair(
        baseUrl: String,
        tlsPinSha256: String?,
        ownerLabel: String = Build.MODEL,
    ): PairingResult = withContext(Dispatchers.IO) {
        val endpoint = validatePresenceEndpoint(baseUrl, tlsPinSha256)
        if (!endpoint.allowed) return@withContext PairingResult.Refused(endpoint.reason ?: "endpoint refused")
        val client = clientFor(baseUrl, tlsPinSha256)
        val challengeBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val challenge = Base64.encodeToString(challengeBytes, Base64.NO_WRAP)

        val proofRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/identity")
            .header("x-nua-challenge", challenge)
            .get()
            .build()
        val proof = runCatching {
            client.newCall(proofRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.decodeFromString<CompanionIdentityProof>(response.body?.string().orEmpty())
            }
        }.getOrNull() ?: return@withContext PairingResult.Refused("companion identity unavailable")

        if (!verifyCompanionIdentity(proof.identity, challenge.toByteArray(), proof.challengeSignatureBase64)) {
            return@withContext PairingResult.Refused("companion identity proof failed")
        }

        val localIdentity = identityStore.identity(ownerLabel)
        val challengeBody = buildJsonObject {
            put("identity", json.encodeToJsonElement(DeviceIdentity.serializer(), localIdentity))
        }
        val pairRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/pair/challenge")
            .post(challengeBody.toString().toRequestBody(JSON_MEDIA))
            .build()
        val pairingChallenge = runCatching {
            client.newCall(pairRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.decodeFromString<PairingChallenge>(response.body?.string().orEmpty())
            }
        }.getOrNull() ?: return@withContext PairingResult.Refused("pairing challenge failed")

        val signature = identityStore.sign(Base64.decode(pairingChallenge.nonceBase64, Base64.DEFAULT))
        val proveBody = buildJsonObject {
            put("challengeId", pairingChallenge.challengeId)
            put("signatureBase64", signature)
        }
        val proveRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/pair/prove")
            .post(proveBody.toString().toRequestBody(JSON_MEDIA))
            .build()
        val result = runCatching {
            client.newCall(proveRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.decodeFromString<PairProofResponse>(response.body?.string().orEmpty())
            }
        }.getOrNull()

        if (result?.verified != true || result.ownerApprovalRequired != true) {
            PairingResult.Refused("device proof was not accepted")
        } else {
            PairingResult.PendingOwnerApproval(pairingChallenge.challengeId, proof.identity)
        }
    }

    suspend fun fetchPresence(
        baseUrl: String,
        tlsPinSha256: String?,
    ): PresenceState? = withContext(Dispatchers.IO) {
        val endpoint = validatePresenceEndpoint(baseUrl, tlsPinSha256)
        if (!endpoint.allowed) return@withContext null
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/presence").get().build()
        runCatching {
            clientFor(baseUrl, tlsPinSha256).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.decodeFromString<PresenceState>(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    suspend fun signedPost(
        baseUrl: String,
        tlsPinSha256: String?,
        path: String,
        operation: String,
        payload: JsonElement,
    ): String? = withContext(Dispatchers.IO) {
        val endpoint = validatePresenceEndpoint(baseUrl, tlsPinSha256)
        if (!endpoint.allowed) return@withContext null
        val identity = identityStore.identity()
        val payloadJson = canonicalJson(payload)
        val unsignedMeta = LocalApiRequestMeta(
            requestId = UUID.randomUUID().toString(),
            clientId = identity.deviceId,
            nonce = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            signatureBase64 = "",
        )
        val signature = identityStore.sign(remoteRequestDigest(unsignedMeta, operation, payloadJson))
        val meta = unsignedMeta.copy(signatureBase64 = signature)
        val body = buildJsonObject {
            put("meta", json.encodeToJsonElement(LocalApiRequestMeta.serializer(), meta))
            put("operation", operation)
            put("payload", payload)
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        runCatching {
            clientFor(baseUrl, tlsPinSha256).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    private fun clientFor(baseUrl: String, pin: String?): OkHttpClient {
        val validation = validatePresenceEndpoint(baseUrl, pin)
        if (!validation.allowed || validation.local || pin.isNullOrBlank()) return baseClient
        val host = URI(baseUrl).host
        return baseClient.newBuilder()
            .certificatePinner(CertificatePinner.Builder().add(host, pin).build())
            .build()
    }

    private fun verifyCompanionIdentity(
        identity: DeviceIdentity,
        challenge: ByteArray,
        signatureBase64: String?,
    ): Boolean {
        if (signatureBase64.isNullOrBlank()) return false
        if (identity.keyAlgorithm != "ED25519" || identity.publicKeyEncoding != "RAW") return false
        if (Build.VERSION.SDK_INT < 33) return false
        val raw = runCatching { Base64.decode(identity.publicKey, Base64.DEFAULT) }.getOrNull() ?: return false
        if (raw.size != 32) return false

        // X.509 SubjectPublicKeyInfo prefix for Ed25519 + raw 32-byte public key.
        val prefix = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
        )
        val encoded = prefix + raw
        return runCatching {
            val publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(encoded))
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey)
            verifier.update(challenge)
            verifier.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
        }.getOrDefault(false)
    }
}
