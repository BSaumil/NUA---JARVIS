package com.nua.assistant.security.guardian

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.PlanStep
import com.nua.assistant.memory.AutonomyContractEntity
import com.nua.assistant.trust.autonomyTierFor

/** One anomaly the Sentinel found — a thing worth a human's attention, never itself an
 *  enforcement decision. See [auditContracts]'s own doc comment for why. */
data class SafetyAnomaly(val subject: String, val description: String)

/**
 * Runtime Safety Sentinel — Feature 10 part 2 of the 5-Year Standalone Master Directive,
 * Guardian Lab's first production (not test-only) component. A read-only diagnostic pass
 * over live Contextual Autonomy Contracts (Feature 5), flagging configurations that are
 * misconfigured or stale rather than dangerous: every anomaly this function can find is
 * already independently fail-closed by `trust/AutonomyContract.kt`'s `evaluateContract` —
 * a risk-ceiling-below-fixed-tier contract, for instance, already denies every request; it
 * just does so silently, forever, which is a usability/observability problem, not a
 * security one. This is deliberately **not** a second enforcement mechanism: it never
 * changes a decision `evaluateContract` would make (see
 * `AutonomyContractAdversarialTest.kt`'s own explicit case proving exactly that), it only
 * surfaces rows worth a human's attention. Pure and deterministic given [contracts] and
 * [now] — no database access, no side effects, so it's fully unit-testable and safe to run
 * as often as a caller likes.
 */
fun auditContracts(contracts: List<AutonomyContractEntity>, now: Long): List<SafetyAnomaly> {
    val anomalies = mutableListOf<SafetyAnomaly>()
    contracts.forEach { contract ->
        val action = runCatching { NuaActionType.valueOf(contract.actionType) }.getOrNull()
        if (action == null) {
            anomalies += SafetyAnomaly(contract.actionType, "actionType names no real NuaActionType — an orphaned row from a removed action type")
            return@forEach
        }
        if (contract.active && contract.expiresAt <= now) {
            anomalies += SafetyAnomaly(contract.actionType, "expired but still marked active — a stale row that never actually grants anything")
        }
        val ceiling = contract.riskCeiling
        if (ceiling != null && autonomyTierFor(action).ordinal > ceiling.ordinal) {
            anomalies += SafetyAnomaly(
                contract.actionType,
                "risk ceiling ($ceiling) is below $action's own fixed tier (${autonomyTierFor(action)}) — this contract can never permit anything",
            )
        }
        val cap = contract.maxPerWindow
        if (cap != null && cap <= 0) {
            anomalies += SafetyAnomaly(contract.actionType, "frequency cap ($cap) is zero or negative — this contract can never permit anything")
        }
    }
    return anomalies
}

/**
 * The same diagnostic idea applied to a compiled recipe's steps (Feature 6): a step
 * whose action no longer has a registered capability — e.g. a `NuaSkill` binding removed
 * since this recipe was compiled — will always fail at run time, silently, for a user who
 * has no way to know why. `recipes/RecipeCompiler.kt` already catches this at compile
 * time (an unresolved clause); this catches the same class of problem for a recipe that
 * compiled successfully once but has since gone stale.
 */
fun auditRecipeSteps(steps: List<PlanStep>, descriptorFor: (NuaActionType) -> CapabilityDescriptor?): List<SafetyAnomaly> =
    steps.filter { descriptorFor(it.action) == null }
        .map { SafetyAnomaly(it.id, "no capability is registered for ${it.action} — this step will always fail") }
