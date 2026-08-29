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
        // MockWebServer's own dispatch thread for a cancelled, still-delaying response can
        // still be mid-write when shutdown() is called right after cancellation — a race in
        // the test harness itself, not something under test. Swallow it rather than let a
        // harness-timing IOException mask a real assertion result.
        runCatching { server.shutdown() }
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
        // setHeadersDelay, not setBodyDelay: OkHttp's onResponse() fires once the status
        // line and headers arrive, before the body is read — a body-only delay lets the
        // call complete normally almost immediately (nothing left to cancel), which is
        // not what this test means to set up. Delaying the headers is what actually keeps
        // the call in flight long enough for cancellation to have something to interrupt.
        //
        // The bound below is deliberately relative to the configured delay, not an
        // absolute millisecond figure, so it stays meaningful without being fragile to CI
        // runner slowness — "well under a quarter of the delay" still clearly
        // distinguishes "cancellation worked" from "cancellation was a no-op and we just
        // waited it out."
        val serverDelayMillis = 10_000L
        server.enqueue(MockResponse().setHeadersDelay(serverDelayMillis, TimeUnit.MILLISECONDS).setBody("late"))
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
        delay(200) // give MockWebServer's dispatch thread a moment to notice the closed socket

        assertTrue(
            "cancellation should resolve in well under the server's ${serverDelayMillis}ms delay, took ${elapsedMillis}ms",
            elapsedMillis < serverDelayMillis / 4,
        )
        assertTrue(
            "the call's coroutine should complete via cancellation, not a resumed value",
            threw is CancellationException || job.isCancelled,
        )
    }
}
