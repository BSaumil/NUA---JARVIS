package com.nua.assistant.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerTest {

    @Test
    fun `splits on non-alphanumeric and lowercases`() {
        assertEquals(setOf("smart", "home", "devices"), tokenize("Control Smart-Home Devices!"))
    }

    @Test
    fun `drops tokens of length 2 or less`() {
        // The rule two of the three original tokenizers already had: a 1-2 char token
        // carries too little signal to drive overlap ranking. The palette's copy lacked
        // this filter before unification — see the doc comment on tokenize().
        assertEquals(setOf("app"), tokenize("Open an app"))
    }

    @Test
    fun `deduplicates`() {
        assertEquals(setOf("coffee"), tokenize("coffee coffee coffee"))
    }

    @Test
    fun `blank input yields an empty set`() {
        assertTrue(tokenize("   ").isEmpty())
        assertTrue(tokenize("???").isEmpty())
    }

    @Test
    fun `hyphenated words split at the hyphen, matching word-start expectations`() {
        assertTrue("home" in tokenize("smart-home"))
    }
}
