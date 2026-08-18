package com.nua.assistant.ui.orb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbStateTest {

    @Test
    fun `every state has an appearance`() {
        OrbState.entries.forEach { assertNotNull(appearanceFor(it)) }
    }

    @Test
    fun `every state is distinguishable from every other`() {
        // The Orb is NUA's identity — two states that render identically would make it a
        // generic glowing circle, which is precisely what it must not be.
        val appearances = OrbState.entries.map { appearanceFor(it) }
        assertEquals(OrbState.entries.size, appearances.toSet().size)
    }

    @Test
    fun `offline is genuinely inert, not slowly alive`() {
        val offline = appearanceFor(OrbState.OFFLINE)
        assertNull("offline must not breathe", offline.breathPeriodMillis)
        assertEquals("offline must not change size", offline.restScale, offline.peakScale, 0.0001f)
        assertFalse(offline.showsParticles)
        assertFalse(offline.showsSweep)
        assertTrue("offline must be visibly dimmed", offline.intensity < 1f)
        assertEquals(OrbPalette.MUTED, offline.palette)
    }

    @Test
    fun `only thinking shows particles and only acting sweeps`() {
        OrbState.entries.forEach { state ->
            val appearance = appearanceFor(state)
            assertEquals("particles for $state", state == OrbState.THINKING, appearance.showsParticles)
            assertEquals("sweep for $state", state == OrbState.ACTING, appearance.showsSweep)
        }
    }

    @Test
    fun `listening is visibly larger at rest than idle`() {
        // "Expands subtly" has to be legible without watching a full breath cycle.
        assertTrue(appearanceFor(OrbState.LISTENING).restScale > appearanceFor(OrbState.IDLE).restScale)
    }

    @Test
    fun `idle breathes slowly enough to read as resting`() {
        val idle = appearanceFor(OrbState.IDLE)
        assertNotNull(idle.breathPeriodMillis)
        assertTrue("idle breath was ${idle.breathPeriodMillis}ms", idle.breathPeriodMillis!! >= 3000)
    }

    @Test
    fun `error is calmer than warning`() {
        // An error should read as controlled, not frantic — a faster, bigger red pulse
        // would make failures feel worse than they are.
        val error = appearanceFor(OrbState.ERROR)
        val warning = appearanceFor(OrbState.WARNING)
        assertTrue("error should pulse slower", error.breathPeriodMillis!! > warning.breathPeriodMillis!!)
        assertTrue("error should pulse smaller", error.peakScale < warning.peakScale)
    }

    @Test
    fun `alert states drop the brand gradient for a flat status colour`() {
        // A warning that still looks like the brand gradient doesn't read as a warning.
        assertEquals(OrbPalette.WARNING, appearanceFor(OrbState.WARNING).palette)
        assertEquals(OrbPalette.SUCCESS, appearanceFor(OrbState.SUCCESS).palette)
        assertEquals(OrbPalette.CRITICAL, appearanceFor(OrbState.ERROR).palette)
    }

    @Test
    fun `only the states meant to be exceptional reach for pink`() {
        val exceptional = OrbState.entries.filter { appearanceFor(it).palette == OrbPalette.EXCEPTIONAL }
        assertEquals(listOf(OrbState.LISTENING), exceptional)
    }

    @Test
    fun `success confirmation is short`() {
        assertTrue(appearanceFor(OrbState.SUCCESS).breathPeriodMillis!! <= 600)
    }

    @Test
    fun `every state announces something specific to TalkBack`() {
        val descriptions = OrbState.entries.map { orbContentDescription(it) }
        assertEquals("descriptions must be distinct", OrbState.entries.size, descriptions.toSet().size)
        descriptions.forEach { assertTrue("description must not be blank", it.isNotBlank()) }
    }

    @Test
    fun `no state animates at zero or negative period`() {
        OrbState.entries.forEach { state ->
            appearanceFor(state).breathPeriodMillis?.let {
                assertTrue("$state period was $it", it > 0)
            }
        }
    }

    @Test
    fun `peak is never smaller than rest`() {
        OrbState.entries.forEach { state ->
            val appearance = appearanceFor(state)
            assertTrue("$state peak < rest", appearance.peakScale >= appearance.restScale)
        }
    }
}
