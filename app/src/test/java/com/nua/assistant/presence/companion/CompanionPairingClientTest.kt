package com.nua.assistant.presence.companion

import com.nua.assistant.presence.protocol.DeviceIdentity
import com.nua.assistant.presence.protocol.NuaPlatform
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val TEST_JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }

private class FakePairingSigningIdentity(deviceId: String = "device-1") : CompanionSigningIdentity {
    override val identity = DeviceIdentity(
        deviceId = deviceId,
        publicKey = "fake-public-key",
        ownerLabel = "test device",
        platform = NuaPlatform.ANDROID_PHONE,
        appVersion = "0.1.0",
    )
    var lastSignedMessage: ByteArray? = null
    var nextSignature = "fake-signature"

    override fun sign(message: ByteArray): String {
        lastSignedMessage = message
        return nextSignature
    }
}

private class ScriptedTransport(
    private val responses: MutableMap<String, CompanionHttpResult>,
) : CompanionHttpTransport {
    val postedBodies = mutableMapOf<String, String>()

    override suspend fun get(path: String): CompanionHttpResult = responses[path] ?: error("no stub for GET $path")

    override suspend fun post(path: String, bodyJson: String): CompanionHttpResult {
        postedBodies[path] = bodyJson
        return responses[path] ?: error("no stub for POST $path")
    }
}

private fun challengeResponse(challengeId: String, nonceBase64: String) = """
    {"challengeId":"$challengeId","challenger":{"deviceId":"device-1","publicKey":"fake-public-key","ownerLabel":"test device","platform":"ANDROID_PHONE","appVersion":"0.1.0","protocolVersion":1},"nonceBase64":"$nonceBase64","expiresAtMillis":9999999999999}
""".trimIndent()

class CompanionPairingClientTest {

    @Test
    fun `a verified proof with owner approval still required is reported honestly, not as trust`() = runTest {
        val nonce = Base64.getEncoder().encodeToString("some-nonce-bytes".toByteArray())
        val transport = ScriptedTransport(
            mutableMapOf(
                "/pair/challenge" to CompanionHttpResult.Success(challengeResponse("chal-1", nonce)),
                "/pair/prove" to CompanionHttpResult.Success("""{"verified":true,"ownerApprovalRequired":true}"""),
            ),
        )
        val identity = FakePairingSigningIdentity()
        val client = CompanionPairingClient(transport, identity, TEST_JSON)

        val outcome = client.requestPairing()

        assertEquals(PairingOutcome.Proved("chal-1", ownerApprovalRequired = true), outcome)
        assertTrue("the challenge's decoded nonce bytes must be exactly what gets signed", identity.lastSignedMessage!!.contentEquals("some-nonce-bytes".toByteArray()))
    }

    @Test
    fun `a rejected signature is reported as rejected, not as a transport failure`() = runTest {
        val nonce = Base64.getEncoder().encodeToString("nonce".toByteArray())
        val transport = ScriptedTransport(
            mutableMapOf(
                "/pair/challenge" to CompanionHttpResult.Success(challengeResponse("chal-2", nonce)),
                "/pair/prove" to CompanionHttpResult.Failure(403, """{"verified":false,"ownerApprovalRequired":false}"""),
            ),
        )
        val client = CompanionPairingClient(transport, FakePairingSigningIdentity(), TEST_JSON)

        val outcome = client.requestPairing()

        assertEquals(PairingOutcome.SignatureRejected("chal-2"), outcome)
    }

    @Test
    fun `an unreachable companion is reported as a failure naming the reason`() = runTest {
        val transport = ScriptedTransport(
            mutableMapOf("/pair/challenge" to CompanionHttpResult.Unreachable("connection refused")),
        )
        val client = CompanionPairingClient(transport, FakePairingSigningIdentity(), TEST_JSON)

        val outcome = client.requestPairing()

        assertTrue(outcome is PairingOutcome.Failed)
        assertTrue((outcome as PairingOutcome.Failed).reason.contains("connection refused"))
    }

    @Test
    fun `a malformed challenge response is refused rather than crashing`() = runTest {
        val transport = ScriptedTransport(
            mutableMapOf("/pair/challenge" to CompanionHttpResult.Success("not json at all")),
        )
        val client = CompanionPairingClient(transport, FakePairingSigningIdentity(), TEST_JSON)

        val outcome = client.requestPairing()

        assertTrue(outcome is PairingOutcome.Failed)
    }
}
