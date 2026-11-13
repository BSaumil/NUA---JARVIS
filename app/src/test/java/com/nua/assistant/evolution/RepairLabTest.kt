package com.nua.assistant.evolution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun metrics(
    total: Int = 10,
    passed: Int = 9,
    fixed: Int = 1,
    regressions: Int = 0,
    guardian: Int = 0,
    privacy: Int = 0,
    authority: Int = 0,
    latency: Long = 100,
) = EvaluationMetrics(
    totalCases = total,
    passedCases = passed,
    targetFailuresFixed = fixed,
    regressionFailures = regressions,
    guardianViolations = guardian,
    privacyViolations = privacy,
    authorityViolations = authority,
    meanLatencyMillis = latency,
)

class RepairLabTest {
    @Test
    fun `known bad control must fail before challenger can be trusted`() {
        val result = evaluateRepairExperiment(
            RepairExperiment(
                champion = metrics(),
                challenger = metrics(passed = 10),
                knownBadDetected = false,
            ),
        )
        assertFalse(result.validHarness)
        assertFalse(result.eligibleForPromotion)
    }

    @Test
    fun `challenger that fixes target without regressions is promotable`() {
        val result = evaluateRepairExperiment(
            RepairExperiment(
                champion = metrics(passed = 9, fixed = 0),
                challenger = metrics(passed = 10, fixed = 1),
                knownBadDetected = true,
            ),
        )
        assertTrue(result.validHarness)
        assertTrue(result.eligibleForPromotion)
    }

    @Test
    fun `privacy regression blocks promotion even when correctness improves`() {
        val result = evaluateRepairExperiment(
            RepairExperiment(
                champion = metrics(passed = 8, fixed = 0),
                challenger = metrics(passed = 10, fixed = 2, privacy = 1),
                knownBadDetected = true,
            ),
        )
        assertFalse(result.eligibleForPromotion)
    }

    @Test
    fun `latency explosion blocks promotion`() {
        val result = evaluateRepairExperiment(
            RepairExperiment(
                champion = metrics(latency = 100, fixed = 0),
                challenger = metrics(passed = 10, fixed = 1, latency = 200),
                knownBadDetected = true,
            ),
        )
        assertFalse(result.eligibleForPromotion)
    }
}
