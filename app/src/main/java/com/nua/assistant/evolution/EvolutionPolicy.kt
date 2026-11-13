package com.nua.assistant.evolution

enum class PromotionDecision {
    OBSERVE_ONLY,
    SUGGEST_ONLY,
    AUTO_TEST,
    AUTO_PROMOTE_RUNTIME,
    REQUIRES_OWNER_APPROVAL,
    REFUSE,
}

data class RepairPolicyDecision(
    val decision: PromotionDecision,
    val reasons: List<String>,
)

fun decideRepairPromotion(
    level: RepairLevel,
    candidate: RepairCandidate,
    labResult: RepairLabResult?,
): RepairPolicyDecision {
    if (candidate.impact.isForbidden()) {
        return RepairPolicyDecision(
            PromotionDecision.REFUSE,
            listOf("repair attempts to bypass UAF or weaken Guardian"),
        )
    }

    if (level == RepairLevel.R0_OBSERVE) {
        return RepairPolicyDecision(PromotionDecision.OBSERVE_ONLY, listOf("repair level is R0"))
    }
    if (level == RepairLevel.R1_SUGGEST) {
        return RepairPolicyDecision(PromotionDecision.SUGGEST_ONLY, listOf("repair level is R1"))
    }

    if (labResult == null) {
        return RepairPolicyDecision(PromotionDecision.AUTO_TEST, listOf("candidate requires Self-Repair Lab evaluation"))
    }
    if (!labResult.validHarness) {
        return RepairPolicyDecision(PromotionDecision.REFUSE, labResult.reasons)
    }
    if (!labResult.eligibleForPromotion) {
        return RepairPolicyDecision(PromotionDecision.SUGGEST_ONLY, labResult.reasons)
    }

    val authorityExpansion = candidate.impact.hasAuthorityExpansion()
    val highRisk = candidate.risk >= RepairRisk.HIGH
    val codeLike = candidate.kind in setOf(
        RepairKind.SOURCE_PATCH,
        RepairKind.RECIPE_PATCH,
        RepairKind.SKILL_PACKAGE_PATCH,
    )

    if (authorityExpansion || highRisk || codeLike || candidate.impact.irreversibleDataChange) {
        return if (level == RepairLevel.R4_CODE_PROMOTION_REQUIRES_APPROVAL) {
            RepairPolicyDecision(
                PromotionDecision.REQUIRES_OWNER_APPROVAL,
                listOf("tested repair may be prepared for review but cannot self-authorize promotion"),
            )
        } else {
            RepairPolicyDecision(
                PromotionDecision.SUGGEST_ONLY,
                listOf("repair exceeds the R3 runtime auto-promotion envelope"),
            )
        }
    }

    val runtimeEligible =
        candidate.kind in setOf(RepairKind.RUNTIME_TUNING, RepairKind.TRANSFORM_PROGRAM) &&
            candidate.reversible &&
            candidate.risk == RepairRisk.LOW

    return when {
        runtimeEligible && level >= RepairLevel.R3_AUTO_PROMOTE_RUNTIME ->
            RepairPolicyDecision(PromotionDecision.AUTO_PROMOTE_RUNTIME, emptyList())
        else ->
            RepairPolicyDecision(PromotionDecision.AUTO_TEST, listOf("candidate is tested but not auto-promotable at current level"))
    }
}
