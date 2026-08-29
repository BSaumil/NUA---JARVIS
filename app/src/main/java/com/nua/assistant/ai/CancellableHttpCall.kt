package com.nua.assistant.ai

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * A cancellation-aware [OkHttpClient.newCall]. `Call.execute()` — what ClaudeApiClient
 * used before this — is a plain blocking call: even inside `withContext(Dispatchers.IO)`,
 * cancelling the wrapping coroutine (e.g. WorkManager stopping a CoroutineWorker) does not
 * interrupt it. The request keeps running on its IO thread until it naturally completes or
 * hits OkHttpClient's own connect/read/write timeout (bounded, but not immediate — up to
 * tens of seconds of wasted network/battery/API cost for a result nothing will use).
 *
 * This uses `Call.enqueue()` instead, which doesn't block, and registers
 * `invokeOnCancellation { call.cancel() }` so a cancelled coroutine kills the underlying
 * socket immediately rather than waiting it out.
 */
internal suspend fun executeCancellably(client: OkHttpClient, request: Request): Response =
    suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // A cancelled call surfaces here as an IOException too — the
                    // continuation is already cancelled by then, so resuming it at all
                    // (with a value or an exception) would be a no-op or throw
                    // IllegalStateException; skip it rather than let that mask the real
                    // CancellationException already in flight.
                    if (!call.isCanceled()) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            },
        )
    }
