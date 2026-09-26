package com.nua.assistant.ui.trust

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.memory.AutonomyPreferenceEntity
import com.nua.assistant.memory.TrustLedgerEntity
import com.nua.assistant.trust.TrustRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [TrustUiController.refresh]'s result — score, ledger, autonomy suggestions, and active
 *  grants read together. */
internal data class TrustSettingsState(
    val trustScore: Int,
    val trustLedger: List<TrustLedgerEntity>,
    val autonomySuggestions: List<AutonomyPreferenceEntity>,
    val activeAutonomyGrants: List<AutonomyPreferenceEntity>,
)

/**
 * Pure: assembles the Settings Trust card's state from its four independent sources.
 * [TrustUiController.refresh] is a one-line wrapper passing [TrustRepository]'s own
 * suspend functions in — same shape as `executeSandboxed` (automation/SkillSandbox.kt)
 * and `outcomeForWorkerRun` (ai/WorkerRetryPolicy.kt) elsewhere in this codebase.
 * [TrustRepository] constructs `SharedPreferences` from a real `Context` at
 * instantiation, so nothing that depends on it directly can be built in this project's
 * Context/Robolectric-free JVM unit tests. Taking each source as a suspend lambda
 * sidesteps that wall without touching [TrustRepository] itself — its class is
 * unmodified by this extraction.
 */
internal suspend fun refreshedTrustState(
    scoreSnapshot: suspend () -> Int,
    recentLedger: suspend () -> List<TrustLedgerEntity>,
    autonomySuggestions: suspend () -> List<AutonomyPreferenceEntity>,
    activeAutonomyGrants: suspend () -> List<AutonomyPreferenceEntity>,
): TrustSettingsState = TrustSettingsState(scoreSnapshot(), recentLedger(), autonomySuggestions(), activeAutonomyGrants())

/**
 * Pure: enables auto-approve, then reads back the suggestion list — in that order.
 * [enableAutoApprove]'s effect must be written before [autonomySuggestions] is read, or
 * the action type just enabled would still show up in its own suggestion list
 * ([com.nua.assistant.trust.isAutonomySuggested] requires `!autoApproveEnabled`).
 */
internal suspend fun autonomySuggestionsAfterEnabling(
    enableAutoApprove: suspend () -> Unit,
    autonomySuggestions: suspend () -> List<AutonomyPreferenceEntity>,
): List<AutonomyPreferenceEntity> {
    enableAutoApprove()
    return autonomySuggestions()
}

/**
 * Owns the Settings Trust card's observable state — trust score, the curated ledger,
 * autonomy suggestions, and active auto-approve grants — and the operations that mutate
 * it. Extracted out of
 * `NuaViewModel` (1008 lines, 35 dependencies at the time of this extraction) as the
 * first of several planned seams, per an architecture review that found NuaViewModel's
 * size the proven origin of two serious bugs this session: a Kotlin declaration-order
 * compile error (`bcb4515`) and a biometric step-up bypass (`716cbcf`).
 *
 * Deliberately does **not** own `NuaViewModel.actionOutcomes`: that StateFlow is shared
 * with the Act destination (`refreshAct()`), which is out of scope for this seam — moving
 * it here would pull Act's refresh trigger into a class whose name and purpose is
 * Trust/Autonomy only. `NuaViewModel` keeps updating `actionOutcomes` itself from both
 * `refreshAct()` and `refreshTrust()`, exactly as before.
 *
 * `@Singleton`, matching every other injectable business-logic class in this codebase
 * ([TrustRepository], `WhatNowAdvisor`, `SelfDiagnosticsRepository`) — its cached state
 * is meant to survive exactly as long as the process does, same as the StateFlows it
 * replaces did as long as `NuaViewModel`'s own scope did.
 */
@Singleton
class TrustUiController @Inject constructor(
    private val trustRepository: TrustRepository,
) {
    private val _trustScore = MutableStateFlow<Int?>(null)
    val trustScore: StateFlow<Int?> = _trustScore.asStateFlow()

    private val _trustLedger = MutableStateFlow<List<TrustLedgerEntity>>(emptyList())
    val trustLedger: StateFlow<List<TrustLedgerEntity>> = _trustLedger.asStateFlow()

    private val _autonomySuggestions = MutableStateFlow<List<AutonomyPreferenceEntity>>(emptyList())
    val autonomySuggestions: StateFlow<List<AutonomyPreferenceEntity>> = _autonomySuggestions.asStateFlow()

    /** Currently-active auto-approve grants — the transparent "what may NUA do without
     *  asking, and until when" feed. */
    private val _activeAutonomyGrants = MutableStateFlow<List<AutonomyPreferenceEntity>>(emptyList())
    val activeAutonomyGrants: StateFlow<List<AutonomyPreferenceEntity>> = _activeAutonomyGrants.asStateFlow()

    /** Called when Settings opens — same reasoning as NuaViewModel's other refresh* functions: cheap local reads, not worth a live subscription. */
    suspend fun refresh() {
        val state = refreshedTrustState(
            scoreSnapshot = { trustRepository.scoreSnapshot() },
            recentLedger = { trustRepository.recentLedger() },
            autonomySuggestions = { trustRepository.autonomySuggestions() },
            activeAutonomyGrants = { trustRepository.activeAutonomyGrants() },
        )
        _trustScore.value = state.trustScore
        _trustLedger.value = state.trustLedger
        _autonomySuggestions.value = state.autonomySuggestions
        _activeAutonomyGrants.value = state.activeAutonomyGrants
    }

    /** Immediate revoke — the flip side of [enableAutoApprove], and Earned Autonomy's
     *  own "how to revoke it" requirement. Re-reads both lists afterward: a revoked grant
     *  can reappear as a suggestion again once it's re-approved enough times. */
    suspend fun disableAutoApprove(actionType: NuaActionType) {
        trustRepository.setAutoApprove(actionType, enabled = false)
        _activeAutonomyGrants.value = trustRepository.activeAutonomyGrants()
        _autonomySuggestions.value = trustRepository.autonomySuggestions()
    }

    suspend fun enableAutoApprove(actionType: NuaActionType) {
        _autonomySuggestions.value = autonomySuggestionsAfterEnabling(
            enableAutoApprove = { trustRepository.setAutoApprove(actionType, enabled = true) },
            autonomySuggestions = { trustRepository.autonomySuggestions() },
        )
        // The write already landed inside autonomySuggestionsAfterEnabling above, so this
        // read correctly picks up the grant just issued.
        _activeAutonomyGrants.value = trustRepository.activeAutonomyGrants()
    }
}
