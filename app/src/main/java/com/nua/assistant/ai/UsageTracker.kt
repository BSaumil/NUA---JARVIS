package com.nua.assistant.ai

import com.nua.assistant.memory.UsageDao
import com.nua.assistant.memory.UsageLogEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private data class ModelPricing(val inputPerMillion: Double, val outputPerMillion: Double)

// Standard (non-introductory) per-million-token USD pricing as of August 2026. Claude
// Sonnet 5 has a lower introductory rate through 2026-08-31 — deliberately not used
// here so the dashboard doesn't understate cost once that window closes.
private val PRICING = mapOf(
    CLAUDE_MODEL_CONVERSATION to ModelPricing(inputPerMillion = 3.00, outputPerMillion = 15.00),
    CLAUDE_MODEL_UTILITY to ModelPricing(inputPerMillion = 1.00, outputPerMillion = 5.00),
)

data class UsageSummary(val totalCostUsd: Double, val inputTokens: Long, val outputTokens: Long)

/**
 * Logs token usage from every Claude API call and turns it into an approximate USD
 * cost using published list pricing — not the org's actual (possibly discounted)
 * billing, just a budgeting signal shown in Settings.
 */
@Singleton
class UsageTracker @Inject constructor(
    private val usageDao: UsageDao,
) {

    suspend fun record(model: String, usage: ClaudeUsage) {
        if (usage.inputTokens == 0 && usage.outputTokens == 0) return
        usageDao.insert(
            UsageLogEntity(model = model, inputTokens = usage.inputTokens, outputTokens = usage.outputTokens),
        )
    }

    suspend fun summarySince(sinceMillis: Long): UsageSummary {
        val entries = usageDao.since(sinceMillis)
        var cost = 0.0
        var input = 0L
        var output = 0L
        for (entry in entries) {
            val pricing = PRICING[entry.model] ?: continue
            cost += (entry.inputTokens / 1_000_000.0) * pricing.inputPerMillion
            cost += (entry.outputTokens / 1_000_000.0) * pricing.outputPerMillion
            input += entry.inputTokens
            output += entry.outputTokens
        }
        return UsageSummary(totalCostUsd = cost, inputTokens = input, outputTokens = output)
    }

    suspend fun summaryThisMonth(): UsageSummary = summarySince(startOfMonthMillis())

    private fun startOfMonthMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
