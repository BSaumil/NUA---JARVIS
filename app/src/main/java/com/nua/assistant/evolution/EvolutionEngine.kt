package com.nua.assistant.evolution

import javax.inject.Inject
import javax.inject.Singleton

data class EvolutionObservationResult(
    val memory: EvolutionMemoryEntry,
    val candidate: RepairCandidate?,
)

data class EvolutionEvaluationResult(
    val candidate: RepairCandidate,
    val lab: RepairLabResult,
    val policy: RepairPolicyDecision,
    val tuningResult: RuntimeTuningApplyResult?,
)

@Singleton
class EvolutionEngine @Inject constructor(
    private val memoryStore: EvolutionMemoryStore,
    private val settingsStore: EvolutionSettingsStore,
    private val generator: HeuristicRepairGenerator,
    private val tuningApplier: EvolutionRuntimeTuningApplier,
) {
    fun observeFailure(
        source: FailureSource,
        component: String,
        category: FailureCategory,
        code: String,
        severity: FailureSeverity = FailureSeverity.MEDIUM,
        repairable: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
    ): EvolutionObservationResult {
        val signature = failureFingerprint(source, component, category, code)
        val observation = FailureObservation(
            id = "$signature:$nowMillis",
            source = source,
            component = component,
            category = category,
            code = code,
            signature = signature,
            severity = severity,
            occurredAt = nowMillis,
            repairable = repairable,
        )
        val memory = memoryStore.recordFailure(observation)

        val candidate = if (
            repairable &&
            memory.occurrences in setOf(3, 5, 10, 20, 50)
        ) {
            generator.propose(memory)?.also(memoryStore::recordCandidate)
        } else {
            null
        }
        return EvolutionObservationResult(memory, candidate)
    }

    fun evaluateCandidate(
        candidateId: String,
        experiment: RepairExperiment,
    ): EvolutionEvaluationResult? {
        val candidate = memoryStore.candidate(candidateId) ?: return null
        val lab = evaluateRepairExperiment(experiment)
        val policy = decideRepairPromotion(settingsStore.repairLevel(), candidate, lab)
        val tuningResult = if (policy.decision == PromotionDecision.AUTO_PROMOTE_RUNTIME) {
            tuningApplier.apply(candidate, policy)
        } else {
            null
        }

        val outcome = when {
            tuningResult?.applied == true -> "AUTO_PROMOTED_RUNTIME"
            policy.decision == PromotionDecision.REQUIRES_OWNER_APPROVAL -> "AWAITING_OWNER_APPROVAL"
            policy.decision == PromotionDecision.REFUSE -> "REFUSED"
            lab.eligibleForPromotion -> policy.decision.name
            else -> "CHALLENGER_REJECTED"
        }
        memoryStore.recordOutcome(
            RepairOutcomeRecord(
                candidateId = candidate.id,
                decision = outcome,
                detail = (policy.reasons + lab.reasons).distinct().joinToString("; ").ifBlank { null },
            ),
        )
        return EvolutionEvaluationResult(candidate, lab, policy, tuningResult)
    }

    fun rollbackRuntimeRepair(candidateId: String): RuntimeTuningApplyResult =
        tuningApplier.rollback(candidateId)

    fun memory(): List<EvolutionMemoryEntry> = memoryStore.memory()
    fun candidates(): List<RepairCandidate> = memoryStore.candidates()
}
