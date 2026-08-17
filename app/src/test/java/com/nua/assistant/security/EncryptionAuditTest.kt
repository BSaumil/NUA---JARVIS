package com.nua.assistant.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptionAuditTest {

    @Test
    fun `the two EncryptedSharedPreferences stores are reported as encrypted`() {
        val entries = encryptionAuditEntries()
        assertTrue(entries.first { it.label.contains("API key") }.encrypted)
        assertTrue(entries.first { it.label.contains("voice profile") }.encrypted)
    }

    @Test
    fun `the plain Room database is honestly reported as not encrypted`() {
        val entries = encryptionAuditEntries()
        assertFalse(entries.first { it.label.contains("Conversation history") }.encrypted)
    }
}
