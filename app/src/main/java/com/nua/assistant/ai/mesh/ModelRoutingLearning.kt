package com.nua.assistant.ai.mesh

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val MIN_ROUTING_SAMPLES = 5
private const val MIN_SUCCESS_RATE = 0.45
private const val ROUTING_MEMORY_MILLIS = 7L * 24 * 60 * 60 * 1000

data class RoutingTierStats(
    val samples: Int = 0,
    val successes: Int = 0,
    val totalLatencyMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    val successRate: Double
        get() = if (samples == 0) 1.0 else successes.toDouble() / samples

    val averageLatencyMillis: Long
        get() = if (samples == 0) 0 else totalLatencyMillis / samples
}

/**
 * Pure safety-preserving learning rule. It can only reorder tiers that already survived
 * [fallbackOrder]; it can never insert a cloud tier, widen a privacy contract, or invent
 * availability. A tier is deprioritized only after enough recent failures to establish a
 * signal; stale history is ignored so an AICore/model update can recover automatically.
 */
fun applyReliabilityLearning(
    baseOrder: List<ModelProviderTier>,
    stats: Map<ModelProviderTier, RoutingTierStats>,
    nowMillis: Long,
): List<ModelProviderTier> {
    if (baseOrder.size < 2) return baseOrder

    fun unreliable(tier: ModelProviderTier): Boolean {
        val s = stats[tier] ?: return false
        if (s.samples < MIN_ROUTING_SAMPLES) return false
        if (nowMillis - s.updatedAtMillis > ROUTING_MEMORY_MILLIS) return false
        return s.successRate < MIN_SUCCESS_RATE
    }

    val healthyOrUnknown = baseOrder.filterNot(::unreliable)
    val unreliable = baseOrder.filter(::unreliable)
    return healthyOrUnknown + unreliable
}

interface ModelRoutingLearning {
    fun order(
        task: InferenceTaskType,
        baseOrder: List<ModelProviderTier>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ModelProviderTier>

    fun record(
        task: InferenceTaskType,
        tier: ModelProviderTier,
        success: Boolean,
        latencyMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    )
}

/**
 * Content-free routing telemetry. Only aggregate counts and latency are stored; prompts,
 * responses, user identifiers and memory content are never written here.
 */
@Singleton
class SharedPreferencesModelRoutingLearning @Inject constructor(
    @ApplicationContext context: Context,
) : ModelRoutingLearning {
    private val prefs = context.getSharedPreferences("nua_model_routing_learning", Context.MODE_PRIVATE)

    override fun order(
        task: InferenceTaskType,
        baseOrder: List<ModelProviderTier>,
        nowMillis: Long,
    ): List<ModelProviderTier> = synchronized(this) {
        val stats = baseOrder.associateWith { read(task, it) }
        applyReliabilityLearning(baseOrder, stats, nowMillis)
    }

    override fun record(
        task: InferenceTaskType,
        tier: ModelProviderTier,
        success: Boolean,
        latencyMillis: Long,
        nowMillis: Long,
    ) = synchronized(this) {
        val old = read(task, tier)
        val fresh = if (
            old.updatedAtMillis == 0L ||
            nowMillis - old.updatedAtMillis > ROUTING_MEMORY_MILLIS
        ) {
            RoutingTierStats()
        } else {
            old
        }
        val next = fresh.copy(
            samples = fresh.samples + 1,
            successes = fresh.successes + if (success) 1 else 0,
            totalLatencyMillis = fresh.totalLatencyMillis + latencyMillis.coerceAtLeast(0),
            updatedAtMillis = nowMillis,
        )
        write(task, tier, next)
    }

    private fun prefix(task: InferenceTaskType, tier: ModelProviderTier): String =
        "${task.name}:${tier.name}:"

    private fun read(task: InferenceTaskType, tier: ModelProviderTier): RoutingTierStats {
        val p = prefix(task, tier)
        return RoutingTierStats(
            samples = prefs.getInt(p + "samples", 0),
            successes = prefs.getInt(p + "successes", 0),
            totalLatencyMillis = prefs.getLong(p + "latency", 0L),
            updatedAtMillis = prefs.getLong(p + "updated", 0L),
        )
    }

    private fun write(task: InferenceTaskType, tier: ModelProviderTier, stats: RoutingTierStats) {
        val p = prefix(task, tier)
        prefs.edit()
            .putInt(p + "samples", stats.samples)
            .putInt(p + "successes", stats.successes)
            .putLong(p + "latency", stats.totalLatencyMillis)
            .putLong(p + "updated", stats.updatedAtMillis)
            .apply()
    }
}
