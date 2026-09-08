package com.nua.assistant.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NuaDestinationTest {

    @Test
    fun `there are exactly five destinations, in the specified order`() {
        // Five is the design, not an accident — a sixth tab would make the bar a menu.
        assertEquals(
            listOf(
                NuaDestination.HOME,
                NuaDestination.ASK,
                NuaDestination.MEMORY,
                NuaDestination.ACT,
                NuaDestination.YOU,
            ),
            NuaDestination.entries.toList(),
        )
    }

    @Test
    fun `every destination has a short label and a fuller screen-reader description`() {
        NuaDestination.entries.forEach { destination ->
            assertTrue(destination.label.isNotBlank())
            assertTrue(
                "${destination.name} description should say more than its label",
                destination.contentDescription.length > destination.label.length,
            )
        }
    }

    @Test
    fun `labels are single words so the bar stays readable at small widths`() {
        NuaDestination.entries.forEach { destination ->
            assertFalse("${destination.name} label has a space", destination.label.contains(' '))
        }
    }

    @Test
    fun `all seven information-hierarchy labels are present`() {
        assertEquals(7, InfoLabel.entries.size)
        assertEquals(
            setOf("Now", "Next", "Later", "Memory", "Insight", "Action", "Alert"),
            InfoLabel.entries.map { it.label }.toSet(),
        )
    }

    @Test
    fun `memory sections cover the four views onto what NUA knows`() {
        // PRIVACY joined the other three deliberately (Memory Vault/Privacy Centre) —
        // nested under MEMORY rather than promoted to a sixth bottom-nav destination,
        // same reasoning the "five destinations" test above pins for the top level.
        assertEquals(
            listOf(MemorySection.SEARCH, MemorySection.TIMELINE, MemorySection.DOCUMENTS, MemorySection.PRIVACY),
            MemorySection.entries.toList(),
        )
    }
}
