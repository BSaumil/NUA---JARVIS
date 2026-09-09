package com.nua.assistant.world

import com.nua.assistant.memory.WorldRelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldModelRepositoryTest {

    private fun relationship(fromType: String, fromId: Long, toType: String, toId: Long) = WorldRelationshipEntity(
        fromType = fromType,
        fromId = fromId,
        relation = "synthesized_from",
        toType = toType,
        toId = toId,
        confidence = 1f,
    )

    // -------------------------------------------------------------------------------
    // otherSideOf — read-side resolution's direction-agnostic lookup: a caller asking
    // "what's (type, id) connected to" shouldn't have to know or care whether it was
    // originally stored as the from-side or the to-side of the row.
    // -------------------------------------------------------------------------------

    @Test
    fun `queried from the 'from' side, the other side is 'to'`() {
        val relationship = relationship("DREAM", 1, "FACT", 12)
        assertEquals("FACT" to 12L, otherSideOf(relationship, type = "DREAM", id = 1))
    }

    @Test
    fun `queried from the 'to' side, the other side is 'from'`() {
        val relationship = relationship("DREAM", 1, "FACT", 12)
        assertEquals("DREAM" to 1L, otherSideOf(relationship, type = "FACT", id = 12))
    }

    @Test
    fun `a type match alone, with a different id, does not short-circuit to the from-side`() {
        // Same fromType as the query ("FACT") but a different fromId — the from-side
        // check must compare both fields, not type alone.
        val relationship = relationship("FACT", 99, "GOAL", 3)
        assertEquals("FACT" to 99L, otherSideOf(relationship, type = "FACT", id = 3))
    }

    // -------------------------------------------------------------------------------
    // confidenceAfterResolutionCheck — the World Model RFC's one real design decision
    // (§8): an orphaned relationship's row is kept, not deleted, with its confidence
    // dropped to zero instead — same reasoning as the Trust Ledger keeping a record of
    // past mistakes rather than erasing them.
    // -------------------------------------------------------------------------------

    @Test
    fun `a resolved endpoint keeps its stored confidence`() {
        assertEquals(0.8f, confidenceAfterResolutionCheck(currentConfidence = 0.8f, resolved = true))
    }

    @Test
    fun `an unresolved endpoint drops to zero confidence, regardless of what it was`() {
        assertEquals(0f, confidenceAfterResolutionCheck(currentConfidence = 0.9f, resolved = false))
        assertEquals(0f, confidenceAfterResolutionCheck(currentConfidence = 0.1f, resolved = false))
    }

    // -------------------------------------------------------------------------------
    // isActiveRelationship — the flip side: what "orphaned" means for retrieval.
    // -------------------------------------------------------------------------------

    @Test
    fun `zero confidence is not active`() {
        assertFalse(isActiveRelationship(0f))
    }

    @Test
    fun `any positive confidence is active`() {
        assertTrue(isActiveRelationship(0.01f))
        assertTrue(isActiveRelationship(1f))
    }
}
