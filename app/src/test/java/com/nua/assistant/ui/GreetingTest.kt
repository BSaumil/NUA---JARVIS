package com.nua.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingTest {

    @Test
    fun `every hour of the day produces a greeting`() {
        (0..23).forEach { hour ->
            assertTrue("hour $hour produced a blank greeting", greetingForHour(hour).isNotBlank())
        }
    }

    @Test
    fun `greetings match the time of day`() {
        assertEquals("Still up?", greetingForHour(2))
        assertEquals("Good morning", greetingForHour(9))
        assertEquals("Good afternoon", greetingForHour(14))
        assertEquals("Good evening", greetingForHour(20))
    }

    @Test
    fun `boundaries land on the expected side`() {
        assertEquals("Still up?", greetingForHour(4))
        assertEquals("Good morning", greetingForHour(5))
        assertEquals("Good morning", greetingForHour(11))
        assertEquals("Good afternoon", greetingForHour(12))
        assertEquals("Good afternoon", greetingForHour(17))
        assertEquals("Good evening", greetingForHour(18))
        assertEquals("Good evening", greetingForHour(23))
        assertEquals("Still up?", greetingForHour(0))
    }
}
