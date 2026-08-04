package com.nua.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NuaLanguageTest {

    @Test
    fun `all ten requested languages are present`() {
        val displayNames = NuaLanguage.entries.map { it.displayName }.toSet()
        assertEquals(10, NuaLanguage.entries.size)
        assertTrue(displayNames.containsAll(
            listOf("English", "Hindi", "Gujarati", "Marathi", "Italian", "Spanish", "Haryanvi", "Punjabi", "Vietnamese", "Chinese (Mandarin)"),
        ))
    }

    @Test
    fun `mirror directive names every supported language`() {
        val directive = NuaLanguage.mirrorDirective()
        NuaLanguage.entries.forEach { language -> assertTrue(directive.contains(language.displayName)) }
    }

    @Test
    fun `haryanvi shares hindi's speech tag since it has no dedicated Android locale`() {
        assertEquals(NuaLanguage.HINDI.speechTag, NuaLanguage.HARYANVI.speechTag)
    }
}
