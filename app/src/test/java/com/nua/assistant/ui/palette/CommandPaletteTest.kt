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

    // -----------------------------------------------------------------------------------
    // Edge cases. Each of these was a real defect found by probing the ranking directly.
    // -----------------------------------------------------------------------------------

    @Test
    fun `a non-positive limit yields an empty palette rather than throwing`() {
        // take(limit - 1) throws IllegalArgumentException on a negative count.
        assertEquals(emptyList<PaletteEntry>(), buildPalette("meeting", SKILLS, MEMORIES, limit = 0))
        assertEquals(emptyList<PaletteEntry>(), buildPalette("", SKILLS, MEMORIES, limit = 0))
    }

    @Test
    fun `the empty-query palette honours the limit too`() {
        assertEquals(3, buildPalette("", SKILLS, MEMORIES, limit = 3).size)
    }

    @Test
    fun `a repeated word does not inflate a memory's rank`() {
        val padded = PaletteMemory("coffee coffee coffee", "Memory")
        val real = PaletteMemory("Drinks coffee only before noon", "Memory")
        val entries = buildPalette("coffee", SKILLS, listOf(padded, real))
        val scores = entries.filter { it.action is PaletteAction.OpenMemory }.map { it.score }
        assertEquals("repetition must not outrank relevance", 1, scores.distinct().size)
    }

    @Test
    fun `a repeated query term does not inflate scores either`() {
        val one = buildPalette("coffee", SKILLS, listOf(PaletteMemory("Likes coffee", "Memory")))
        val twice = buildPalette("coffee coffee", SKILLS, listOf(PaletteMemory("Likes coffee", "Memory")))
        assertEquals(
            one.first { it.action is PaletteAction.OpenMemory }.score,
            twice.first { it.action is PaletteAction.OpenMemory }.score,
        )
    }

    @Test
    fun `identical memories collapse to one row`() {
        val duplicated = listOf(
            PaletteMemory("Allergic to penicillin", "Memory"),
            PaletteMemory("Allergic to penicillin", "Insight"),
        )
        val rows = buildPalette("penicillin", SKILLS, duplicated).count { it.action is PaletteAction.OpenMemory }
        assertEquals(1, rows)
    }

    @Test
    fun `a hyphenated name still matches at its word boundary`() {
        // "smart-home" is two words to a reader; splitting on spaces alone made "home"
        // a mere substring match and buried the skill.
        assertEquals(matchScore("Read your notifications", "notifications"),
            matchScore("Control smart-home devices", "home"))
    }

    @Test
    fun `higher-scoring entries sort first`() {
        val entries = buildPalette("Open an app", SKILLS, MEMORIES)
        val scores = entries.dropLast(1).map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }
}
