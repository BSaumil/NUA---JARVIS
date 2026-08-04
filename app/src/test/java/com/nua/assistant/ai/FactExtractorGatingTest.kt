package com.nua.assistant.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactExtractorGatingTest {

    @Test
    fun `message with a fact hint is considered regardless of turn index`() {
        assertTrue(FactExtractor.shouldConsider("My name is Alex", userTurnIndex = 1))
        assertTrue(FactExtractor.shouldConsider("I always run on Saturday mornings", userTurnIndex = 7))
        assertTrue(FactExtractor.shouldConsider("I'm allergic to peanuts", userTurnIndex = 3))
    }

    @Test
    fun `message with no hint is skipped off the periodic sweep`() {
        assertFalse(FactExtractor.shouldConsider("what time is it", userTurnIndex = 1))
        assertFalse(FactExtractor.shouldConsider("what time is it", userTurnIndex = 5))
    }

    @Test
    fun `message with no hint is still swept on the periodic interval`() {
        assertTrue(FactExtractor.shouldConsider("what time is it", userTurnIndex = 4))
        assertTrue(FactExtractor.shouldConsider("what time is it", userTurnIndex = 8))
    }

    @Test
    fun `hint matching is case insensitive`() {
        assertTrue(FactExtractor.shouldConsider("CALL ME Sam from now on", userTurnIndex = 1))
    }
}
