package com.nua.assistant.presence.companion

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exercises [CompanionOkHttpTransport] against a real local HTTP server, proving the
 * actual request plumbing (method, path, body, status-code mapping) is correct --
 * [CompanionPairingClientTest] and [CompanionApiClientTest] already cover the
 * pairing/signing logic against a fake transport, so this file only needs to prove the
 * real implementation of that interface behaves as it promises. */
class CompanionOkHttpTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: CompanionOkHttpTransport

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        transport = CompanionOkHttpTransport(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a successful GET is reported as Success with the response body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ready":true}"""))

        val result = transport.get("/health")

        assertEquals(CompanionHttpResult.Success("""{"ready":true}"""), result)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/health", recorded.path)
    }

    @Test
    fun `a POST sends the exact body given and the path requested`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        transport.post("/pair/challenge", """{"identity":{"deviceId":"x"}}""")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/pair/challenge", recorded.path)
        assertEquals("""{"identity":{"deviceId":"x"}}""", recorded.body.readUtf8())
    }

    @Test
    fun `a non-2xx response is reported as Failure with its status code and body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"unpaired_or_revoked"}"""))

        val result = transport.post("/inference", "{}")

        assertEquals(CompanionHttpResult.Failure(403, """{"error":"unpaired_or_revoked"}"""), result)
    }

    @Test
    fun `an unreachable server is reported as Unreachable, never as a crash`() = runBlocking {
        server.shutdown()

        val result = transport.get("/health")

        assertTrue(result is CompanionHttpResult.Unreachable)
    }
}
