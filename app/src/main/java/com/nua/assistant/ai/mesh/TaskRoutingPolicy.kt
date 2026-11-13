package com.nua.assistant.ai.mesh

enum class TaskDifficulty { TRIVIAL, SIMPLE, MODERATE, COMPLEX, FRONTIER }
enum class RoutingPreference { BALANCED, FASTEST, LOWEST_COST, MOST_PRIVATE, LOWEST_ENERGY }
enum class HybridMode {
    LOCAL_FIRST,
    LOCAL_DRAFT_FRONTIER_REFINE,
    FRONTIER_PLAN_LOCAL_EXECUTE,
    LOCAL_ADVISORS_FRONTIER_JUDGE,
    PARALLEL_ADVISORS_DETERMINISTIC,
    TOOL_ORCHESTRA,
    RESEARCH_ORCHESTRA,
}

data class TaskProfile(
    val difficulty: TaskDifficulty? = null,
    val structuredOutput: Boolean = false,
    val contextChars: Int = 0,
    val reasoningDepth: Int = 1,
    val requiresTools: Boolean = false,
    val multimodal: Boolean = false,
    val latencyPriority: Boolean = false,
    val routingPreference: RoutingPreference = RoutingPreference.BALANCED,
    val maxCloudTier: ModelProviderTier? = null,
)

data class RoutingEnvironment(
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val powerSave: Boolean = false,
    val thermalStatus: Int? = null,
    val networkClass: String? = null,
)

data class ModelRoutingDecision(
    val orderedTiers: List<ModelProviderTier>,
    val difficulty: TaskDifficulty,
    val reasonCodes: List<String>,
)

fun estimateDifficulty(contract: TaskContract, promptChars: Int): TaskDifficulty {
    contract.profile.difficulty?.let { return it }
    if (contract.requiresFrontierCapability) return TaskDifficulty.FRONTIER
    val score =
        (if (contract.task in setOf(InferenceTaskType.REASONING, InferenceTaskType.PLANNING, InferenceTaskType.LONG_CONTEXT_SYNTHESIS, InferenceTaskType.DOCUMENT_QA)) 2 else 0) +
        (if (promptChars > 12_000 || contract.profile.contextChars > 12_000) 2 else if (promptChars > 4_000) 1 else 0) +
        (contract.profile.reasoningDepth.coerceIn(1, 4) - 1) +
        (if (contract.profile.requiresTools) 1 else 0) +
        (if (contract.profile.multimodal) 1 else 0)
    return when {
        score <= 0 -> TaskDifficulty.TRIVIAL
        score <= 2 -> TaskDifficulty.SIMPLE
        score <= 4 -> TaskDifficulty.MODERATE
        score <= 6 -> TaskDifficulty.COMPLEX
        else -> TaskDifficulty.FRONTIER
    }
}

fun routeModelTiers(
    contract: TaskContract,
    available: Set<ModelProviderTier>,
    environment: RoutingEnvironment,
    promptChars: Int,
): ModelRoutingDecision {
    val policySafe = fallbackOrder(contract, available).toMutableList()
    val difficulty = estimateDifficulty(contract, promptChars)
    val reasons = mutableListOf<String>()

    val maxCloud = contract.profile.maxCloudTier
    if (maxCloud == ModelProviderTier.CLOUD_FAST) {
        policySafe.remove(ModelProviderTier.CLOUD_FRONTIER)
        reasons += "COST_CEILING_FAST_CLOUD"
    }

    val cloud = setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)
    val local = setOf(ModelProviderTier.LOCAL_RULES, ModelProviderTier.LOCAL_MODEL, ModelProviderTier.PRIVATE_OS_MODEL)

    fun prefer(groups: List<Set<ModelProviderTier>>) {
        val ranked = groups.flatMap { group -> policySafe.filter { it in group } }
        val remaining = policySafe.filterNot { it in ranked }
        policySafe.clear()
        policySafe += ranked + remaining
    }

    when (contract.profile.routingPreference) {
        RoutingPreference.MOST_PRIVATE -> {
            prefer(listOf(local, cloud))
            reasons += "PREFERENCE_PRIVACY"
        }
        RoutingPreference.LOWEST_COST -> {
            prefer(listOf(local, setOf(ModelProviderTier.CLOUD_FAST), setOf(ModelProviderTier.CLOUD_FRONTIER)))
            reasons += "PREFERENCE_COST"
        }
        RoutingPreference.FASTEST -> {
            if (environment.networkClass != "OFFLINE") {
                prefer(listOf(setOf(ModelProviderTier.CLOUD_FAST), setOf(ModelProviderTier.PRIVATE_OS_MODEL), setOf(ModelProviderTier.CLOUD_FRONTIER), setOf(ModelProviderTier.LOCAL_RULES, ModelProviderTier.LOCAL_MODEL)))
            }
            reasons += "PREFERENCE_LATENCY"
        }
        RoutingPreference.LOWEST_ENERGY -> {
            val stressed = environment.powerSave || (!environment.charging && (environment.batteryPercent ?: 100) <= 20) || (environment.thermalStatus ?: 0) >= 4
            if (stressed && policySafe.any { it in cloud }) {
                prefer(listOf(setOf(ModelProviderTier.CLOUD_FAST), local, setOf(ModelProviderTier.CLOUD_FRONTIER)))
                reasons += "DEVICE_ENERGY_STRESSED"
            } else {
                prefer(listOf(local, cloud))
                reasons += "DEVICE_ENERGY_LOCAL_OK"
            }
        }
        RoutingPreference.BALANCED -> Unit
    }

    if (contract.profile.routingPreference == RoutingPreference.BALANCED) {
        when (difficulty) {
            TaskDifficulty.TRIVIAL, TaskDifficulty.SIMPLE -> {
                prefer(listOf(local, setOf(ModelProviderTier.CLOUD_FAST), setOf(ModelProviderTier.CLOUD_FRONTIER)))
                reasons += "DIFFICULTY_LOCAL_FIRST"
            }
            TaskDifficulty.MODERATE -> reasons += "DIFFICULTY_BALANCED"
            TaskDifficulty.COMPLEX, TaskDifficulty.FRONTIER -> {
                if (contract.allowCloud && !contract.requiresOffline) {
                    prefer(listOf(setOf(ModelProviderTier.CLOUD_FRONTIER), setOf(ModelProviderTier.PRIVATE_OS_MODEL), setOf(ModelProviderTier.CLOUD_FAST), setOf(ModelProviderTier.LOCAL_RULES, ModelProviderTier.LOCAL_MODEL)))
                    reasons += "DIFFICULTY_FRONTIER_PREFERRED"
                } else {
                    reasons += "DIFFICULTY_FRONTIER_BLOCKED_BY_PRIVACY"
                }
            }
        }
    }

    if (!contract.allowCloud || contract.requiresOffline) reasons += "PRIVACY_CLOUD_EXCLUDED"
    return ModelRoutingDecision(policySafe, difficulty, reasons.distinct())
}

class ModelRoutingPlanner @javax.inject.Inject constructor() {
    fun decide(
        contract: TaskContract,
        available: Set<ModelProviderTier>,
        environment: RoutingEnvironment,
        promptChars: Int,
    ): ModelRoutingDecision = routeModelTiers(contract, available, environment, promptChars)
}
