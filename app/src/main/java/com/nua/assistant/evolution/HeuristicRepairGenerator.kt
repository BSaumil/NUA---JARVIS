package com.nua.assistant.evolution

import com.nua.assistant.ai.mesh.RoutingLearningPolicyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeuristicRepairGenerator @Inject constructor(
    private val routingPolicyStore: RoutingLearningPolicyStore,
) {
    fun propose(entry: EvolutionMemoryEntry): RepairCandidate? {
        if (entry.occurrences < 3) return null

        if (entry.source == FailureSource.MODEL_MESH &&
            entry.category in setOf(FailureCategory.PROVIDER_FAILURE, FailureCategory.INVALID_OUTPUT)
        ) {
            val currentBps = (routingPolicyStore.current().minSuccessRate * 10_000).toInt()
            val proposed = (currentBps + 500).coerceAtMost(7_500)
            return RepairCandidate(
                failureSignature = entry.signature,
                kind = RepairKind.RUNTIME_TUNING,
                risk = RepairRisk.LOW,
                reason = "Repeated model-provider failure; challenge a stricter reliability threshold.",
                reversible = true,
                runtimeTunings = listOf(
                    RuntimeTuningPatch(
                        RuntimeTuningKey.MODEL_ROUTING_MIN_SUCCESS_RATE_BPS,
                        proposed,
                    ),
                ),
            )
        }

        if (entry.category in setOf(
                FailureCategory.ACTION_EXECUTION,
                FailureCategory.PARSER_SHAPE,
                FailureCategory.DATA_CORRUPTION,
                FailureCategory.REGRESSION,
            )
        ) {
            return RepairCandidate(
                failureSignature = entry.signature,
                kind = RepairKind.SOURCE_PATCH,
                risk = RepairRisk.MEDIUM,
                reason = "Repeated failure requires a code-level challenger in the Self-Repair Lab.",
                reversible = true,
                artifactRef = "evolution://candidate/${entry.signature}",
            )
        }

        return null
    }
}
