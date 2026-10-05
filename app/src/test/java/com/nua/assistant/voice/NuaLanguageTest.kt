package com.nua.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `plain English text has no detectable script, so the caller falls back`() {
        assertNull(NuaLanguage.scriptDetectedLanguage("Sure, I can help with that."))
    }

    @Test
    fun `blank text has no detectable script`() {
        assertNull(NuaLanguage.scriptDetectedLanguage(""))
    }

    @Test
    fun `Devanagari script is detected as Hindi`() {
        // "theek hai, main madad kar sakta hoon" written in Devanagari.
        assertEquals(NuaLanguage.HINDI, NuaLanguage.scriptDetectedLanguage("ठीक है, मैं मदद कर सकता हूँ"))
    }

    @Test
    fun `Gujarati script is detected as Gujarati, never mistaken for Devanagari`() {
        assertEquals(NuaLanguage.GUJARATI, NuaLanguage.scriptDetectedLanguage("હું મદદ કરી શકું છું"))
    }

    @Test
    fun `Gurmukhi script is detected as Punjabi`() {
        assertEquals(NuaLanguage.PUNJABI, NuaLanguage.scriptDetectedLanguage("ਮੈਂ ਮਦਦ ਕਰ ਸਕਦਾ ਹਾਂ"))
    }

    @Test
    fun `Han characters are detected as Chinese`() {
        assertEquals(NuaLanguage.CHINESE, NuaLanguage.scriptDetectedLanguage("我可以帮忙"))
    }

    @Test
    fun `a single non-Latin character embedded in otherwise-English text is still enough to detect`() {
        assertEquals(NuaLanguage.HINDI, NuaLanguage.scriptDetectedLanguage("Sure, that's called ठीक in Hindi."))
    }

    @Test
    fun `Italian and Spanish text, sharing the Latin script with English, are not distinguishable this way -- a named limitation, not a bug`() {
        assertNull(NuaLanguage.scriptDetectedLanguage("Certo, posso aiutarti con questo."))
        assertNull(NuaLanguage.scriptDetectedLanguage("Claro, puedo ayudarte con eso."))
    }
}
