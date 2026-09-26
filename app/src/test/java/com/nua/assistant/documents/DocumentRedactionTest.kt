package com.nua.assistant.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentRedactionTest {

    @Test
    fun `an SSN is redacted`() {
        assertEquals(
            "Their SSN is [REDACTED-SSN], on file.",
            redactSensitivePatterns("Their SSN is 123-45-6789, on file."),
        )
    }

    @Test
    fun `a valid Luhn card number is redacted`() {
        // A well-known test card number (Visa test PAN), passes Luhn.
        val result = redactSensitivePatterns("Card on file: 4111 1111 1111 1111.")
        assertEquals("Card on file: [REDACTED-CARD].", result)
    }

    @Test
    fun `a dash-separated valid card number is redacted`() {
        val result = redactSensitivePatterns("Pay to 4111-1111-1111-1111 please.")
        assertEquals("Pay to [REDACTED-CARD] please.", result)
    }

    @Test
    fun `a 16-digit run that fails Luhn is left alone — not every long number is a card`() {
        // Order/tracking numbers are often long digit runs that aren't card numbers.
        val trackingNumber = "1234567890123456"
        assertFalse(passesLuhnCheck(trackingNumber))
        val result = redactSensitivePatterns("Tracking: $trackingNumber")
        assertEquals("Tracking: $trackingNumber", result)
    }

    @Test
    fun `ordinary text with no PII patterns is unchanged`() {
        val text = "Rent is due on the 1st. Call the landlord at extension 204."
        assertEquals(text, redactSensitivePatterns(text))
    }

    @Test
    fun `multiple redactable patterns in one document are all redacted`() {
        val text = "SSN 123-45-6789, card 4111 1111 1111 1111, SSN 987-65-4320."
        val result = redactSensitivePatterns(text)
        assertTrue(result.contains("[REDACTED-SSN]"))
        assertTrue(result.contains("[REDACTED-CARD]"))
        assertFalse(result.contains("123-45-6789"))
        assertFalse(result.contains("4111"))
    }

    @Test
    fun `passesLuhnCheck rejects an empty string`() {
        assertFalse(passesLuhnCheck(""))
    }
}
