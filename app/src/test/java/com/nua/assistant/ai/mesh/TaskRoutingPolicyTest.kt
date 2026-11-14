package com.nua.assistant.ai.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRoutingPolicyTest {
    private val available = ModelProviderTier.entries.toSet()

    @Test fun `HIGH privacy excludes cloud before energy preference is applied`() {
        val contract = TaskContract(
            InferenceTaskType.REASONING,
            PrivacySensitivity.HIGH,
            profile = TaskProfile(routingPreference = RoutingPreference.LOWEST_ENERGY),
        )
        val decision = routeModelTiers(
            contract,
            available,
            RoutingEnvironment(batteryPercent = 5, powerSave = true, thermalStatus = 5),
            promptChars = 10_000,
        )
        assertTrue(decision.orderedTiers.none { it == ModelProviderTier.CLOUD_FAST || it == ModelProviderTier.CLOUD_FRONTIER })
        assertTrue("PRIVACY_CLOUD_EXCLUDED" in decision.reasonCodes)
    }

    @Test fun `simple balanced task stays local first`() {
        val contract = TaskContract(InferenceTaskType.SUMMARIZATION, PrivacySensitivity.LOW)
        val decision = routeModelTiers(contract, available, RoutingEnvironment(), promptChars = 500)
        assertTrue(decision.orderedTiers.first() in setOf(ModelProviderTier.LOCAL_RULES, ModelProviderTier.LOCAL_MODEL, ModelProviderTier.PRIVATE_OS_MODEL))
    }

    @Test fun `frontier task prefers frontier when privacy permits`() {
        val contract = TaskContract(
            InferenceTaskType.REASONING,
            PrivacySensitivity.LOW,
            profile = TaskProfile(difficulty = TaskDifficulty.FRONTIER),
        )
        val decision = routeModelTiers(contract, available, RoutingEnvironment(), promptChars = 500)
        assertEquals(ModelProviderTier.CLOUD_FRONTIER, decision.orderedTiers.first())
    }

    @Test fun `fast cloud ceiling removes frontier`() {
        val contract = TaskContract(
            InferenceTaskType.REASONING,
            PrivacySensitivity.LOW,
            profile = TaskProfile(difficulty = TaskDifficulty.FRONTIER, maxCloudTier = ModelProviderTier.CLOUD_FAST),
        )
        val decision = routeModelTiers(contract, available, RoutingEnvironment(), promptChars = 500)
        assertFalse(ModelProviderTier.CLOUD_FRONTIER in decision.orderedTiers)
    }
}
