package com.nua.assistant.memory

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.WorkerOutcome
import com.nua.assistant.ai.mesh.InferenceTaskType
import com.nua.assistant.ai.mesh.ModelMesh
import com.nua.assistant.ai.mesh.PrivacySensitivity
import com.nua.assistant.ai.mesh.TaskContract
import com.nua.assistant.ai.outcomeForWorkerRun
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val MESSAGE_RETENTION_THRESHOLD = 300
private const val CONSOLIDATION_BATCH_SIZE = 100

private val CONSOLIDATION_SYSTEM_PROMPT = """
    You compress an old stretch of conversation between a user and NUA, their
    assistant, into a short durable summary — 2-4 sentences covering anything worth
    remembering long-term (recurring topics, decisions, running context), not a
    transcript recap. If nothing in this stretch is durable, reply with an empty string.
""".trimIndent()

/**
 * Keeps the messages table from growing unbounded. Once raw history exceeds
 * MESSAGE_RETENTION_THRESHOLD, the oldest CONSOLIDATION_BATCH_SIZE messages are
 * summarized into a single user_facts entry (category "conversation_summary") via
 * Claude Haiku, then deleted. Recent history and named facts (see FactExtractor) are
 * untouched — this only prunes raw turn-by-turn transcript, not what NUA knows.
 */
@HiltWorker
class MemoryConsolidationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val memoryDao: MemoryDao,
    private val modelMesh: ModelMesh,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (memoryDao.countMessages() <= MESSAGE_RETENTION_THRESHOLD) return Result.success()

        val batch = memoryDao.getOldestMessages(CONSOLIDATION_BATCH_SIZE)
        if (batch.isEmpty()) return Result.success()

        val transcript = batch.joinToString("\n") { "${it.role}: ${it.content}" }
        val contract = TaskContract(task = InferenceTaskType.SUMMARIZATION, privacySensitivity = PrivacySensitivity.HIGH)
        val result = modelMesh.complete(
            contract = contract,
            userPrompt = transcript,
            system = CONSOLIDATION_SYSTEM_PROMPT,
            maxTokens = 300,
        )

        if (outcomeForWorkerRun(result is ClaudeResult.Success, runAttemptCount) == WorkerOutcome.RETRY) return Result.retry()
        // A failed call — even after retries are exhausted — means Claude was never
        // actually consulted about this batch, so its content is unknown. The messages
        // must not be deleted on this path: deletion is only correct once Claude has
        // actually looked at the batch, whether or not it judged anything durable enough
        // to summarize. Found by inspection while adding retry handling: the pre-existing
        // code deleted the batch unconditionally on any failure, discarding up to
        // CONSOLIDATION_BATCH_SIZE messages with no summary ever written for them.
        if (result !is ClaudeResult.Success) return Result.success()

        val summary = result.text.trim()
        if (summary.length > 10) {
            memoryDao.upsertFact(
                key = "conversation_summary_${batch.last().timestamp}",
                value = summary,
                category = "conversation_summary",
                memoryType = MemoryType.EPISODIC,
                source = "consolidated from ${batch.size} older messages",
            )
        }
        memoryDao.deleteMessagesByIds(batch.map { it.id })

        return Result.success()
    }
}
