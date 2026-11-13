package com.nua.assistant.evolution

import android.content.Context
import com.nua.assistant.ai.mesh.RoutingLearningPolicy
import com.nua.assistant.ai.mesh.RoutingLearningPolicyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "nua_evolution_rollbacks"

data class RuntimeTuningApplyResult(
    val applied: Boolean,
    val detail: String,
)

@Singleton
class EvolutionRuntimeTuningApplier @Inject constructor(
    @ApplicationContext context: Context,
    private val routingPolicyStore: RoutingLearningPolicyStore,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apply(candidate: RepairCandidate, policyDecision: RepairPolicyDecision): RuntimeTuningApplyResult {
        if (policyDecision.decision != PromotionDecision.AUTO_PROMOTE_RUNTIME) {
            return RuntimeTuningApplyResult(false, "policy did not authorize runtime promotion")
        }
        if (candidate.kind != RepairKind.RUNTIME_TUNING || candidate.runtimeTunings.isEmpty()) {
            return RuntimeTuningApplyResult(false, "candidate has no runtime tuning payload")
        }

        val previous = routingPolicyStore.current()
        val next = applyPatches(previous, candidate.runtimeTunings)
            ?: return RuntimeTuningApplyResult(false, "runtime tuning payload is outside the whitelist/range")

        saveRollback(candidate.id, previous)
        routingPolicyStore.replace(next)
        return RuntimeTuningApplyResult(true, "reversible routing policy tuning applied")
    }

    fun rollback(candidateId: String): RuntimeTuningApplyResult {
        val previous = loadRollback(candidateId)
            ?: return RuntimeTuningApplyResult(false, "no rollback snapshot found")
        routingPolicyStore.replace(previous)
        prefs.edit().remove(candidateId).apply()
        return RuntimeTuningApplyResult(true, "runtime tuning rolled back")
    }

    private fun applyPatches(
        policy: RoutingLearningPolicy,
        patches: List<RuntimeTuningPatch>,
    ): RoutingLearningPolicy? {
        var next = policy
        for (patch in patches) {
            next = when (patch.key) {
                RuntimeTuningKey.MODEL_ROUTING_MIN_SAMPLES -> {
                    if (patch.intValue !in 3..20) return null
                    next.copy(minSamples = patch.intValue)
                }
                RuntimeTuningKey.MODEL_ROUTING_MIN_SUCCESS_RATE_BPS -> {
                    if (patch.intValue !in 3_000..8_000) return null
                    next.copy(minSuccessRate = patch.intValue / 10_000.0)
                }
                RuntimeTuningKey.MODEL_ROUTING_MEMORY_DAYS -> {
                    if (patch.intValue !in 1..30) return null
                    next.copy(memoryDays = patch.intValue)
                }
            }
        }
        return next
    }

    private fun saveRollback(id: String, policy: RoutingLearningPolicy) {
        prefs.edit()
            .putString(
                id,
                listOf(
                    policy.minSamples,
                    (policy.minSuccessRate * 10_000).toInt(),
                    policy.memoryDays,
                ).joinToString(","),
            )
            .apply()
    }

    private fun loadRollback(id: String): RoutingLearningPolicy? {
        val parts = prefs.getString(id, null)?.split(',') ?: return null
        if (parts.size != 3) return null
        val minSamples = parts[0].toIntOrNull() ?: return null
        val minRateBps = parts[1].toIntOrNull() ?: return null
        val days = parts[2].toIntOrNull() ?: return null
        return RoutingLearningPolicy(minSamples, minRateBps / 10_000.0, days)
    }
}
