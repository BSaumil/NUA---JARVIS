package com.nua.assistant.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillManifestTest {

    private val manifest = SkillManifest(
        parameters = listOf(
            SkillParameter("contact", required = true),
            SkillParameter("message", required = true),
            SkillParameter("tone"),
        ),
    )

    @Test
    fun `all required parameters present means nothing is missing`() {
        val missing = missingRequiredParameters(manifest, mapOf("contact" to "Sam", "message" to "running late"))
        assertTrue(missing.isEmpty())
    }

    @Test
    fun `an absent required parameter is reported`() {
        val missing = missingRequiredParameters(manifest, mapOf("contact" to "Sam"))
        assertEquals(listOf("message"), missing)
    }

    @Test
    fun `a blank required parameter counts as missing, not present`() {
        val missing = missingRequiredParameters(manifest, mapOf("contact" to "Sam", "message" to "   "))
        assertEquals(listOf("message"), missing)
    }

    @Test
    fun `an absent optional parameter is not reported`() {
        val missing = missingRequiredParameters(manifest, mapOf("contact" to "Sam", "message" to "hi"))
        assertTrue(missing.isEmpty())
    }

    @Test
    fun `every missing required parameter is reported, not just the first`() {
        assertEquals(listOf("contact", "message"), missingRequiredParameters(manifest, emptyMap()))
    }

    @Test
    fun `a skill with no declared parameters accepts an empty map`() {
        assertTrue(missingRequiredParameters(SkillManifest(), emptyMap()).isEmpty())
    }

    @Test
    fun `undeclared parameters are stripped before the skill sees them`() {
        val filtered = filterToDeclaredParameters(
            manifest,
            mapOf("contact" to "Sam", "message" to "hi", "recipient_override" to "attacker@example.com"),
        )
        assertEquals(mapOf("contact" to "Sam", "message" to "hi"), filtered)
    }

    @Test
    fun `declared optional parameters survive filtering`() {
        val filtered = filterToDeclaredParameters(manifest, mapOf("contact" to "Sam", "tone" to "warm"))
        assertEquals(mapOf("contact" to "Sam", "tone" to "warm"), filtered)
    }

    @Test
    fun `a skill declaring no parameters receives none`() {
        assertTrue(filterToDeclaredParameters(SkillManifest(), mapOf("anything" to "at all")).isEmpty())
    }

    @Test
    fun `permissions are described in words a user would recognize`() {
        assertEquals("location", describePermission("android.permission.ACCESS_COARSE_LOCATION"))
        assertEquals("contacts access", describePermission("android.permission.READ_CONTACTS"))
        assertEquals("permission to add calendar events", describePermission("android.permission.WRITE_CALENDAR"))
    }

    @Test
    fun `an unmapped permission still degrades to something readable`() {
        assertEquals("body sensors", describePermission("android.permission.BODY_SENSORS"))
    }
}
