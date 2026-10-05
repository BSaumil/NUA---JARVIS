package com.nua.assistant.trust

import android.content.Context
import com.nua.assistant.ai.FamiliarityTier
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.memory.ActionOutcomeDao
import com.nua.assistant.memory.ActionOutcomeEntity
import com.nua.assistant.memory.AutonomyContractDao
import com.nua.assistant.memory.AutonomyContractEntity
import com.nua.assistant.memory.AutonomyPreferenceDao
import com.nua.assistant.memory.AutonomyPreferenceEntity
import com.nua.assistant.memory.ShadowPredictionDao
import com.nua.assistant.memory.ShadowPredictionEntity
import com.nua.assistant.memory.TrustLedgerDao
import com.nua.assistant.memory.TrustLedgerEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

private const val PREFS_NAME = "nua_trust_prefs"
private const val KEY_LAST_SELF_REPORT_AT = "last_self_report_at"
private const val SELF_REPORT_MIN_INTERVAL_DAYS = 14L
private const val AUTONOMY_SUGGESTION_THRESHOLD = 5
/** How long a committed idempotency key blocks a repeat — long enough to catch a
 *  replayed confirmation or retry, short enough not to block a genuine same-day resend. */
private const val IDEMPOTENCY_WINDOW_MILLIS = 5 * 60_000L

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
    private val autonomyContractDao: AutonomyContractDao,
    private val shadowPredictionDao: ShadowPredictionDao,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Logs one dispatch/confirmation result. Failures (including rejections) also land in the curated ledger. */
    suspend fun recordOutcome(
        actionType: String,
        tier: AutonomyTier,
        summary: String,
        outcome: ActionOutcomeState,
        wasRejection: Boolean = false,
        idempotencyKey: String? = null,
        recipient: String? = null,
    ) {
        actionOutcomeDao.insert(
            ActionOutcomeEntity(
                actionType = actionType,
                tier = tier,
                summary = summary,
                outcomeState = outcome,
                wasRejection = wasRejection,
                idempotencyKey = idempotencyKey,
                recipient = recipient,
            ),
        )
        if (outcome.countsAsFailure()) {
            val type = if (wasRejection) TrustEventType.REJECTED_PLAN else TrustEventType.FAILED_ACTION
            trustLedgerDao.insert(TrustLedgerEntity(type = type, description = summary))
            // "No unresolved failure trend" — a failure fail-closes any standing auto-
            // approve grant for this action type immediately, rather than waiting for the
            // user to notice and revoke it by hand.
            revokeAutoApproveIfGranted(actionType)
            suspendContractIfDrifting(actionType)
        }
    }

    private suspend fun revokeAutoApproveIfGranted(actionType: String) {
        val preference = autonomyPreferenceDao.get(actionType) ?: return
        if (preference.autoApproveEnabled) {
            autonomyPreferenceDao.upsert(preference.copy(autoApproveEnabled = false, expiresAt = null))
        }
    }

    /** Contextual Autonomy Contracts' drift detection — see
     *  trust/AutonomyContract.kt's contractShouldSuspend for the actual spike rule. */
    private suspend fun suspendContractIfDrifting(actionType: String) {
        val contract = autonomyContractDao.get(actionType) ?: return
        if (!contract.active) return
        val recent = actionOutcomeDao.mostRecentByActionType(actionType, limit = 3)
        if (contractShouldSuspend(recent.map { it.outcomeState })) {
            autonomyContractDao.suspendContract(actionType)
        }
    }

    /**
     * True when [idempotencyKey] already has a committed (accepted/completed/verified)
     * outcome recorded within [windowMillis] — the caller should suppress re-executing the
     * real-world side effect rather than call recordOutcome/dispatch again. A prior FAILED
     * or unresolved attempt never blocks — only a call that actually went out does.
     */
    suspend fun wasRecentlyExecuted(idempotencyKey: String, windowMillis: Long = IDEMPOTENCY_WINDOW_MILLIS): Boolean {
        val sinceMillis = System.currentTimeMillis() - windowMillis
        val mostRecent = actionOutcomeDao.mostRecentByIdempotencyKey(idempotencyKey, sinceMillis)
        return mostRecent?.outcomeState?.countsAsCommitted() == true
    }

    suspend fun recordMistake(type: TrustEventType, description: String) {
        trustLedgerDao.insert(TrustLedgerEntity(type = type, description = description))
    }

    suspend fun scoreSnapshot(): Int = TrustScoreEngine.score(actionOutcomeDao.getAll())

    suspend fun recentLedger(limit: Int = 20): List<TrustLedgerEntity> = trustLedgerDao.recent(limit)

    suspend fun recentOutcomes(limit: Int = 20): List<ActionOutcomeEntity> = actionOutcomeDao.recent(limit)

    /**
     * Committed sends to [recipient] for [actionType] since [sinceMillis] — thread
     * provenance's actual query: "what have I already sent this person." Filters to
     * [ActionOutcomeState.countsAsCommitted] outcomes only, same distinction
     * [wasRecentlyExecuted] draws — a failed attempt was never actually sent.
     */
    suspend fun recentSendsTo(recipient: String, actionType: String, sinceMillis: Long): List<ActionOutcomeEntity> =
        actionOutcomeDao.recentByRecipient(recipient, actionType, sinceMillis)
            .filter { it.outcomeState.countsAsCommitted() }

    /** Call after the user approves a proposed reply/plan — feeds the adaptive-autonomy threshold. */
    suspend fun recordApproval(actionType: NuaActionType): AutonomyPreferenceEntity {
        val existing = autonomyPreferenceDao.get(actionType.name)
        val updated = (existing ?: AutonomyPreferenceEntity(actionType = actionType.name))
            .copy(approvedCount = (existing?.approvedCount ?: 0) + 1)
        autonomyPreferenceDao.upsert(updated)
        return updated
    }

    suspend fun isAutoApproved(actionType: NuaActionType): Boolean {
        val preference = autonomyPreferenceDao.get(actionType.name) ?: return false
        return isGrantActive(preference.autoApproveEnabled, preference.expiresAt, System.currentTimeMillis())
    }

    /**
     * Enabling issues a fresh [AUTONOMY_GRANT_DURATION_MILLIS] grant — every auto-approve
     * grant has a review date, never indefinite standing autonomy. Disabling (an explicit
     * revoke) clears it immediately.
     */
    suspend fun setAutoApprove(actionType: NuaActionType, enabled: Boolean) {
        val existing = autonomyPreferenceDao.get(actionType.name)
            ?: AutonomyPreferenceEntity(actionType = actionType.name)
        val expiresAt = if (enabled) System.currentTimeMillis() + AUTONOMY_GRANT_DURATION_MILLIS else null
        autonomyPreferenceDao.upsert(existing.copy(autoApproveEnabled = enabled, expiresAt = expiresAt))
    }

    /** Action types that have been approved enough times to suggest auto-approving, but aren't yet. */
    suspend fun autonomySuggestions(): List<AutonomyPreferenceEntity> =
        autonomyPreferenceDao.getAll().filter { isAutonomySuggested(it, AUTONOMY_SUGGESTION_THRESHOLD) }

    suspend fun allAutonomyPreferences(): List<AutonomyPreferenceEntity> = autonomyPreferenceDao.getAll()

    /** Currently-active grants only — the transparent "what may NUA do without asking"
     *  feed the directive names, not every preference row (a lapsed or never-enabled one
     *  isn't autonomy NUA currently has). */
    suspend fun activeAutonomyGrants(): List<AutonomyPreferenceEntity> =
        autonomyPreferenceDao.getAll().filter { isGrantActive(it.autoApproveEnabled, it.expiresAt, System.currentTimeMillis()) }

    /** Issues (or replaces) the context-bounded contract for [actionType] — see
     *  AutonomyContractEntity's own doc comment for what each dimension means. */
    suspend fun createContract(
        actionType: NuaActionType,
        recipient: String? = null,
        riskCeiling: AutonomyTier? = null,
        maxPerWindow: Int? = null,
        windowMillis: Long? = null,
        durationMillis: Long = AUTONOMY_GRANT_DURATION_MILLIS,
        shadowMode: Boolean = false,
    ) {
        autonomyContractDao.upsert(
            AutonomyContractEntity(
                actionType = actionType.name,
                recipient = recipient,
                riskCeiling = riskCeiling,
                maxPerWindow = maxPerWindow,
                windowMillis = windowMillis,
                expiresAt = System.currentTimeMillis() + durationMillis,
                shadowMode = shadowMode,
            ),
        )
    }

    suspend fun revokeContract(actionType: NuaActionType) = autonomyContractDao.suspendContract(actionType.name)

    suspend fun allContracts(): List<AutonomyContractEntity> = autonomyContractDao.getAll()

    /** Live view of every contract, for a settings screen to observe directly rather than
     *  re-polling [allContracts]. */
    fun observeContracts(): Flow<List<AutonomyContractEntity>> = autonomyContractDao.observeAll()

    /**
     * The single decision point a contract contributes to autonomy — always computed for
     * every proposal, never skipped, fail-closed ([ContractDecision.NoContract]/[ContractDecision.Deny]
     * both mean "don't auto-approve via a contract"; the legacy grant is evaluated
     * completely independently by [isAutoApproved] and can still separately permit the
     * same action — see NuaViewModel.applyPendingEffect, the one centralized call site
     * for both). A shadow-mode contract's real decision is still returned here in full —
     * the caller, not this function, is responsible for never acting on it when
     * [AutonomyContractEntity.shadowMode] is true (see [recordShadowPrediction]).
     */
    suspend fun contractDecisionFor(
        actionType: NuaActionType,
        recipient: String?,
        now: Long = System.currentTimeMillis(),
    ): Pair<AutonomyContractEntity?, ContractDecision> {
        val contract = autonomyContractDao.get(actionType.name) ?: return null to ContractDecision.NoContract
        val cap = contract.maxPerWindow
        val window = contract.windowMillis
        val recentCommittedCount = if (cap != null && window != null) {
            actionOutcomeDao.recentByActionType(actionType.name, now - window).count { it.outcomeState.countsAsCommitted() }
        } else {
            0
        }
        return contract to evaluateContract(contract, actionType, recipient, now, recentCommittedCount)
    }

    /** Records what a shadow contract would have decided, without ever acting on it.
     *  Returns the new row's id so the caller can later resolve it via
     *  [resolveShadowPrediction] against what the user actually did with this exact
     *  proposal — see NuaUiState.pendingShadowPredictionId. */
    suspend fun recordShadowPrediction(
        contract: AutonomyContractEntity,
        actionType: NuaActionType,
        recipient: String?,
        decision: ContractDecision,
    ): Long = shadowPredictionDao.insert(
        ShadowPredictionEntity(
            contractId = contract.id,
            actionType = actionType.name,
            recipient = recipient,
            predictedPermit = decision is ContractDecision.Permit,
            reason = when (decision) {
                is ContractDecision.Permit -> "would auto-approve"
                is ContractDecision.Deny -> decision.reason
                ContractDecision.NoContract -> "no contract"
            },
        ),
    )

    /** Resolves a shadow prediction against what the user actually did with the proposal
     *  it was recorded for — called from NuaViewModel's confirmPending-/dismissPending- methods. */
    suspend fun resolveShadowPrediction(predictionId: Long, approved: Boolean) =
        shadowPredictionDao.resolve(predictionId, if (approved) "APPROVED" else "REJECTED")

    suspend fun recentResolvedShadowPredictions(limit: Int = 50): List<ShadowPredictionEntity> =
        shadowPredictionDao.recentResolved(limit)

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
