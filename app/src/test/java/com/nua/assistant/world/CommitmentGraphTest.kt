package com.nua.assistant.world

import org.junit.Assert.assertEquals
import org.junit.Test

private const val NOW = 1_000_000L

class CommitmentGraphTest {

    // -------------------------------------------------------------------------------
    // commitmentStateFor
    // -------------------------------------------------------------------------------

    @Test
    fun `no due date and not fulfilled is pending forever`() {
        assertEquals(CommitmentState.PENDING, commitmentStateFor(dueAt = null, fulfilledAt = null, now = NOW))
        assertEquals(CommitmentState.PENDING, commitmentStateFor(dueAt = null, fulfilledAt = null, now = NOW + 1_000_000_000L))
    }

    @Test
    fun `a future due date, not yet fulfilled, is pending`() {
        assertEquals(CommitmentState.PENDING, commitmentStateFor(dueAt = NOW + 1, fulfilledAt = null, now = NOW))
    }

    @Test
    fun `a due date exactly at now, not fulfilled, is already expired -- the boundary is inclusive`() {
        assertEquals(CommitmentState.EXPIRED, commitmentStateFor(dueAt = NOW, fulfilledAt = null, now = NOW))
    }

    @Test
    fun `a past due date, not fulfilled, is expired`() {
        assertEquals(CommitmentState.EXPIRED, commitmentStateFor(dueAt = NOW - 1, fulfilledAt = null, now = NOW))
    }

    @Test
    fun `fulfilled before the due date is fulfilled, not pending`() {
        assertEquals(CommitmentState.FULFILLED, commitmentStateFor(dueAt = NOW + 1000, fulfilledAt = NOW, now = NOW))
    }

    @Test
    fun `fulfilled after the due date already passed is still fulfilled, never expired -- late is not broken`() {
        assertEquals(CommitmentState.FULFILLED, commitmentStateFor(dueAt = NOW - 1000, fulfilledAt = NOW, now = NOW))
    }

    @Test
    fun `fulfilled with no due date at all is fulfilled`() {
        assertEquals(CommitmentState.FULFILLED, commitmentStateFor(dueAt = null, fulfilledAt = NOW, now = NOW))
    }

    // -------------------------------------------------------------------------------
    // isRelationshipValidAt
    // -------------------------------------------------------------------------------

    @Test
    fun `both bounds null means valid at any instant -- every relationship written before these columns existed stays valid`() {
        assertEquals(true, isRelationshipValidAt(validFrom = null, validUntil = null, at = NOW))
        assertEquals(true, isRelationshipValidAt(validFrom = null, validUntil = null, at = 0L))
        assertEquals(true, isRelationshipValidAt(validFrom = null, validUntil = null, at = Long.MAX_VALUE))
    }

    @Test
    fun `before validFrom is not yet valid`() {
        assertEquals(false, isRelationshipValidAt(validFrom = NOW, validUntil = null, at = NOW - 1))
    }

    @Test
    fun `exactly at validFrom is valid -- the start bound is inclusive`() {
        assertEquals(true, isRelationshipValidAt(validFrom = NOW, validUntil = null, at = NOW))
    }

    @Test
    fun `exactly at validUntil is no longer valid -- the end bound is exclusive`() {
        assertEquals(false, isRelationshipValidAt(validFrom = null, validUntil = NOW, at = NOW))
    }

    @Test
    fun `just before validUntil is still valid`() {
        assertEquals(true, isRelationshipValidAt(validFrom = null, validUntil = NOW, at = NOW - 1))
    }

    @Test
    fun `within both bounds is valid`() {
        assertEquals(true, isRelationshipValidAt(validFrom = NOW - 10, validUntil = NOW + 10, at = NOW))
    }

    @Test
    fun `a superseded relationship's old validUntil and the new relationship's validFrom can be the same instant without overlap`() {
        // The instant a relationship is superseded is the first instant the new one is
        // valid, never a moment both are simultaneously true.
        val supersessionInstant = NOW
        val oldValidUntil = supersessionInstant
        val newValidFrom = supersessionInstant
        assertEquals(false, isRelationshipValidAt(validFrom = null, validUntil = oldValidUntil, at = supersessionInstant))
        assertEquals(true, isRelationshipValidAt(validFrom = newValidFrom, validUntil = null, at = supersessionInstant))
    }
}
