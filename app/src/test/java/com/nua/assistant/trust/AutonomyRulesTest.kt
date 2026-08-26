package com.nua.assistant.trust

import com.nua.assistant.ai.FamiliarityTier
import com.nua.assistant.memory.AutonomyPreferenceEntity
import com.nua.assistant.memory.TrustLedgerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun preference(approvedCount: Int, autoApproveEnabled: Boolean) =
    AutonomyPreferenceEntity(actionType = "X", approvedCount = approvedCount, autoApproveEnabled = autoApproveEnabled)

private fun event(type: TrustEventType) = TrustLedgerEntity(type = type, description = "d")

private const val DAY_MS = 24L * 60 * 60 * 1000

class AutonomyRulesTest {

    // -----------------------------------------------------------------------------------
    // isAutonomySuggested — the sole gate on a Settings suggestion that leads into
    // setAutoApprove, so it's worth pinning precisely.
    // -----------------------------------------------------------------------------------

    @Test
    fun `below the threshold is never suggested`() {
        assertFalse(isAutonomySuggested(preference(approvedCount = 4, autoApproveEnabled = false), threshold = 5))
    }

    @Test
    fun `exactly at the threshold is suggested`() {
        assertTrue(isAutonomySuggested(preference(approvedCount = 5, autoApproveEnabled = false), threshold = 5))
    }

    @Test
    fun `already auto-approved is never suggested again`() {
        assertFalse(isAutonomySuggested(preference(approvedCount = 50, autoApproveEnabled = true), threshold = 5))
    }

    // -----------------------------------------------------------------------------------
    // selfReportEligible
    // -----------------------------------------------------------------------------------

    @Test
    fun `only ESTABLISHED familiarity self-reports`() {
        val eligible = FamiliarityTier.entries.filter { selfReportEligible(it, lastReportAt = 0, now = DAY_MS * 30, minIntervalMillis = DAY_MS) }
        assertEquals(listOf(FamiliarityTier.ESTABLISHED), eligible)
    }

    @Test
    fun `too soon since the last report is not eligible`() {
        val now = DAY_MS * 10
        assertFalse(selfReportEligible(FamiliarityTier.ESTABLISHED, lastReportAt = now - DAY_MS, now = now, minIntervalMillis = DAY_MS * 14))
    }

    @Test
    fun `exactly at the interval boundary is eligible`() {
        val now = DAY_MS * 14
        assertTrue(selfReportEligible(FamiliarityTier.ESTABLISHED, lastReportAt = 0, now = now, minIntervalMillis = DAY_MS * 14))
    }

    // -----------------------------------------------------------------------------------
    // selfReportMessage
    // -----------------------------------------------------------------------------------

    @Test
    fun `no events since the last report means silence, not an empty report`() {
        assertNull(selfReportMessage(emptyList()))
    }

    @Test
    fun `one event is phrased in the singular`() {
        val message = selfReportMessage(listOf(event(TrustEventType.MISREAD_INTENT)))
        assertTrue(message!!.contains("1 thing wrong"))
        assertFalse(message.contains("1 things"))
    }

    @Test
    fun `multiple events are phrased in the plural and grouped by type`() {
        val message = selfReportMessage(listOf(event(TrustEventType.MISREAD_INTENT), event(TrustEventType.MISREAD_INTENT), event(TrustEventType.STALE_FACT)))
        assertTrue(message!!.contains("3 things wrong"))
        assertTrue(message.contains("2 times misread what you meant"))
        assertTrue(message.contains("1 time remembered something no longer true"))
    }
}
