package com.nua.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalityAxesTest {

    @Test
    fun `default axes is isDefault`() {
        assertTrue(PersonalityAxes().isDefault)
    }

    @Test
    fun `either axis away from default makes isDefault false`() {
        assertEquals(false, PersonalityAxes(humor = HumorLevel.LOW).isDefault)
        assertEquals(false, PersonalityAxes(directness = DirectnessLevel.BLUNT).isDefault)
    }

    @Test
    fun `default axes produces no directive at all, never an empty-but-present one`() {
        assertNull(axesDirective(PersonalityAxes()))
    }

    @Test
    fun `low humor produces a directive asking for less, not a fabricated opposite`() {
        val directive = axesDirective(PersonalityAxes(humor = HumorLevel.LOW))
        assertTrue(directive!!.contains("less humor"))
    }

    @Test
    fun `high humor produces a directive asking for more`() {
        val directive = axesDirective(PersonalityAxes(humor = HumorLevel.HIGH))
        assertTrue(directive!!.contains("more personality"))
    }

    @Test
    fun `gentle directness asks for warmth first`() {
        val directive = axesDirective(PersonalityAxes(directness = DirectnessLevel.GENTLE))
        assertTrue(directive!!.contains("gentler"))
    }

    @Test
    fun `blunt directness asks to skip the cushioning`() {
        val directive = axesDirective(PersonalityAxes(directness = DirectnessLevel.BLUNT))
        assertTrue(directive!!.contains("bluntness"))
    }

    @Test
    fun `both axes away from default combine into one directive naming both`() {
        val directive = axesDirective(PersonalityAxes(humor = HumorLevel.HIGH, directness = DirectnessLevel.BLUNT))
        assertTrue(directive!!.contains("more personality"))
        assertTrue(directive.contains("bluntness"))
    }
}
