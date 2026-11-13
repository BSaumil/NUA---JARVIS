package com.nua.assistant.ai.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelRoutingLearningTest {
    @Test
    fun `reliability learning never introduces a tier absent from privacy-safe base order`() {
        val base = listOf(ModelProviderTier.PRIVATE_OS_MODEL)
        val stats = mapOf(
            ModelProviderTier.PRIVATE_OS_MODEL to RoutingTierStats(
                samples = 10,
                successes = 0,
                updatedAtMillis = 1_000,
            ),
            ModelProviderTier.CLOUD_FAST to RoutingTierStats(
                samples = 10,
                successes = 10,
                updatedAtMillis = 1_000,
            ),
        )

        assertEquals(
            listOf(ModelProviderTier.PRIVATE_OS_MODEL),
            applyReliabilityLearning(base, stats, nowMillis = 2_000),
        )
    }

    @Test
    fun `repeatedly failing private tier is deprioritized for ordinary allowed cloud task`() {
        val base = listOf(
            ModelProviderTier.PRIVATE_OS_MODEL,
            ModelProviderTier.CLOUD_FAST,
            ModelProviderTier.CLOUD_FRONTIER,
        )
        val stats = mapOf(
            ModelProviderTier.PRIVATE_OS_MODEL to RoutingTierStats(
                samples = 8,
                successes = 1,
                updatedAtMillis = 10_000,
            ),
        )

        assertEquals(
            listOf(
                ModelProviderTier.CLOUD_FAST,
                ModelProviderTier.CLOUD_FRONTIER,
                ModelProviderTier.PRIVATE_OS_MODEL,
            ),
            applyReliabilityLearning(base, stats, nowMillis = 11_000),
        )
    }

    @Test
    fun `insufficient samples preserve local-first order`() {
        val base = listOf(ModelProviderTier.PRIVATE_OS_MODEL, ModelProviderTier.CLOUD_FAST)
        val stats = mapOf(
            ModelProviderTier.PRIVATE_OS_MODEL to RoutingTierStats(
                samples = 4,
                successes = 0,
                updatedAtMillis = 10_000,
            ),
        )
        assertEquals(base, applyReliabilityLearning(base, stats, nowMillis = 11_000))
    }

    @Test
    fun `stale failure history expires and local tier gets another chance`() {
        val base = listOf(ModelProviderTier.PRIVATE_OS_MODEL, ModelProviderTier.CLOUD_FAST)
        val old = 1_000L
        val eightDays = 8L * 24 * 60 * 60 * 1000
        val stats = mapOf(
            ModelProviderTier.PRIVATE_OS_MODEL to RoutingTierStats(
                samples = 20,
                successes = 0,
                updatedAtMillis = old,
            ),
        )
        assertEquals(base, applyReliabilityLearning(base, stats, nowMillis = old + eightDays))
    }
}
