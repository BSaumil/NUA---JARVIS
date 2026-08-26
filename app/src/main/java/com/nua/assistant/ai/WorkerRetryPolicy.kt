package com.nua.assistant.ai

/**
 * What a background worker should do after its one Claude call, given how that call
 * went. Every worker that calls Claude (`DreamSynthesisWorker`, `GoalReviewWorker`,
 * `MemoryConsolidationWorker`) used to treat [ClaudeResult.Failure] identically to "Claude
 * legitimately had nothing to say" — both paths returned `Result.success()`, so a
 * transient API/network failure during a weekly or daily job silently produced "nothing
 * happened this run," with WorkManager never retrying and nothing surfaced anywhere.
 * Found in an architecture review; there was no test asserting a failed call was ever
 * distinguished from an empty one, because the distinction didn't exist in the code.
 *
 * `androidx.work.ListenableWorker.Result` needs the Android runtime to even reference
 * (there's no Robolectric in this project's unit tests), so the retry-vs-done decision
 * lives here as a plain enum instead — each worker maps [WorkerOutcome] onto its own
 * `Result.retry()`/`Result.success()` in a couple of lines.
 */
enum class WorkerOutcome { DONE, RETRY }

/** Bounded so a persistently failing call (bad API key, service genuinely down) eventually
 *  gives up rather than retrying forever — the next regularly scheduled run tries again. */
const val MAX_WORKER_CLAUDE_ATTEMPTS = 3

/**
 * Pure: whether a worker run should be retried, given whether it made progress this
 * attempt and how many attempts it's had. [madeProgress] is deliberately a Boolean, not
 * a [ClaudeResult] — a single-call worker passes `result is ClaudeResult.Success`, but a
 * worker that calls Claude once per item (`GoalReviewWorker`, one call per active goal)
 * cannot honestly summarize a run with three successes and one failure as either "done"
 * or "retry": retrying would re-run the three that already wrote an observation, and
 * `GoalRepository.recordObservation` has no idempotency check, so that goal would end up
 * with two. The correct rule, expressed here rather than duplicated per call site: retry
 * only when the run made zero progress — otherwise accept the partial result, since the
 * item that failed gets picked up again on the worker's own next scheduled run anyway.
 *
 * [attempt] is `ListenableWorker.runAttemptCount`, which WorkManager increments across
 * retries of the same scheduled run and resets for the next period — bounding retries so
 * a persistently failing call (bad key, service genuinely down) eventually gives up
 * rather than retrying forever.
 */
fun outcomeForWorkerRun(madeProgress: Boolean, attempt: Int, maxAttempts: Int = MAX_WORKER_CLAUDE_ATTEMPTS): WorkerOutcome =
    when {
        madeProgress -> WorkerOutcome.DONE
        attempt < maxAttempts -> WorkerOutcome.RETRY
        else -> WorkerOutcome.DONE
    }
