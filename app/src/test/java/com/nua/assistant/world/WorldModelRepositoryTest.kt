package com.nua.assistant.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldModelRepositoryTest {

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
