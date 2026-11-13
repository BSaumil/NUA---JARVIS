package com.nua.assistant.presence.companion

import com.nua.assistant.presence.protocol.DeviceIdentity
import com.nua.assistant.presence.protocol.NuaPlatform
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val TEST_JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }

private class FakeSigningIdentity(deviceId: String = "device-1") : CompanionSigningIdentity {
    override val identity = DeviceIdentity(
        deviceId = deviceId,
        publicKey = "fake-public-key",
        ownerLabel = "test device",
        platform = NuaPlatform.ANDROID_PHONE,
        appVersion = "0.1.0",
    )

    override fun sign(message: ByteArray): String = "sig-${message.size}"
}

private class RecordingTransport(private val response: CompanionHttpResult) : CompanionHttpTransport {
    var lastPath: String? = null
    var lastBody: String? = null

    override suspend fun get(path: String): CompanionHttpResult = error("not used")

    override suspend fun post(path: String, bodyJson: String): CompanionHttpResult {
        lastPath = path
        lastBody = bodyJson
        return response
    }
}

class CompanionApiClientTest {

    @Test
    fun `a successful inference call returns the text and provider`() = runTest {
        val transport = RecordingTransport(
            CompanionHttpResult.Success("""{"succeeded":true,"text":"hi there","provider":"ollama"}"""),
        )
        val client = CompanionApiClient(transport, FakeSigningIdentity(), TEST_JSON)

        val outcome = client.inference("hello", "local-model")

        assertEquals(InferenceOutcome.Succeeded("hi there", "ollama"), outcome)
        assertEquals("/inference", transport.lastPath)
    }

    @Test
    fun `the signed request's payload exactly matches what was sent for prompt and modelId`() = runTest {
        val transport = RecordingTransport(CompanionHttpResult.Success("""{"succeeded":true,"text":"ok"}"""))
        val client = CompanionApiClient(transport, FakeSigningIdentity(), TEST_JSON)

        client.inference("what's the weather", "llama3.2")

        val sentBody = TEST_JSON.parseToJsonElement(transport.lastBody!!).jsonObject
        val payload = sentBody["payload"]!!.jsonObject
        assertEquals("what's the weather", payload["prompt"]!!.jsonPrimitive.content)
        assertEquals("llama3.2", payload["modelId"]!!.jsonPrimitive.content)
        val scopes = sentBody["scopes"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("INFERENCE"), scopes)
        assertEquals("inference.start", sentBody["operation"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a companion refusal is reported with its error code, never as a success`() = runTest {
        val transport = RecordingTransport(
            CompanionHttpResult.Failure(403, """{"error":"unpaired_or_revoked"}"""),
        )
        val client = CompanionApiClient(transport, FakeSigningIdentity(), TEST_JSON)

        val outcome = client.inference("hello")

        assertEquals(InferenceOutcome.Refused("unpaired_or_revoked"), outcome)
    }

    @Test
    fun `an unreachable companion is refused, never silently treated as success`() = runTest {
        val transport = RecordingTransport(CompanionHttpResult.Unreachable("timed out"))
        val client = CompanionApiClient(transport, FakeSigningIdentity(), TEST_JSON)

        val outcome = client.inference("hello")

        assertTrue(outcome is InferenceOutcome.Refused)
        assertTrue((outcome as InferenceOutcome.Refused).reason.contains("timed out"))
    }
}
