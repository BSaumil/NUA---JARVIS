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
    private val safeSpecPolicyStore: SafeSpecPolicyStore,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apply(candidate: RepairCandidate, policyDecision: RepairPolicyDecision): RuntimeTuningApplyResult {
        if (policyDecision.decision != PromotionDecision.AUTO_PROMOTE_RUNTIME) {
            return RuntimeTuningApplyResult(false, "policy did not authorize runtime promotion")
        }
        return when (candidate.kind) {
            RepairKind.RUNTIME_TUNING -> {
                if (candidate.runtimeTunings.isEmpty()) {
                    return RuntimeTuningApplyResult(false, "candidate has no runtime tuning payload")
                }
                val previous = routingPolicyStore.current()
                val next = applyPatches(previous, candidate.runtimeTunings)
                    ?: return RuntimeTuningApplyResult(false, "runtime tuning payload is outside the whitelist/range")
                saveRoutingRollback(candidate.id, previous)
                routingPolicyStore.replace(next)
                RuntimeTuningApplyResult(true, "reversible routing policy tuning applied")
            }
            RepairKind.SPEC_TUNING -> {
                if (candidate.specTunings.isEmpty()) {
                    return RuntimeTuningApplyResult(false, "candidate has no spec tuning payload")
                }
                val previousPrompt = safeSpecPolicyStore.prompt()
                val previousResearch = safeSpecPolicyStore.research()
                val next = applySpecPatches(previousPrompt, previousResearch, candidate.specTunings)
                    ?: return RuntimeTuningApplyResult(false, "spec tuning payload is outside the whitelist/range")
                saveSpecRollback(candidate.id, previousPrompt, previousResearch)
                safeSpecPolicyStore.replace(next.first, next.second)
                RuntimeTuningApplyResult(true, "reversible prompt/research spec tuning applied")
            }
            else -> RuntimeTuningApplyResult(false, "candidate kind has no runtime apply path")
        }
    }

    fun rollback(candidateId: String): RuntimeTuningApplyResult {
        val routing = loadRoutingRollback(candidateId)
        if (routing != null) {
            routingPolicyStore.replace(routing)
            prefs.edit().remove("routing:$candidateId").remove(candidateId).apply()
            return RuntimeTuningApplyResult(true, "routing tuning rolled back")
        }
        val spec = loadSpecRollback(candidateId)
        if (spec != null) {
            safeSpecPolicyStore.replace(spec.first, spec.second)
            prefs.edit().remove("spec:$candidateId").apply()
            return RuntimeTuningApplyResult(true, "prompt/research spec tuning rolled back")
        }
        return RuntimeTuningApplyResult(false, "no rollback snapshot found")
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

    private fun saveRoutingRollback(id: String, policy: RoutingLearningPolicy) {
        prefs.edit()
            .putString(
                "routing:$id",
                listOf(
                    policy.minSamples,
                    (policy.minSuccessRate * 10_000).toInt(),
                    policy.memoryDays,
                ).joinToString(","),
            )
            .apply()
    }

    private fun loadRoutingRollback(id: String): RoutingLearningPolicy? {
        val raw = prefs.getString("routing:$id", null) ?: prefs.getString(id, null) ?: return null
        val parts = raw.split(',')
        if (parts.size != 3) return null
        val minSamples = parts[0].toIntOrNull() ?: return null
        val minRateBps = parts[1].toIntOrNull() ?: return null
        val days = parts[2].toIntOrNull() ?: return null
        return RoutingLearningPolicy(minSamples, minRateBps / 10_000.0, days)
    }

    private fun saveSpecRollback(
        id: String,
        prompt: PromptSpecPolicy,
        research: ResearchSpecPolicy,
    ) {
        prefs.edit().putString(
            "spec:$id",
            listOf(
                prompt.schemaReminderCount,
                prompt.maxRepairAttempts,
                research.maxSubquestions,
                research.crossCheckDepth,
            ).joinToString(","),
        ).apply()
    }

    private fun loadSpecRollback(id: String): Pair<PromptSpecPolicy, ResearchSpecPolicy>? {
        val parts = prefs.getString("spec:$id", null)?.split(',') ?: return null
        if (parts.size != 4) return null
        val schema = parts[0].toIntOrNull() ?: return null
        val repair = parts[1].toIntOrNull() ?: return null
        val subquestions = parts[2].toIntOrNull() ?: return null
        val crossCheck = parts[3].toIntOrNull() ?: return null
        val applied = applySpecPatches(
            PromptSpecPolicy(),
            ResearchSpecPolicy(),
            listOf(
                SpecTuningPatch(SpecTuningKey.PROMPT_SCHEMA_REMINDERS, schema),
                SpecTuningPatch(SpecTuningKey.PROMPT_REPAIR_ATTEMPTS, repair),
                SpecTuningPatch(SpecTuningKey.RESEARCH_MAX_SUBQUESTIONS, subquestions),
                SpecTuningPatch(SpecTuningKey.RESEARCH_CROSSCHECK_DEPTH, crossCheck),
            ),
        ) ?: return null
        return applied
    }
}
