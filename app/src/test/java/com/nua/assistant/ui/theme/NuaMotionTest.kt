package com.nua.assistant.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NuaMotionTest {

    @Test
    fun `reduced motion collapses every duration to an instant cut`() {
        // The claim "NUA respects reduced motion" is exactly the kind that quietly stops
        // being true, so it's asserted rather than trusted.
        listOf(NuaMotion.INSTANT, NuaMotion.TRANSITION, NuaMotion.EXPRESSIVE, NuaMotion.EXPRESSIVE_SLOW)
            .forEach { base ->
                assertEquals("$base should collapse to 0", 0, motionDuration(base, motionEnabled = false))
            }
    }

    @Test
    fun `normal motion passes the duration through unchanged`() {
        assertEquals(NuaMotion.TRANSITION, motionDuration(NuaMotion.TRANSITION, motionEnabled = true))
    }

    @Test
    fun `the scale is ordered and matches the specified bands`() {
        assertEquals(150, NuaMotion.INSTANT)
        assertEquals(250, NuaMotion.TRANSITION)
        assertTrue("expressive should sit in the 400-600ms band", NuaMotion.EXPRESSIVE in 400..600)
        assertTrue("expressive-slow should sit in the 400-600ms band", NuaMotion.EXPRESSIVE_SLOW in 400..600)
        assertTrue(NuaMotion.INSTANT < NuaMotion.TRANSITION)
        assertTrue(NuaMotion.TRANSITION < NuaMotion.EXPRESSIVE)
        assertTrue(NuaMotion.EXPRESSIVE < NuaMotion.EXPRESSIVE_SLOW)
    }

    @Test
    fun `ambient orb motion is deliberately slower than the whole interaction scale`() {
        // A "breathing" element cycling at interaction speed reads as panicking, not
        // resting — so the Orb's idle breath is intentionally outside this scale.
        val idleBreath = com.nua.assistant.ui.orb.appearanceFor(com.nua.assistant.ui.orb.OrbState.IDLE).breathPeriodMillis
        assertTrue(idleBreath != null && idleBreath > NuaMotion.EXPRESSIVE_SLOW)
    }
}
