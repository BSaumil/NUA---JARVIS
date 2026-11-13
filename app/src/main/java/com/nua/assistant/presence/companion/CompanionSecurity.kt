package com.nua.assistant.presence.companion

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Wire-format helpers for the companion node's HTTP API
 * (services/companion/nua_companion/security.py), mirrored field-for-field so a
 * signature produced here verifies there and vice versa. This is a distinct wire
 * format from [com.nua.assistant.localapi.LocalApiContract]'s own
 * `localApiRequestDigest` (which returns a hex *string* digest for a different
 * local-API surface) -- the companion node signs and verifies the raw 32-byte
 * SHA-256 digest directly, so reusing that hex-string function here would produce
 * a signature the companion's own `verify_ed25519` could never accept. See this
 * workstream's handoff doc for the discrepancy between the two conventions.
 */
object CompanionSecurity {
    private const val MAX_CLOCK_SKEW_MILLIS = 120_000L

    fun nowMillis(): Long = System.currentTimeMillis()

    fun newNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** Matches Python's `json.dumps(value, separators=(",", ":"), sort_keys=True)` for a
     * flat string-valued map -- the only payload shape this companion client needs. */
    fun compactJson(payload: Map<String, String>): String {
        return payload.entries
            .sortedBy { it.key }
            .joinToString(",", prefix = "{", postfix = "}") { (key, value) ->
                "${jsonQuote(key)}:${jsonQuote(value)}"
            }
    }

    /** Matches Python's `json.dumps`'s default `ensure_ascii=True`: every character
     * outside printable ASCII (0x20-0x7E) is escaped as `\uXXXX`, not left as a literal
     * UTF-8 byte sequence. Without this, any prompt containing non-ASCII text (NUA
     * supports multiple languages) would canonicalize differently on each side and every
     * such request's signature would fail to verify. */
    private fun jsonQuote(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        for (char in value) {
            when (char) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000c' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (char.code < 0x20 || char.code > 0x7E) {
                    builder.append("\\u%04x".format(char.code))
                } else {
                    builder.append(char)
                }
            }
        }
        builder.append('"')
        return builder.toString()
    }

    fun canonicalRequest(
        protocolVersion: Int,
        requestId: String,
        clientId: String,
        nonce: String,
        timestampMillis: Long,
        operation: String,
        payloadJson: String,
    ): ByteArray {
        return listOf(
            protocolVersion.toString(),
            requestId,
            clientId,
            nonce,
            timestampMillis.toString(),
            operation,
            payloadJson,
        ).joinToString("|").toByteArray(Charsets.UTF_8)
    }

    fun digestRequest(
        protocolVersion: Int,
        requestId: String,
        clientId: String,
        nonce: String,
        timestampMillis: Long,
        operation: String,
        payloadJson: String,
    ): ByteArray {
        val canonical = canonicalRequest(protocolVersion, requestId, clientId, nonce, timestampMillis, operation, payloadJson)
        return MessageDigest.getInstance("SHA-256").digest(canonical)
    }

    /** Mirrors the companion's own `ReplayWindow`: a timestamp outside the clock-skew
     * window can never be accepted, independent of nonce tracking (which only this
     * client's own outgoing requests need -- the companion tracks nonces it has seen). */
    fun isWithinClockSkew(timestampMillis: Long, nowMillis: Long = nowMillis()): Boolean {
        return kotlin.math.abs(nowMillis - timestampMillis) <= MAX_CLOCK_SKEW_MILLIS
    }
}
