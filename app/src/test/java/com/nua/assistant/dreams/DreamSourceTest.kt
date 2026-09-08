package com.nua.assistant.dreams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamSourceTest {

    @Test
    fun `two distinct sources are sufficient provenance`() {
        val sources = listOf(DreamSource(DreamSourceType.FACT, 1), DreamSource(DreamSourceType.GOAL, 2))
        assertTrue(hasSufficientProvenance(sources))
    }

    @Test
    fun `zero or one source is not sufficient`() {
        assertFalse(hasSufficientProvenance(emptyList()))
        assertFalse(hasSufficientProvenance(listOf(DreamSource(DreamSourceType.FACT, 1))))
    }

    @Test
    fun `the same source repeated does not count as two`() {
        val sources = listOf(DreamSource(DreamSourceType.FACT, 1), DreamSource(DreamSourceType.FACT, 1))
        assertFalse(hasSufficientProvenance(sources))
    }

    @Test
    fun `validatedDreamSources drops ids that were never actually offered`() {
        val sources = validatedDreamSources(
            connectedFactIds = listOf(1, 99),
            connectedGoalIds = listOf(5),
            connectedLedgerIds = emptyList(),
            availableFactIds = setOf(1, 2, 3),
            availableGoalIds = setOf(5, 6),
            availableLedgerIds = setOf(10),
        )
        assertEquals(setOf(DreamSource(DreamSourceType.FACT, 1), DreamSource(DreamSourceType.GOAL, 5)), sources.toSet())
    }

    @Test
    fun `a hallucinated id set with no real overlap yields no sources at all`() {
        val sources = validatedDreamSources(
            connectedFactIds = listOf(404),
            connectedGoalIds = listOf(404),
            connectedLedgerIds = listOf(404),
            availableFactIds = setOf(1),
            availableGoalIds = setOf(2),
            availableLedgerIds = setOf(3),
        )
        assertTrue(sources.isEmpty())
        assertFalse(hasSufficientProvenance(sources))
    }

    @Test
    fun `duplicate ids across the same list collapse to one source`() {
        val sources = validatedDreamSources(
            connectedFactIds = listOf(1, 1, 1),
            connectedGoalIds = emptyList(),
            connectedLedgerIds = emptyList(),
            availableFactIds = setOf(1),
            availableGoalIds = emptySet(),
            availableLedgerIds = emptySet(),
        )
        assertEquals(1, sources.size)
    }
}
