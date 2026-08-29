package com.nua.assistant.trust

import android.content.Context
import com.nua.assistant.ai.FamiliarityTier
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.memory.ActionOutcomeDao
import com.nua.assistant.memory.ActionOutcomeEntity
import com.nua.assistant.memory.AutonomyPreferenceDao
import com.nua.assistant.memory.AutonomyPreferenceEntity
import com.nua.assistant.memory.TrustLedgerDao
import com.nua.assistant.memory.TrustLedgerEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "nua_trust_prefs"
private const val KEY_LAST_SELF_REPORT_AT = "last_self_report_at"
private const val SELF_REPORT_MIN_INTERVAL_DAYS = 14L
private const val AUTONOMY_SUGGESTION_THRESHOLD = 5

/**
 * Owns everything the Trust Engine needs: the audit trail of what NUA did (and whether it
 * worked), the curated ledger of what went wrong, per-action-type approval tracking for
 * adaptive autonomy, and the rate-limited unprompted self-report at high familiarity.
 */
@Singleton
class TrustRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val trustLedgerDao: TrustLedgerDao,
    private val actionOutcomeDao: ActionOutcomeDao,
    private val autonomyPreferenceDao: AutonomyPreferenceDao,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Logs one dispatch/confirmation result. Failures (including rejections) also land in the curated ledger. */
    suspend fun recordOutcome(
        actionType: String,
        tier: AutonomyTier,
        summary: String,
        outcome: ActionOutcomeState,
        wasRejection: Boolean = false,
    ) {
        actionOutcomeDao.insert(
            ActionOutcomeEntity(
                actionType = actionType,
                tier = tier,
                summary = summary,
                outcomeState = outcome,
                wasRejection = wasRejection,
            ),
        )
        if (outcome.countsAsFailure()) {
            val type = if (wasRejection) TrustEventType.REJECTED_PLAN else TrustEventType.FAILED_ACTION
            trustLedgerDao.insert(TrustLedgerEntity(type = type, description = summary))
        }
    }

    suspend fun recordMistake(type: TrustEventType, description: String) {
        trustLedgerDao.insert(TrustLedgerEntity(type = type, description = description))
    }

    suspend fun scoreSnapshot(): Int = TrustScoreEngine.score(actionOutcomeDao.getAll())

    suspend fun recentLedger(limit: Int = 20): List<TrustLedgerEntity> = trustLedgerDao.recent(limit)

    suspend fun recentOutcomes(limit: Int = 20): List<ActionOutcomeEntity> = actionOutcomeDao.recent(limit)

    /** Call after the user approves a proposed reply/plan — feeds the adaptive-autonomy threshold. */
    suspend fun recordApproval(actionType: NuaActionType): AutonomyPreferenceEntity {
        val existing = autonomyPreferenceDao.get(actionType.name)
        val updated = (existing ?: AutonomyPreferenceEntity(actionType = actionType.name))
            .copy(approvedCount = (existing?.approvedCount ?: 0) + 1)
        autonomyPreferenceDao.upsert(updated)
        return updated
    }

    suspend fun isAutoApproved(actionType: NuaActionType): Boolean =
        autonomyPreferenceDao.get(actionType.name)?.autoApproveEnabled == true

    suspend fun setAutoApprove(actionType: NuaActionType, enabled: Boolean) {
        val existing = autonomyPreferenceDao.get(actionType.name)
            ?: AutonomyPreferenceEntity(actionType = actionType.name)
        autonomyPreferenceDao.upsert(existing.copy(autoApproveEnabled = enabled))
    }

    /** Action types that have been approved enough times to suggest auto-approving, but aren't yet. */
    suspend fun autonomySuggestions(): List<AutonomyPreferenceEntity> =
        autonomyPreferenceDao.getAll().filter { isAutonomySuggested(it, AUTONOMY_SUGGESTION_THRESHOLD) }

    suspend fun allAutonomyPreferences(): List<AutonomyPreferenceEntity> = autonomyPreferenceDao.getAll()

    /**
     * At the highest familiarity tier, NUA occasionally reports on itself unprompted — the
     * inverse of how most assistants behave. Rate-limited to once every
     * [SELF_REPORT_MIN_INTERVAL_DAYS] days, and silent if nothing happened since last time
     * so it can never become a recurring interruption.
     */
    suspend fun pendingSelfReport(currentTier: FamiliarityTier): String? {
        val lastReportAt = prefs.getLong(KEY_LAST_SELF_REPORT_AT, 0L)
        val now = System.currentTimeMillis()
        val minIntervalMillis = TimeUnit.DAYS.toMillis(SELF_REPORT_MIN_INTERVAL_DAYS)
        if (!selfReportEligible(currentTier, lastReportAt, now, minIntervalMillis)) return null

        val since = trustLedgerDao.since(lastReportAt)
        val message = selfReportMessage(since) ?: return null

        prefs.edit().putLong(KEY_LAST_SELF_REPORT_AT, now).apply()
        return message
    }
}
