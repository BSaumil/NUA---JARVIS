package com.nua.assistant.connectors

import com.nua.assistant.presence.companion.CompanionHttpResult
import com.nua.assistant.presence.companion.CompanionHttpTransport
import com.nua.assistant.presence.companion.CompanionSigningIdentity
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

private class FakeFilesSigningIdentity : CompanionSigningIdentity {
    override val identity = DeviceIdentity(
        deviceId = "device-1",
        publicKey = "fake-public-key",
        ownerLabel = "test device",
        platform = NuaPlatform.ANDROID_PHONE,
        appVersion = "0.1.0",
    )

    override fun sign(message: ByteArray): String = "sig-${message.size}"
}

private class RecordingFilesTransport(private val response: CompanionHttpResult) : CompanionHttpTransport {
    var lastPath: String? = null
    var lastBody: String? = null

    override suspend fun get(path: String): CompanionHttpResult = error("not used")

    override suspend fun post(path: String, bodyJson: String): CompanionHttpResult {
        lastPath = path
        lastBody = bodyJson
        return response
    }
}

class CompanionFilesConnectorTest {

    @Test
    fun `a successful list request returns the entries as a ReadSuccess`() = runTest {
        val transport = RecordingFilesTransport(
            CompanionHttpResult.Success(
                """{"succeeded":true,"entries":[{"name":"notes.txt","isDir":false,"sizeBytes":5}]}""",
            ),
        )
        val connector = CompanionFilesConnector(transport, FakeFilesSigningIdentity(), TEST_JSON)

        val result = connector.list("documents")

        assertTrue(result is ConnectorResult.ReadSuccess)
        assertEquals("companion-files", (result as ConnectorResult.ReadSuccess).connectorId)
        assertTrue(result.body.contains("notes.txt"))
        assertEquals("/files/list", transport.lastPath)
    }

    @Test
    fun `the signed request names the exact mount and path requested, never a raw filesystem path smuggled in otherwise`() = runTest {
        val transport = RecordingFilesTransport(CompanionHttpResult.Success("""{"succeeded":true,"entries":[]}"""))
        val connector = CompanionFilesConnector(transport, FakeFilesSigningIdentity(), TEST_JSON)

        connector.list("documents", "sub/dir")

        val sentBody = TEST_JSON.parseToJsonElement(transport.lastBody!!).jsonObject
        val payload = sentBody["payload"]!!.jsonObject
        assertEquals("documents", payload["mount"]!!.jsonPrimitive.content)
        assertEquals("sub/dir", payload["path"]!!.jsonPrimitive.content)
        val scopes = sentBody["scopes"]!!.jsonArray.map { it.jsonPrimitive.content }
        // Reuses CONNECTORS_READ (the canonical connector-read scope), not a one-off
        // FILES_READ scope -- see CompanionFilesConnector's own doc comment.
        assertEquals(listOf("CONNECTORS_READ"), scopes)
        assertEquals("files.list", sentBody["operation"]!!.jsonPrimitive.content)
        val expectedPayloadJson = com.nua.assistant.presence.companion.CompanionSecurity.compactJson(
            mapOf("mount" to "documents", "path" to "sub/dir"),
        )
        assertEquals(expectedPayloadJson, sentBody["payloadJson"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a successful read request returns the base64 content as a ReadSuccess`() = runTest {
        val transport = RecordingFilesTransport(
            CompanionHttpResult.Success("""{"succeeded":true,"contentBase64":"aGVsbG8="}"""),
        )
        val connector = CompanionFilesConnector(transport, FakeFilesSigningIdentity(), TEST_JSON)

        val result = connector.read("documents", "notes.txt")

        assertTrue(result is ConnectorResult.ReadSuccess)
        assertEquals("aGVsbG8=", (result as ConnectorResult.ReadSuccess).body)
    }

    @Test
    fun `a companion refusal -- e g an unapproved device or a path-traversal attempt -- is reported, never as success`() = runTest {
        val transport = RecordingFilesTransport(CompanionHttpResult.Failure(403, """{"error":"unpaired_or_revoked"}"""))
        val connector = CompanionFilesConnector(transport, FakeFilesSigningIdentity(), TEST_JSON)

        val result = connector.read("documents", "../../etc/passwd")

        assertTrue(result is ConnectorResult.Refused)
    }

    @Test
    fun `an unreachable companion is reported as failed, never silently treated as success`() = runTest {
        val transport = RecordingFilesTransport(CompanionHttpResult.Unreachable("timed out"))
        val connector = CompanionFilesConnector(transport, FakeFilesSigningIdentity(), TEST_JSON)

        val result = connector.list("documents")

        assertTrue(result is ConnectorResult.Failed)
    }
}
