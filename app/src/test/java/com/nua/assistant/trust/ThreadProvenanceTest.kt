package com.nua.assistant.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadProvenanceTest {

    @Test
    fun `zero prior sends produces no note`() {
        assertNull(threadProvenanceNote(0))
    }

    @Test
    fun `a negative count is treated the same as zero`() {
        assertNull(threadProvenanceNote(-1))
    }

    @Test
    fun `one prior send is reported as message #2`() {
        assertEquals("This is message #2 to them today.", threadProvenanceNote(1))
    }

    @Test
    fun `three prior sends are reported as message #4`() {
        assertEquals("This is message #4 to them today.", threadProvenanceNote(3))
    }

    @Test
    fun `sameDayWindowStart is exactly 24 hours before now`() {
        val now = 1_000_000_000L
        assertEquals(now - 24L * 60 * 60 * 1000, sameDayWindowStart(now))
    }
}
