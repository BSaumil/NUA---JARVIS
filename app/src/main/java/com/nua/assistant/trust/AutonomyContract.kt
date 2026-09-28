package com.nua.assistant.trust

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.memory.AutonomyContractEntity

/**
 * The Contextual Autonomy Contracts directive (Feature 5) — context-bounded autonomy,
 * extending rather than replacing the legacy unscoped grant (see AutonomyGrant.kt's
 * AUTONOMY_GRANT_DURATION_MILLIS/isGrantActive). A contract adds four real, evaluable
 * dimensions on top of the legacy grant's single actionType key: recipient scope, a risk
 * ceiling against [AutonomyTier], a frequency cap, and its own expiry. Explicitly not
 * attempted this round: place/location (no location subsystem feeds this decision
 * point), data-category (no such taxonomy exists yet), confidence-threshold (the
 * proposals this evaluates — SmsProposed/ReplyProposed/PlanProposed — don't carry a
 * confidence score through to this point), and adapter-scope (that's the Universal
 * Action Fabric's CapabilityDescriptor concept, a separate dispatch path from the legacy
 * sendMessage/pendingEffectFor flow this contract governs).
 */
sealed class ContractDecision {
    /** No contract row exists for this action type — the legacy grant is the only vote. */
    data object NoContract : ContractDecision()
    data object Permit : ContractDecision()
    data class Deny(val reason: String) : ContractDecision()
}

/**
 * Pure: what [contract] decides for one proposed action, right now. Fails closed on every
 * dimension — a null/unset dimension means "unrestricted on this axis," never "permit
 * anyway on ambiguity." [recentCommittedCountInWindow] is supplied by the caller
 * ([TrustRepository.contractDecisionFor]) rather than computed here, keeping this
 * function itself free of any database access and fully deterministic given its inputs.
 */
fun evaluateContract(
    contract: AutonomyContractEntity,
    actionType: NuaActionType,
    recipient: String?,
    now: Long,
    recentCommittedCountInWindow: Int,
): ContractDecision {
    if (!contract.active) return ContractDecision.Deny("contract suspended")
    if (contract.expiresAt <= now) return ContractDecision.Deny("contract expired")
    if (contract.recipient != null && contract.recipient != recipient) {
        return ContractDecision.Deny("recipient out of scope")
    }
    val ceiling = contract.riskCeiling
    if (ceiling != null && autonomyTierFor(actionType).ordinal > ceiling.ordinal) {
        return ContractDecision.Deny("risk ceiling exceeded")
    }
    val cap = contract.maxPerWindow
    if (cap != null && recentCommittedCountInWindow >= cap) {
        return ContractDecision.Deny("frequency cap reached")
    }
    return ContractDecision.Permit
}

/**
 * Pure: whether a contract has drifted enough to auto-suspend, given its most recent
 * outcomes for this action type ([recentOutcomesMostRecentFirst], newest first — see
 * [com.nua.assistant.memory.ActionOutcomeDao.mostRecentByActionType]). Two or more
 * failures within the last three attempts trips it — a real spike, distinct from the
 * legacy [AutonomyPreferenceEntity] grant's own zero-tolerance revoke-on-any-failure
 * (see [TrustRepository.revokeAutoApproveIfGranted]), which a single flaky attempt
 * shouldn't cost a contract. Fewer than [lookback] attempts recorded yet never trips —
 * not enough signal to call it a spike.
 */
fun contractShouldSuspend(
    recentOutcomesMostRecentFirst: List<ActionOutcomeState>,
    lookback: Int = 3,
    failureThreshold: Int = 2,
): Boolean {
    if (recentOutcomesMostRecentFirst.size < lookback) return false
    return recentOutcomesMostRecentFirst.take(lookback).count { it.countsAsFailure() } >= failureThreshold
}
