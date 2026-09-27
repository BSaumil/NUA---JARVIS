package com.nua.assistant.ai.mesh

/**
 * The Sovereign Model Mesh directive's five-tier routing chain, in priority order (see
 * [fallbackOrder]). [LOCAL_RULES] and [CLOUD_FAST]/[CLOUD_FRONTIER] have real
 * implementations today; [LOCAL_MODEL]/[PRIVATE_OS_MODEL] are declared but always report
 * unavailable ([ModelAvailabilityDetector]) — no on-device model ships with this app yet.
 * Never represented as available when they aren't; see this feature's own
 * "explicitly not attempted" note in docs/HISTORY.md for why building one is out of
 * scope for this slice.
 */
enum class ModelProviderTier { LOCAL_RULES, LOCAL_MODEL, PRIVATE_OS_MODEL, CLOUD_FAST, CLOUD_FRONTIER }

/**
 * Pure: the ordered list of tiers worth trying for [contract], filtered to only
 * [availableTiers] — [ModelMesh] tries each in the returned order, stopping at the first
 * one that actually produces a usable result (availability alone doesn't guarantee a
 * result — [ModelProviderTier.LOCAL_RULES] can be "available" for a task and still miss
 * a specific input, e.g. an utterance no keyword rule matches). Deterministic given the
 * same inputs — no randomness, no hidden state — so routing behavior is fully unit
 * testable without ever making a real model call.
 */
fun fallbackOrder(contract: TaskContract, availableTiers: Set<ModelProviderTier>): List<ModelProviderTier> {
    val priority = listOf(
        ModelProviderTier.LOCAL_RULES,
        ModelProviderTier.LOCAL_MODEL,
        ModelProviderTier.PRIVATE_OS_MODEL,
        ModelProviderTier.CLOUD_FAST,
        ModelProviderTier.CLOUD_FRONTIER,
    )
    val cloudTiers = setOf(ModelProviderTier.CLOUD_FAST, ModelProviderTier.CLOUD_FRONTIER)
    return priority
        .filterNot { contract.requiresOffline && it in cloudTiers }
        .filterNot { contract.requiresFrontierCapability && it == ModelProviderTier.CLOUD_FAST }
        .filter { it in availableTiers }
}
