package com.nua.assistant.automation

import com.nua.assistant.ai.NuaActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillCatalogTest {

    @Test
    fun `every action type has a human-readable name`() {
        NuaActionType.entries.forEach { action ->
            val name = displayNameFor(action)
            assertTrue("$action has a blank name", name.isNotBlank())
            assertFalse("$action still reads like an enum constant: $name", name.contains('_'))
            assertFalse("$action name is all-caps: $name", name == name.uppercase())
        }
    }

    @Test
    fun `names are distinct so two capabilities can't look like the same thing`() {
        val names = NuaActionType.entries.map { displayNameFor(it) }
        assertEquals(NuaActionType.entries.size, names.toSet().size)
    }

    @Test
    fun `names read as things NUA does, not as internal identifiers`() {
        assertEquals("Send a text", displayNameFor(NuaActionType.SMS_SEND))
        assertEquals("Create a calendar event", displayNameFor(NuaActionType.CALENDAR_INVITE))
        assertEquals("Reply to a message", displayNameFor(NuaActionType.REPLY_TO_NOTIFICATION))
    }
}
