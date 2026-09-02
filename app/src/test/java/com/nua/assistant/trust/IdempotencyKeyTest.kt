package com.nua.assistant.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdempotencyKeyTest {

    @Test
    fun `same action type and components produce the same key`() {
        val first = idempotencyKeyFor("SMS_SEND", "+15551234567", "on my way")
        val second = idempotencyKeyFor("SMS_SEND", "+15551234567", "on my way")
        assertEquals(first, second)
    }

    @Test
    fun `a different component changes the key`() {
        val original = idempotencyKeyFor("SMS_SEND", "+15551234567", "on my way")
        val differentMessage = idempotencyKeyFor("SMS_SEND", "+15551234567", "running late")
        val differentRecipient = idempotencyKeyFor("SMS_SEND", "+15559999999", "on my way")
        assertNotEquals(original, differentMessage)
        assertNotEquals(original, differentRecipient)
    }

    @Test
    fun `a different action type changes the key even with identical components`() {
        val sms = idempotencyKeyFor("SMS_SEND", "same", "same")
        val reply = idempotencyKeyFor("REPLY_TO_NOTIFICATION", "same", "same")
        assertNotEquals(sms, reply)
    }

    @Test
    fun `no components still produces a stable key from the action type alone`() {
        assertEquals(idempotencyKeyFor("CALENDAR_INVITE"), idempotencyKeyFor("CALENDAR_INVITE"))
    }
}
