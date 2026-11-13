package com.nua.assistant.evolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EvolutionMemoryTest {
    @Test
    fun `failure fingerprint is stable and content free`() {
        val a = failureFingerprint(
            FailureSource.UAF,
            "SMS_SEND",
            FailureCategory.ACTION_EXECUTION,
            "ADAPTER_FAILURE",
        )
        val b = failureFingerprint(
            FailureSource.UAF,
            "SMS_SEND",
            FailureCategory.ACTION_EXECUTION,
            "ADAPTER_FAILURE",
        )
        val c = failureFingerprint(
            FailureSource.UAF,
            "SMS_SEND",
            FailureCategory.ACTION_EXECUTION,
            "NO_ADAPTER",
        )
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(64, a.length)
    }

    @Test
    fun `repeated observation increments recurrence without storing raw payload`() {
        val observation = FailureObservation(
            id = "1",
            source = FailureSource.MODEL_MESH,
            component = "SUMMARIZATION:PRIVATE_OS_MODEL",
            category = FailureCategory.INVALID_OUTPUT,
            code = "CONTRACT_VALIDATION_FAILED",
            signature = "sig",
            severity = FailureSeverity.MEDIUM,
            occurredAt = 10,
        )
        val first = mergeEvolutionMemoryEntry(null, observation)
        val second = mergeEvolutionMemoryEntry(
            first,
            observation.copy(id = "2", occurredAt = 20, severity = FailureSeverity.HIGH),
        )

        assertEquals(2, second.occurrences)
        assertEquals(10, second.firstSeenAt)
        assertEquals(20, second.lastSeenAt)
        assertEquals(FailureSeverity.HIGH, second.severity)
        assertEquals("CONTRACT_VALIDATION_FAILED", second.code)
    }
}
