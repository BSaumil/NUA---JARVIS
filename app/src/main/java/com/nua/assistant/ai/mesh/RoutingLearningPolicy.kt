package com.nua.assistant.ai.mesh

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class RoutingLearningPolicy(
    val minSamples: Int = 5,
    val minSuccessRate: Double = 0.45,
    val memoryDays: Int = 7,
)

@Singleton
class RoutingLearningPolicyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("nua_model_routing_policy", Context.MODE_PRIVATE)

    fun current(): RoutingLearningPolicy = RoutingLearningPolicy(
        minSamples = prefs.getInt("min_samples", 5).coerceIn(3, 20),
        minSuccessRate = prefs.getInt("min_success_rate_bps", 4_500).coerceIn(3_000, 8_000) / 10_000.0,
        memoryDays = prefs.getInt("memory_days", 7).coerceIn(1, 30),
    )

    fun replace(policy: RoutingLearningPolicy) {
        prefs.edit()
            .putInt("min_samples", policy.minSamples.coerceIn(3, 20))
            .putInt("min_success_rate_bps", (policy.minSuccessRate * 10_000).toInt().coerceIn(3_000, 8_000))
            .putInt("memory_days", policy.memoryDays.coerceIn(1, 30))
            .apply()
    }
}
