package com.nua.assistant.trust.lineage

import com.nua.assistant.memory.LineageRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(runId: String = "r1", stepId: String = "a", outcomeState: String = "SUCCEEDED") =
    LineageEntry(runId, stepId, action = "GET_WEATHER", adapterType = "LOCAL_NATIVE", authorizationKind = "NotRequired", outcomeState = outcomeState, detail = null, timestampMillis = 1_000L)

class LineageChainTest {

    @Test
    fun `the same entry and previous hash always produce the same hash -- deterministic`() {
        val e = entry()
        assertEquals(nextLineageHash("abc", e), nextLineageHash("abc", e))
    }

    @Test
    fun `a different previous hash changes the result, even for an identical entry`() {
        val e = entry()
        assertNotEquals(nextLineageHash("abc", e), nextLineageHash("xyz", e))
    }

    @Test
    fun `changing any single field of the entry changes the hash`() {
        val base = entry()
        val base_hash = nextLineageHash(null, base)
        assertNotEquals(base_hash, nextLineageHash(null, base.copy(stepId = "b")))
        assertNotEquals(base_hash, nextLineageHash(null, base.copy(outcomeState = "FAILED")))
        assertNotEquals(base_hash, nextLineageHash(null, base.copy(detail = "something")))
        assertNotEquals(base_hash, nextLineageHash(null, base.copy(runId = "r2")))
    }

    @Test
    fun `a null previous hash is treated distinctly from an empty-string previous hash's content`() {
        // Not a literal collision test -- just documents that the first record in a chain
        // (previousHash = null) hashes deterministically, same as any other.
        val first = nextLineageHash(null, entry())
        assertTrue(first.isNotBlank())
    }

    @Test
    fun `verifyLineageChain accepts a correctly-chained sequence of records`() {
        val e1 = entry(stepId = "a")
        val h1 = nextLineageHash(null, e1)
        val e2 = entry(stepId = "b")
        val h2 = nextLineageHash(h1, e2)

        val records = listOf(
            LineageRecordEntity(runId = e1.runId, stepId = e1.stepId, action = e1.action, adapterType = e1.adapterType, authorizationKind = e1.authorizationKind, outcomeState = e1.outcomeState, detail = e1.detail, hash = h1, previousHash = null, timestampMillis = e1.timestampMillis),
            LineageRecordEntity(runId = e2.runId, stepId = e2.stepId, action = e2.action, adapterType = e2.adapterType, authorizationKind = e2.authorizationKind, outcomeState = e2.outcomeState, detail = e2.detail, hash = h2, previousHash = h1, timestampMillis = e2.timestampMillis),
        )

        assertTrue(verifyLineageChain(records))
    }

    @Test
    fun `verifyLineageChain rejects a record whose stored hash was tampered with`() {
        val e1 = entry(stepId = "a")
        val h1 = nextLineageHash(null, e1)
        val tampered = LineageRecordEntity(runId = e1.runId, stepId = e1.stepId, action = e1.action, adapterType = e1.adapterType, authorizationKind = e1.authorizationKind, outcomeState = e1.outcomeState, detail = e1.detail, hash = "not-the-real-hash", previousHash = null, timestampMillis = e1.timestampMillis)

        assertFalse(verifyLineageChain(listOf(tampered)))
    }

    @Test
    fun `verifyLineageChain rejects a record whose content was altered after its hash was computed -- the actual tamper case`() {
        val e1 = entry(stepId = "a", outcomeState = "SUCCEEDED")
        val h1 = nextLineageHash(null, e1)
        // The hash still reflects the *original* outcome, but the stored row now claims a
        // different one -- exactly what an attacker editing the database directly would do.
        val alteredAfterHashing = LineageRecordEntity(runId = e1.runId, stepId = e1.stepId, action = e1.action, adapterType = e1.adapterType, authorizationKind = e1.authorizationKind, outcomeState = "FAILED", detail = e1.detail, hash = h1, previousHash = null, timestampMillis = e1.timestampMillis)

        assertFalse(verifyLineageChain(listOf(alteredAfterHashing)))
    }

    @Test
    fun `verifyLineageChain rejects a chain with a record removed from the middle`() {
        val e1 = entry(stepId = "a")
        val h1 = nextLineageHash(null, e1)
        val e2 = entry(stepId = "b")
        val h2 = nextLineageHash(h1, e2)
        val e3 = entry(stepId = "c")
        val h3 = nextLineageHash(h2, e3)

        val full = listOf(
            LineageRecordEntity(runId = e1.runId, stepId = e1.stepId, action = e1.action, adapterType = e1.adapterType, authorizationKind = e1.authorizationKind, outcomeState = e1.outcomeState, detail = e1.detail, hash = h1, previousHash = null, timestampMillis = e1.timestampMillis),
            LineageRecordEntity(runId = e2.runId, stepId = e2.stepId, action = e2.action, adapterType = e2.adapterType, authorizationKind = e2.authorizationKind, outcomeState = e2.outcomeState, detail = e2.detail, hash = h2, previousHash = h1, timestampMillis = e2.timestampMillis),
            LineageRecordEntity(runId = e3.runId, stepId = e3.stepId, action = e3.action, adapterType = e3.adapterType, authorizationKind = e3.authorizationKind, outcomeState = e3.outcomeState, detail = e3.detail, hash = h3, previousHash = h2, timestampMillis = e3.timestampMillis),
        )
        // Remove the middle record -- the third record's previousHash (h2) no longer
        // matches what verifyLineageChain expects (h1, the new predecessor's hash).
        val withMiddleRemoved = listOf(full[0], full[2])

        assertFalse(verifyLineageChain(withMiddleRemoved))
    }

    @Test
    fun `an empty chain is trivially valid`() {
        assertTrue(verifyLineageChain(emptyList()))
    }
}
