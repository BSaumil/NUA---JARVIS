package com.nua.assistant.ai.mesh

enum class ModelProviderTier {
    LOCAL_RULES,
    LOCAL_MODEL,
    PRIVATE_OS_MODEL,
    CLOUD_FAST,
    CLOUD_FRONTIER,
}

/**
 * Pure routing order. The Mesh never invents availability; this only orders the tiers
 * that the runtime detector has already proven usable.
 */
fun fallbackOrder(
    contract: TaskContract,
    availableTiers: Set<ModelProviderTier>,
): List<ModelProviderTier> {
    val priority = listOf(
        ModelProviderTier.LOCAL_RULES,
        ModelProviderTier.LOCAL_MODEL,
        ModelProviderTier.PRIVATE_OS_MODEL,
        ModelProviderTier.CLOUD_FAST,
        ModelProviderTier.CLOUD_FRONTIER,
    )
    val cloudTiers = setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)
    return priority
        .filterNot { (contract.requiresOffline || !contract.allowCloud) && it in cloudTiers }
        .filterNot { contract.requiresFrontierCapability && it == ModelProviderTier.CLOUD_FAST }
        .filter { it in availableTiers }
}
