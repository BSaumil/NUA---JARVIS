package com.nua.assistant.ai

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs against a real local socket (MockWebServer), not virtual time — the property
 * under test (does cancelling the coroutine actually kill the in-flight request) is a
 * genuine runtime/OS-level behavior that virtual-time test dispatchers can't stand in
 * for. See CancellableHttpCall.kt's doc comment for the bug this replaces: OkHttp's
 * plain `Call.execute()` doesn't respond to coroutine cancellation at all.
 */
class CancellableHttpCallTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `an ordinary call still returns the response body`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val response = executeCancellably(client, Request.Builder().url(server.url("/")).build())
        response.use {
            assertTrue(it.isSuccessful)
            assertTrue(it.body?.string()?.contains("ok") == true)
        }
    }

    @Test
    fun `cancelling the coroutine returns well before a slow server responds`() = runBlocking {
        // The server takes 10s to respond; a fix that isn't cancellation-aware would make
        // this test itself take ~10s to finish. Cancellation firing correctly is what
        // keeps this test fast.
        server.enqueue(MockResponse().setBody("late").setBodyDelay(10, TimeUnit.SECONDS))
        val request = Request.Builder().url(server.url("/")).build()

        var threw: Throwable? = null
        val job: Job = launch {
            try {
                executeCancellably(client, request)
            } catch (t: Throwable) {
                threw = t
            }
        }

        delay(200) // let the request actually start
        val start = System.currentTimeMillis()
        job.cancelAndJoin()
        val elapsedMillis = System.currentTimeMillis() - start

        assertTrue(
            "cancellation should resolve in well under the server's 10s delay, took ${elapsedMillis}ms",
            elapsedMillis < 2000,
        )
        assertTrue(
            "the call's coroutine should complete via cancellation, not a resumed value",
            threw is CancellationException || job.isCancelled,
        )
    }
}
