package com.nua.assistant.ai.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun contract(
    task: InferenceTaskType = InferenceTaskType.REASONING,
    requiresOffline: Boolean = false,
    requiresFrontierCapability: Boolean = false,
) = TaskContract(task, PrivacySensitivity.MEDIUM, requiresOffline, requiresFrontierCapability)

class ModelProviderTierTest {

    private val allTiers = ModelProviderTier.entries.toSet()

    @Test
    fun `with everything available, the priority order is tried in full, cheapest-first`() {
        assertEquals(
            listOf(ModelProviderTier.LOCAL_RULES, ModelProviderTier.LOCAL_MODEL, ModelProviderTier.PRIVATE_OS_MODEL, ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER),
            fallbackOrder(contract(), allTiers),
        )
    }

    @Test
    fun `only genuinely available tiers are offered`() {
        assertEquals(
            listOf(ModelProviderTier.CLOUD_FAST),
            fallbackOrder(contract(), setOf(ModelProviderTier.CLOUD_FAST)),
        )
    }

    @Test
    fun `requiresOffline excludes both cloud tiers even when they're available`() {
        val order = fallbackOrder(contract(requiresOffline = true), allTiers)
        assertTrue(ModelProviderTier.CLOUD_FAST !in order)
        assertTrue(ModelProviderTier.CLOUD_FRONTIER !in order)
        assertEquals(listOf(ModelProviderTier.LOCAL_RULES, ModelProviderTier.LOCAL_MODEL, ModelProviderTier.PRIVATE_OS_MODEL), order)
    }

    @Test
    fun `requiresOffline with no on-device tier available produces an empty order -- never falls back to network`() {
        assertEquals(emptyList<ModelProviderTier>(), fallbackOrder(contract(requiresOffline = true), setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)))
    }

    @Test
    fun `requiresFrontierCapability skips CLOUD_FAST straight to CLOUD_FRONTIER`() {
        val order = fallbackOrder(contract(requiresFrontierCapability = true), allTiers)
        assertTrue(ModelProviderTier.CLOUD_FAST !in order)
        assertTrue(ModelProviderTier.CLOUD_FRONTIER in order)
    }

    @Test
    fun `a utility task with no frontier requirement includes CLOUD_FAST before CLOUD_FRONTIER`() {
        val order = fallbackOrder(contract(task = InferenceTaskType.INTENT_CLASSIFICATION), allTiers)
        assertTrue(order.indexOf(ModelProviderTier.CLOUD_FAST) < order.indexOf(ModelProviderTier.CLOUD_FRONTIER))
    }

    @Test
    fun `no available tiers at all produces an empty order, never a crash`() {
        assertEquals(emptyList<ModelProviderTier>(), fallbackOrder(contract(), emptySet()))
    }

    @Test
    fun `routing is deterministic -- the same inputs always produce the same order`() {
        val c = contract(requiresFrontierCapability = true)
        assertEquals(fallbackOrder(c, allTiers), fallbackOrder(c, allTiers))
    }
}
