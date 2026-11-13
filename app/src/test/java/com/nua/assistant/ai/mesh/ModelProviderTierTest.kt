package com.nua.assistant.ai.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun contract(
    task: InferenceTaskType = InferenceTaskType.REASONING,
    sensitivity: PrivacySensitivity = PrivacySensitivity.MEDIUM,
    requiresOffline: Boolean = false,
    requiresFrontierCapability: Boolean = false,
    allowCloud: Boolean = sensitivity != PrivacySensitivity.HIGH,
) = TaskContract(task, sensitivity, requiresOffline, requiresFrontierCapability, allowCloud)

class ModelProviderTierTest {
    private val allTiers = ModelProviderTier.entries.toSet()

    @Test
    fun `full availability is local-first then cloud`() {
        assertEquals(
            listOf(
                ModelProviderTier.LOCAL_RULES,
                ModelProviderTier.LOCAL_MODEL,
                ModelProviderTier.PRIVATE_OS_MODEL,
                ModelProviderTier.CLOUD_FAST,
                ModelProviderTier.CLOUD_FRONTIER,
            ),
            fallbackOrder(contract(), allTiers),
        )
    }

    @Test
    fun `HIGH privacy is local-only by default`() {
        val order = fallbackOrder(contract(sensitivity = PrivacySensitivity.HIGH), allTiers)
        assertTrue(ModelProviderTier.CLOUD_FAST !in order)
        assertTrue(ModelProviderTier.CLOUD_FRONTIER !in order)
        assertTrue(ModelProviderTier.PRIVATE_OS_MODEL in order)
    }

    @Test
    fun `explicit HIGH privacy cloud allowance restores cloud candidates`() {
        val order = fallbackOrder(
            contract(sensitivity = PrivacySensitivity.HIGH, allowCloud = true),
            allTiers,
        )
        assertTrue(ModelProviderTier.CLOUD_FAST in order)
        assertTrue(ModelProviderTier.CLOUD_FRONTIER in order)
    }

    @Test
    fun `offline excludes cloud regardless of explicit allowance`() {
        val order = fallbackOrder(
            contract(requiresOffline = true, allowCloud = true),
            allTiers,
        )
        assertTrue(ModelProviderTier.CLOUD_FAST !in order)
        assertTrue(ModelProviderTier.CLOUD_FRONTIER !in order)
    }

    @Test
    fun `frontier requirement skips fast cloud`() {
        val order = fallbackOrder(contract(requiresFrontierCapability = true), allTiers)
        assertTrue(ModelProviderTier.CLOUD_FAST !in order)
        assertTrue(ModelProviderTier.CLOUD_FRONTIER in order)
    }

    @Test
    fun `routing remains deterministic`() {
        val c = contract(requiresFrontierCapability = true)
        assertEquals(fallbackOrder(c, allTiers), fallbackOrder(c, allTiers))
    }
}
