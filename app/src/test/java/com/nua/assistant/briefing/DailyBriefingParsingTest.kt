package com.nua.assistant.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBriefingParsingTest {

    @Test
    fun `a full briefing parses every section`() {
        val json = """
            {"nothingUnusual": false, "today": "Busy day ahead.", "attention": ["Dentist at 3pm"], "contextChanges": ["Rain moved in overnight"], "risks": ["Traffic near the dentist"], "opportunities": ["Free hour before lunch"], "recommendation": "Leave 20 minutes early"}
        """.trimIndent()
        val result = parseDailyBriefingResponse(json) as DailyBriefing.Summary
        assertEquals("Busy day ahead.", result.today)
        assertEquals(listOf("Dentist at 3pm"), result.attention)
        assertEquals(listOf("Rain moved in overnight"), result.contextChanges)
        assertEquals(listOf("Traffic near the dentist"), result.risks)
        assertEquals(listOf("Free hour before lunch"), result.opportunities)
        assertEquals("Leave 20 minutes early", result.recommendation)
    }

    @Test
    fun `nothingUnusual true becomes the honest anti-case regardless of other fields`() {
        val json = """{"nothingUnusual": true, "today": "should be ignored"}"""
        assertEquals(DailyBriefing.NothingUnusual, parseDailyBriefingResponse(json))
    }

    @Test
    fun `empty sections stay empty, never backfilled`() {
        val json = """{"nothingUnusual": false, "today": "Quiet day."}"""
        val result = parseDailyBriefingResponse(json) as DailyBriefing.Summary
        assertTrue(result.attention.isEmpty())
        assertTrue(result.contextChanges.isEmpty())
        assertTrue(result.risks.isEmpty())
        assertTrue(result.opportunities.isEmpty())
        assertNull(result.recommendation)
    }

    @Test
    fun `a blank today with nothingUnusual false is too thin to trust`() {
        val json = """{"nothingUnusual": false, "today": ""}"""
        assertNull(parseDailyBriefingResponse(json))
    }

    @Test
    fun `malformed JSON returns null so the caller can fall back`() {
        assertNull(parseDailyBriefingResponse("not json at all"))
    }

    @Test
    fun `a blank recommendation is treated as no recommendation`() {
        val json = """{"nothingUnusual": false, "today": "Fine.", "recommendation": "   "}"""
        val result = parseDailyBriefingResponse(json) as DailyBriefing.Summary
        assertNull(result.recommendation)
    }

    @Test
    fun `renderText shows only the sections that have content`() {
        val minimal = DailyBriefing.Summary(today = "Quiet day.")
        assertEquals("Quiet day.", minimal.renderText())

        val full = DailyBriefing.Summary(
            today = "Busy day.",
            attention = listOf("Dentist at 3pm"),
            recommendation = "Leave early",
        )
        val rendered = full.renderText()
        assertTrue(rendered.contains("Busy day."))
        assertTrue(rendered.contains("Attention: Dentist at 3pm"))
        assertTrue(rendered.contains("Recommendation: Leave early"))
        assertTrue("empty sections must not appear at all", !rendered.contains("Risks:"))
    }

    @Test
    fun `NothingUnusual renders as one honest line`() {
        assertEquals("Nothing unusual today — you're all clear.", DailyBriefing.NothingUnusual.renderText())
    }
}
