package com.nua.assistant.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseTest {

    @Test
    fun `every requested phrase is present`() {
        val ids = WakePhrases.ALL.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("jarvis", "hey_nua", "hello_nua", "nua", "daddys_home", "wake_up_sleepy_head")))
    }

    @Test
    fun `phrase ids are unique`() {
        val ids = WakePhrases.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `custom asset file names are unique`() {
        val fileNames = WakePhrases.ALL
            .map { it.source }
            .filterIsInstance<WakePhraseSource.CustomAsset>()
            .map { it.assetFileName }
        assertEquals(fileNames.size, fileNames.toSet().size)
    }

    @Test
    fun `jarvis is the only built-in, everything else needs a trained model`() {
        val builtIns = WakePhrases.ALL.filter { it.source is WakePhraseSource.BuiltIn }
        assertEquals(listOf("jarvis"), builtIns.map { it.id })
    }
}
