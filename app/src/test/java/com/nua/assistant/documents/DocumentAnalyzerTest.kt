package com.nua.assistant.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class DocumentAnalyzerTest {

    @Test
    fun `valid ISO date parses to the matching timestamp`() {
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-12-31")!!.time
        assertEquals(expected, parseIsoDate("2026-12-31"))
    }

    @Test
    fun `null string yields no date`() {
        assertNull(parseIsoDate(null))
    }

    @Test
    fun `the literal word null yields no date`() {
        assertNull(parseIsoDate("null"))
    }

    @Test
    fun `blank string yields no date`() {
        assertNull(parseIsoDate("  "))
    }

    @Test
    fun `malformed date yields no date rather than throwing`() {
        assertNull(parseIsoDate("not a date"))
    }
}
