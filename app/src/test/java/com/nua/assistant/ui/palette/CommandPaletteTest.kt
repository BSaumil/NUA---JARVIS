package com.nua.assistant.ui.palette

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.trust.AutonomyTier
import com.nua.assistant.ui.nav.NuaDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val SKILLS = listOf(
    PaletteSkill(NuaActionType.GET_WEATHER, "Check the weather", AutonomyTier.T0),
    PaletteSkill(NuaActionType.OPEN_APP, "Open an app", AutonomyTier.T1),
    PaletteSkill(NuaActionType.SMART_HOME, "Control smart-home devices", AutonomyTier.T2),
    PaletteSkill(NuaActionType.SMS_SEND, "Send a text", AutonomyTier.T3),
    PaletteSkill(NuaActionType.PLAN_TASK, "Plan something out", AutonomyTier.T4),
)

private val MEMORIES = listOf(
    PaletteMemory("Sam prefers morning meetings", "Memory"),
    PaletteMemory("Renew the lease before March", "Insight"),
)

class CommandPaletteTest {

    @Test
    fun `an empty query offers the destinations, not a wall of everything`() {
        val entries = buildPalette("", SKILLS, MEMORIES)
        assertEquals(NuaDestination.entries.size, entries.size)
        assertTrue(entries.all { it.action is PaletteAction.Navigate })
    }

    @Test
    fun `a non-empty query always ends with an ask-NUA fallback so it never dead-ends`() {
        val entries = buildPalette("zzzznomatch", SKILLS, MEMORIES)
        assertTrue(entries.isNotEmpty())
        val last = entries.last()
        assertTrue(last.action is PaletteAction.AskNua)
        assertTrue(last.title.contains("zzzznomatch"))
    }

    @Test
    fun `destinations match by name`() {
        val entries = buildPalette("mem", SKILLS, MEMORIES)
        val navigate = entries.firstOrNull { it.action is PaletteAction.Navigate }
        assertEquals(NuaDestination.MEMORY, (navigate?.action as? PaletteAction.Navigate)?.destination)
    }

    @Test
    fun `a prefix match outranks a mere substring match`() {
        assertTrue(matchScore("Memory", "mem") > matchScore("Remember this", "mem"))
    }

    @Test
    fun `a word-start match outranks a mid-word substring match`() {
        assertTrue(matchScore("Check the weather", "weather") > matchScore("Sweatherproof", "weather"))
    }

    @Test
    fun `no match scores zero`() {
        assertEquals(0, matchScore("Check the weather", "banana"))
        assertEquals(0, matchScore("anything", ""))
    }

    // -----------------------------------------------------------------------------------
    // The safety property: the palette must never become a one-tap way around the
    // confirmation and step-up gates that protect high-authority actions.
    // -----------------------------------------------------------------------------------

    @Test
    fun `low-tier skills execute immediately`() {
        val weather = buildPalette("weather", SKILLS, MEMORIES).first { it.action is PaletteAction.RunSkill }
        assertTrue(weather.executesImmediately)
    }

    @Test
    fun `T3 and above never execute immediately from the palette`() {
        listOf("Send a text", "Plan something out").forEach { name ->
            val entry = buildPalette(name, SKILLS, MEMORIES).first { it.action is PaletteAction.RunSkill }
            assertFalse("$name must not fire straight from the palette", entry.executesImmediately)
        }
    }

    @Test
    fun `gated rows are phrased as requests, not commands`() {
        val sms = buildPalette("Send a text", SKILLS, MEMORIES).first { it.action is PaletteAction.RunSkill }
        assertTrue("gated row should read as a request: ${sms.title}", sms.title.startsWith("Ask NUA to"))
    }

    @Test
    fun `ungated rows are phrased plainly`() {
        val weather = buildPalette("weather", SKILLS, MEMORIES).first { it.action is PaletteAction.RunSkill }
        assertFalse(weather.title.startsWith("Ask NUA to"))
    }

    @Test
    fun `the ask-NUA fallback is itself never an immediate execution`() {
        val ask = buildPalette("anything at all", SKILLS, MEMORIES).last()
        assertFalse(ask.executesImmediately)
    }

    // -----------------------------------------------------------------------------------

    @Test
    fun `memories match on token overlap`() {
        val entries = buildPalette("lease", SKILLS, MEMORIES)
        val memory = entries.firstOrNull { it.action is PaletteAction.OpenMemory }
        assertTrue(memory?.title?.contains("lease") == true)
    }

    @Test
    fun `results are capped so the palette stays a shortcut`() {
        val many = (1..50).map { PaletteMemory("meeting number $it", "Memory") }
        val entries = buildPalette("meeting", SKILLS, many, limit = 8)
        assertEquals(8, entries.size)
    }

    @Test
    fun `higher-scoring entries sort first`() {
        val entries = buildPalette("Open an app", SKILLS, MEMORIES)
        val scores = entries.dropLast(1).map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }
}
