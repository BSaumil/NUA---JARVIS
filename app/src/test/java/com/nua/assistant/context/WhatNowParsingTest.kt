package com.nua.assistant.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatNowParsingTest {

    @Test
    fun `a recommendation with all fields parses to Recommendation`() {
        val json = """
            {"hasRecommendation": true, "action": "Leave for the airport", "reason": "traffic is heavy and your flight boards in 90 minutes", "confidence": 0.85, "estimatedMinutes": 45}
        """.trimIndent()
        val result = parseWhatNowResponse(json) as WhatNowResult.Recommendation
        assertEquals("Leave for the airport", result.action)
        assertEquals("traffic is heavy and your flight boards in 90 minutes", result.reason)
        assertEquals(0.85f, result.confidence, 0.001f)
        assertEquals(45, result.estimatedMinutes)
    }

    @Test
    fun `hasRecommendation false becomes the honest anti-case, not a fabricated action`() {
        val json = """{"hasRecommendation": false}"""
        assertEquals(WhatNowResult.NothingNeedsAttention, parseWhatNowResponse(json))
    }

    @Test
    fun `hasRecommendation true but a blank action still counts as nothing to recommend`() {
        val json = """{"hasRecommendation": true, "action": "", "reason": "", "confidence": 0.5}"""
        assertEquals(WhatNowResult.NothingNeedsAttention, parseWhatNowResponse(json))
    }

    @Test
    fun `malformed JSON becomes Unavailable, never silently treated as nothing needed`() {
        val result = parseWhatNowResponse("not json at all")
        assertTrue(result is WhatNowResult.Unavailable)
    }

    @Test
    fun `confidence is clamped into 0 to 1`() {
        val tooHigh = parseWhatNowResponse(
            """{"hasRecommendation": true, "action": "x", "reason": "y", "confidence": 1.4}""",
        ) as WhatNowResult.Recommendation
        assertEquals(1f, tooHigh.confidence, 0.001f)

        val negative = parseWhatNowResponse(
            """{"hasRecommendation": true, "action": "x", "reason": "y", "confidence": -0.2}""",
        ) as WhatNowResult.Recommendation
        assertEquals(0f, negative.confidence, 0.001f)
    }

    @Test
    fun `a markdown-fenced reply still parses, same as extractJsonPayload handles elsewhere`() {
        val fenced = "```json\n{\"hasRecommendation\": true, \"action\": \"Call Sam\", \"reason\": \"he asked yesterday\", \"confidence\": 0.6}\n```"
        val result = parseWhatNowResponse(fenced) as WhatNowResult.Recommendation
        assertEquals("Call Sam", result.action)
    }

    @Test
    fun `chatSummary renders each state distinctly`() {
        val recommendation = WhatNowResult.Recommendation("Call Sam", "he asked yesterday", 0.6f)
        assertEquals("Call Sam — he asked yesterday", recommendation.chatSummary())
        assertEquals("Nothing unusual — you're clear.", WhatNowResult.NothingNeedsAttention.chatSummary())
        assertTrue(WhatNowResult.Unavailable("network error").chatSummary().contains("network error"))
    }
}
