package com.nua.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `outcomeForWorkerRun` is the fix for a real defect an architecture review found: every
 * Claude-calling worker (`DreamSynthesisWorker`, `GoalReviewWorker`,
 * `MemoryConsolidationWorker`) treated a failed API call identically to "Claude had
 * nothing to say" — both returned `Result.success()`, so WorkManager never retried and
 * nothing surfaced the failure. `MemoryConsolidationWorker` additionally deleted its
 * message batch unconditionally on that path, discarding up to 100 messages with no
 * summary ever written for them — found while adding this test, not assumed in advance.
 */
class WorkerRetryPolicyTest {

    @Test
    fun `progress made means done, regardless of attempt count`() {
        assertEquals(WorkerOutcome.DONE, outcomeForWorkerRun(madeProgress = true, attempt = 0))
        assertEquals(WorkerOutcome.DONE, outcomeForWorkerRun(madeProgress = true, attempt = 10))
    }

    @Test
    fun `no progress retries while attempts remain`() {
        assertEquals(WorkerOutcome.RETRY, outcomeForWorkerRun(madeProgress = false, attempt = 0, maxAttempts = 3))
        assertEquals(WorkerOutcome.RETRY, outcomeForWorkerRun(madeProgress = false, attempt = 2, maxAttempts = 3))
    }

    @Test
    fun `no progress gives up once attempts are exhausted, rather than retrying forever`() {
        assertEquals(WorkerOutcome.DONE, outcomeForWorkerRun(madeProgress = false, attempt = 3, maxAttempts = 3))
        assertEquals(WorkerOutcome.DONE, outcomeForWorkerRun(madeProgress = false, attempt = 100, maxAttempts = 3))
    }

    @Test
    fun `the default bound is finite`() {
        // A regression here (e.g. someone "fixing" a flaky worker by cranking this up)
        // would turn a transient outage into an indefinite retry loop.
        assertEquals(3, MAX_WORKER_CLAUDE_ATTEMPTS)
    }

    // -----------------------------------------------------------------------------------
    // The GoalReviewWorker shape: N independent calls, no per-item checkpointing, and
    // recordObservation has no idempotency check. madeProgress must be computed as
    // "nothing failed, OR something was actually written" — never "nothing failed AND
    // everything was written" — otherwise a run with one success and one failure would
    // retry and duplicate the goal that already succeeded.
    // -----------------------------------------------------------------------------------

    @Test
    fun `all goals succeeding needs no retry even if none produced a recordable observation`() {
        val anyCallFailed = false
        val anyObservationRecorded = false // every call succeeded but said nothing worth keeping
        assertEquals(WorkerOutcome.DONE, outcomeForWorkerRun(!anyCallFailed || anyObservationRecorded, attempt = 0))
    }

    @Test
    fun `a fully failed run retries`() {
        val anyCallFailed = true
        val anyObservationRecorded = false
        assertEquals(WorkerOutcome.RETRY, outcomeForWorkerRun(!anyCallFailed || anyObservationRecorded, attempt = 0))
    }

    @Test
    fun `a partially failed run with something already written does not retry`() {
        // Retrying here would re-run the goal that already wrote an observation,
        // duplicating it — accepting the partial result is the only safe choice.
        val anyCallFailed = true
        val anyObservationRecorded = true
        assertEquals(WorkerOutcome.DONE, outcomeForWorkerRun(!anyCallFailed || anyObservationRecorded, attempt = 0))
    }
}
