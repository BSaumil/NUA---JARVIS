package com.nua.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonExtractionTest {

    @Test
    fun `plain JSON object passes through unchanged`() {
        assertEquals("""{"a":1}""", extractJsonPayload("""{"a":1}"""))
    }

    @Test
    fun `JSON wrapped in a markdown code fence is unwrapped`() {
        val raw = "```json\n{\"a\":1}\n```"
        assertEquals("""{"a":1}""", extractJsonPayload(raw))
    }

    @Test
    fun `JSON wrapped in an unlabeled code fence is unwrapped`() {
        val raw = "```\n{\"a\":1}\n```"
        assertEquals("""{"a":1}""", extractJsonPayload(raw))
    }

    @Test
    fun `leading and trailing prose around JSON is stripped`() {
        val raw = "Sure, here you go: {\"a\":1} — let me know if you need anything else."
        assertEquals("""{"a":1}""", extractJsonPayload(raw))
    }

    @Test
    fun `JSON array is extracted the same way as an object`() {
        val raw = "```json\n[1,2,3]\n```"
        assertEquals("[1,2,3]", extractJsonPayload(raw))
    }
}
