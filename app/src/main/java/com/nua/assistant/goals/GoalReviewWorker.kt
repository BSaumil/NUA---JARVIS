package com.nua.assistant.goals

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nua.assistant.ai.CLAUDE_MODEL_UTILITY
import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.WorkerOutcome
import com.nua.assistant.ai.outcomeForWorkerRun
import com.nua.assistant.context.ContextEngine
import com.nua.assistant.context.describe
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private val GOAL_REVIEW_SYSTEM_PROMPT = """
    You review one durable goal the user has set for themselves against their current
    situation (time, weather, calendar, notifications). Reply with exactly one short,
    concrete, non-obvious observation or proposal connected to the goal — not
    encouragement, not a restatement of the goal, not generic advice. If the current
    situation genuinely doesn't suggest anything worth saying right now, reply with an
    empty string. Don't force a proposal just to have one.
""".trimIndent()

/**
 * Weekly review of every active goal against the current ContextEngine snapshot.
 * Mirrors MemoryConsolidationWorker's shape (gather real state, ask Claude for one
 * thing, store it if it's non-empty) — same "don't force output" discipline the
 * consolidation and fact-extraction prompts already use.
 */
@HiltWorker
class GoalReviewWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val goalRepository: GoalRepository,
    private val contextEngine: ContextEngine,
    private val claudeApiClient: ClaudeApiClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val activeGoals = goalRepository.activeGoals()
        if (activeGoals.isEmpty()) return Result.success()

        val snapshot = contextEngine.currentSnapshot().describe()
        var anyCallFailed = false
        var anyObservationRecorded = false

        activeGoals.forEach { goal ->
            val prompt = "Goal: ${goal.text}\n\nCurrent situation:\n$snapshot"
            val result = claudeApiClient.complete(
                userPrompt = prompt,
                system = GOAL_REVIEW_SYSTEM_PROMPT,
                model = CLAUDE_MODEL_UTILITY,
                maxTokens = 200,
            )
            if (result !is ClaudeResult.Success) {
                anyCallFailed = true
                return@forEach
            }
            val observation = result.text.trim()
            if (observation.length > 10) {
                goalRepository.recordObservation(goal.id, observation)
                anyObservationRecorded = true
            }
        }

        // Retrying re-processes every active goal from scratch, and recordObservation has
        // no idempotency check — so retrying after anything was already written would
        // duplicate that goal's observation. Only retry a run where nothing was written
        // yet; a goal whose call failed after something else succeeded is picked up again
        // on next week's scheduled run instead.
        val madeProgress = !anyCallFailed || anyObservationRecorded
        if (outcomeForWorkerRun(madeProgress, runAttemptCount) == WorkerOutcome.RETRY) return Result.retry()

        return Result.success()
    }
}
