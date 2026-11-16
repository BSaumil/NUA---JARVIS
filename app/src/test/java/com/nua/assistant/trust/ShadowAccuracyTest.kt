package com.nua.assistant.trust

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.memory.ShadowPredictionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun prediction(predictedPermit: Boolean, actualOutcome: String?) = ShadowPredictionEntity(
    contractId = 1,
    actionType = NuaActionType.SMS_SEND.name,
    predictedPermit = predictedPermit,
    reason = "test",
    actualOutcome = actualOutcome,
)

class ShadowAccuracyTest {

    @Test
    fun `an empty prediction list reports zero resolved and a null accuracy rate`() {
        val accuracy = shadowAccuracyFor(emptyList())
        assertEquals(0, accuracy.totalResolved)
        assertNull(accuracy.accuracyRate)
    }

    @Test
    fun `unresolved predictions are excluded entirely, never counted as either outcome`() {
        val accuracy = shadowAccuracyFor(listOf(prediction(predictedPermit = true, actualOutcome = null)))
        assertEquals(0, accuracy.totalResolved)
        assertNull(accuracy.accuracyRate)
    }

    @Test
    fun `predicted permit and approved counts as a true positive`() {
        val accuracy = shadowAccuracyFor(listOf(prediction(predictedPermit = true, actualOutcome = "APPROVED")))
        assertEquals(1, accuracy.truePositives)
        assertEquals(1.0, accuracy.accuracyRate)
    }

    @Test
    fun `predicted permit but declined counts as the dangerous false positive`() {
        val accuracy = shadowAccuracyFor(listOf(prediction(predictedPermit = true, actualOutcome = "REJECTED")))
        assertEquals(1, accuracy.falsePositives)
        assertEquals(0.0, accuracy.accuracyRate)
    }

    @Test
    fun `predicted deny but the user approved counts as the safe-direction false negative`() {
        val accuracy = shadowAccuracyFor(listOf(prediction(predictedPermit = false, actualOutcome = "APPROVED")))
        assertEquals(1, accuracy.falseNegatives)
        assertEquals(0.0, accuracy.accuracyRate)
    }

    @Test
    fun `predicted deny and declined counts as a true negative`() {
        val accuracy = shadowAccuracyFor(listOf(prediction(predictedPermit = false, actualOutcome = "REJECTED")))
        assertEquals(1, accuracy.trueNegatives)
        assertEquals(1.0, accuracy.accuracyRate)
    }

    @Test
    fun `a mix aggregates correctly and the accuracy rate only counts the two correct outcomes`() {
        val accuracy = shadowAccuracyFor(
            listOf(
                prediction(predictedPermit = true, actualOutcome = "APPROVED"),
                prediction(predictedPermit = true, actualOutcome = "REJECTED"),
                prediction(predictedPermit = false, actualOutcome = "APPROVED"),
                prediction(predictedPermit = false, actualOutcome = "REJECTED"),
                prediction(predictedPermit = true, actualOutcome = null),
            ),
        )
        assertEquals(4, accuracy.totalResolved)
        assertEquals(1, accuracy.truePositives)
        assertEquals(1, accuracy.falsePositives)
        assertEquals(1, accuracy.falseNegatives)
        assertEquals(1, accuracy.trueNegatives)
        assertEquals(0.5, accuracy.accuracyRate)
    }
}
