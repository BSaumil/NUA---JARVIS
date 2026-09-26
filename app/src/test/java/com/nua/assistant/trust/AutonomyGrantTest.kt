package com.nua.assistant.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyGrantTest {

    private val now = 1_000_000L

    @Test
    fun `an enabled grant with a future expiry is active`() {
        assertTrue(isGrantActive(autoApproveEnabled = true, expiresAt = now + 1, now = now))
    }

    @Test
    fun `an enabled grant past its own expiry is not active`() {
        assertFalse(isGrantActive(autoApproveEnabled = true, expiresAt = now - 1, now = now))
    }

    @Test
    fun `a grant exactly at its expiry instant is not active`() {
        assertFalse(isGrantActive(autoApproveEnabled = true, expiresAt = now, now = now))
    }

    @Test
    fun `a disabled grant is never active, regardless of expiresAt`() {
        assertFalse(isGrantActive(autoApproveEnabled = false, expiresAt = now + 1, now = now))
        assertFalse(isGrantActive(autoApproveEnabled = false, expiresAt = null, now = now))
    }

    @Test
    fun `an enabled grant with no expiry recorded fails closed, not open`() {
        // Shouldn't happen going forward (setAutoApprove always sets one), but a
        // pre-migration row with autoApproveEnabled=true and expiresAt=null must not be
        // read as perpetually valid.
        assertFalse(isGrantActive(autoApproveEnabled = true, expiresAt = null, now = now))
    }

    @Test
    fun `daysUntilExpiry rounds down and never goes negative`() {
        val oneDayMillis = 24L * 60 * 60 * 1000
        assertEquals(1L, daysUntilExpiry(now + oneDayMillis, now))
        assertEquals(0L, daysUntilExpiry(now + oneDayMillis - 1, now))
        assertEquals(0L, daysUntilExpiry(now - oneDayMillis, now))
    }
}
