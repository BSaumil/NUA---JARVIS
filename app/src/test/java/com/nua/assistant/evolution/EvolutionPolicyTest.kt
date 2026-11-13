package com.nua.assistant.evolution

import org.junit.Assert.assertEquals
import org.junit.Test

private val greenLab = RepairLabResult(
    validHarness = true,
    eligibleForPromotion = true,
    reasons = emptyList(),
)

private fun candidate(
    kind: RepairKind = RepairKind.RUNTIME_TUNING,
    risk: RepairRisk = RepairRisk.LOW,
    reversible: Boolean = true,
    impact: RepairImpact = RepairImpact(),
) = RepairCandidate(
    id = "candidate",
    failureSignature = "sig",
    kind = kind,
    risk = risk,
    reason = "test",
    reversible = reversible,
    impact = impact,
    runtimeTunings = if (kind == RepairKind.RUNTIME_TUNING) {
        listOf(RuntimeTuningPatch(RuntimeTuningKey.MODEL_ROUTING_MIN_SAMPLES, 6))
    } else {
        emptyList()
    },
)

class EvolutionPolicyTest {
    @Test
    fun `R3 can auto promote only low risk reversible runtime repair`() {
        val result = decideRepairPromotion(
            RepairLevel.R3_AUTO_PROMOTE_RUNTIME,
            candidate(),
            greenLab,
        )
        assertEquals(PromotionDecision.AUTO_PROMOTE_RUNTIME, result.decision)
    }

    @Test
    fun `source patch never auto promotes even at R4`() {
        val result = decideRepairPromotion(
            RepairLevel.R4_CODE_PROMOTION_REQUIRES_APPROVAL,
            candidate(kind = RepairKind.SOURCE_PATCH, risk = RepairRisk.MEDIUM),
            greenLab,
        )
        assertEquals(PromotionDecision.REQUIRES_OWNER_APPROVAL, result.decision)
    }

    @Test
    fun `authority expansion requires owner approval rather than self grant`() {
        val result = decideRepairPromotion(
            RepairLevel.R4_CODE_PROMOTION_REQUIRES_APPROVAL,
            candidate(
                impact = RepairImpact(newAndroidPermissions = setOf("android.permission.SEND_SMS")),
            ),
            greenLab,
        )
        assertEquals(PromotionDecision.REQUIRES_OWNER_APPROVAL, result.decision)
    }

    @Test
    fun `UAF bypass is refused at every repair level`() {
        val result = decideRepairPromotion(
            RepairLevel.R4_CODE_PROMOTION_REQUIRES_APPROVAL,
            candidate(impact = RepairImpact(bypassesUaf = true)),
            greenLab,
        )
        assertEquals(PromotionDecision.REFUSE, result.decision)
    }

    @Test
    fun `R2 stops at automatic testing`() {
        val result = decideRepairPromotion(
            RepairLevel.R2_AUTO_TEST,
            candidate(),
            greenLab,
        )
        assertEquals(PromotionDecision.AUTO_TEST, result.decision)
    }
}
