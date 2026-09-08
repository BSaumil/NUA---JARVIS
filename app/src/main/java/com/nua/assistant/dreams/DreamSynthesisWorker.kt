package com.nua.assistant.dreams

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nua.assistant.ai.CLAUDE_MODEL_CONVERSATION
import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.WorkerOutcome
import com.nua.assistant.ai.extractJsonPayload
import com.nua.assistant.ai.outcomeForWorkerRun
import com.nua.assistant.context.ContextEngine
import com.nua.assistant.context.describe
import com.nua.assistant.goals.GoalRepository
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.trust.TrustRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Below this much raw material, there isn't enough to cross-reference honestly — skip rather than force an insight. */
private const val MINIMUM_SIGNAL_COUNT = 4

private val DREAM_SYSTEM_PROMPT = """
    You review everything NUA currently knows about this user — durable facts, recent
    goals, a record of what's gone right or wrong, and their current situation — looking
    for exactly one insight that would NOT be obvious from any single piece of that
    information alone. Every item below is tagged with an id, like [fact#12] or
    [goal#3] or [ledger#7] — your insight must connect at least two specific, different
    tagged items, and you must name which ones. A vague sense of "the whole picture"
    doesn't count; you need two actual items you can point to. It must not restate,
    summarize, or repackage information that's already been said elsewhere in what you
    were given — that is a failure, not a weaker version of success. Reject anything
    generic ("stay organized," "consider your goals") — it should be specific to this
    exact data, or it should not exist.

    Reply with JSON only, no prose:
    {"category": "OPPORTUNITY|PATTERN|REMINDER|CONCERN|OPTIMIZATION|RELATIONSHIP|FINANCE|PRODUCTIVITY|LEARNING|BUSINESS", "insight": "<one or two sentences>", "connectedFactIds": [<fact# numbers you actually connected>], "connectedGoalIds": [<goal# numbers you actually connected>], "connectedLedgerIds": [<ledger# numbers you actually connected>]}

    If you can't name at least two specific tagged items your insight genuinely connects,
    reply {"category": null, "insight": ""} instead. Saying nothing is the correct answer
    far more often than not — don't force one to exist.
""".trimIndent()

@Serializable
private data class DreamDto(
    val category: String? = null,
    val insight: String = "",
    val connectedFactIds: List<Long> = emptyList(),
    val connectedGoalIds: List<Long> = emptyList(),
    val connectedLedgerIds: List<Long> = emptyList(),
)

/**
 * The weekly, rate-limited "NUA dreams" pass — one deep look across memory for a single
 * non-obvious, cross-referenced insight, never a repackaged summary. Mirrors
 * MemoryConsolidationWorker/GoalReviewWorker's "gather real state, ask Claude for one
 * thing, discard if it's not there" shape, with an explicit signal-count gate so it
 * doesn't hallucinate connections out of a near-empty database.
 */
@HiltWorker
class DreamSynthesisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val memoryDao: MemoryDao,
    private val goalRepository: GoalRepository,
    private val trustRepository: TrustRepository,
    private val contextEngine: ContextEngine,
    private val dreamRepository: DreamRepository,
    private val claudeApiClient: ClaudeApiClient,
    private val json: Json,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val facts = memoryDao.getAllFacts()
        val goals = goalRepository.activeGoals()
        val goalObservations = goals.mapNotNull { goalRepository.latestObservation(it.id) }
        val ledger = trustRepository.recentLedger(limit = 10)

        val signalCount = facts.size + goalObservations.size + ledger.size
        if (signalCount < MINIMUM_SIGNAL_COUNT) return Result.success()

        val snapshot = contextEngine.currentSnapshot()

        val prompt = buildString {
            appendLine(snapshot.describe())
            appendLine()
            appendLine("Known facts: " + facts.joinToString("; ") { "[fact#${it.id}] ${it.value}" })
            appendLine()
            appendLine("Active goals: " + if (goals.isEmpty()) "none" else goals.joinToString("; ") { "[goal#${it.id}] ${it.text}" })
            appendLine(
                "Recent goal observations: " + if (goalObservations.isEmpty()) {
                    "none"
                } else {
                    goalObservations.joinToString("; ") { "(about goal#${it.goalId}) ${it.text}" }
                },
            )
            appendLine()
            appendLine(
                "Recent things NUA has gotten wrong: " + if (ledger.isEmpty()) {
                    "none logged"
                } else {
                    ledger.joinToString("; ") { "[ledger#${it.id}] ${it.description}" }
                },
            )
        }

        val result = claudeApiClient.complete(
            userPrompt = prompt,
            system = DREAM_SYSTEM_PROMPT,
            model = CLAUDE_MODEL_CONVERSATION,
            maxTokens = 250,
        )

        if (outcomeForWorkerRun(result is ClaudeResult.Success, runAttemptCount) == WorkerOutcome.RETRY) return Result.retry()
        val text = (result as? ClaudeResult.Success)?.text ?: return Result.success()
        val dto = runCatching {
            json.decodeFromString(DreamDto.serializer(), extractJsonPayload(text))
        }.getOrNull() ?: return Result.success()

        val category = dto.category?.let { runCatching { DreamCategory.valueOf(it) }.getOrNull() }
        if (category != null && dto.insight.length > 15) {
            val sources = validatedDreamSources(
                connectedFactIds = dto.connectedFactIds,
                connectedGoalIds = dto.connectedGoalIds,
                connectedLedgerIds = dto.connectedLedgerIds,
                availableFactIds = facts.map { it.id }.toSet(),
                availableGoalIds = goals.map { it.id }.toSet(),
                availableLedgerIds = ledger.map { it.id }.toSet(),
            )
            // Code-enforced, not just prompt-instructed: an insight that can't name two
            // real, distinct things it connected is discarded here, the same "saying
            // nothing is correct more often than not" outcome as any other reject path.
            if (hasSufficientProvenance(sources)) {
                dreamRepository.record(category, dto.insight, sources)
            }
        }

        return Result.success()
    }
}
