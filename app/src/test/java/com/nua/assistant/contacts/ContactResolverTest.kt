package com.nua.assistant.contacts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactResolverTest {

    @Test
    fun `plain digit strings look like phone numbers`() {
        assertTrue(looksLikePhoneNumber("5551234567"))
        assertTrue(looksLikePhoneNumber("+1 555 123 4567"))
        assertTrue(looksLikePhoneNumber("(555) 123-4567"))
    }

    @Test
    fun `names do not look like phone numbers`() {
        assertFalse(looksLikePhoneNumber("Sam"))
        assertFalse(looksLikePhoneNumber("my sister"))
    }

    @Test
    fun `too-short digit strings do not look like phone numbers`() {
        assertFalse(looksLikePhoneNumber("123"))
    }

    @Test
    fun `well-formed addresses look like emails`() {
        assertTrue(looksLikeEmail("priya@example.com"))
        assertTrue(looksLikeEmail(" sam.smith@work.co "))
    }

    @Test
    fun `names do not look like emails`() {
        assertFalse(looksLikeEmail("Sam"))
        assertFalse(looksLikeEmail("priya at example dot com"))
    }
}
