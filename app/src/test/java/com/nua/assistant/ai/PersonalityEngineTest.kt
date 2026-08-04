package com.nua.assistant.ai

import com.nua.assistant.memory.UserFactEntity
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalityEngineTest {

    private val engine = PersonalityEngine()

    private fun fact(n: Int) = UserFactEntity(id = n.toLong(), key = "k$n", value = "fact $n", category = "other")

    @Test
    fun `a brand new conversation gets the cautious tone`() {
        val prompt = engine.systemPrompt(knownFacts = emptyList(), turnCount = 0)
        assertTrue(prompt.contains("still getting to know"))
    }

    @Test
    fun `a long history with several known facts gets the confident tone`() {
        val facts = (1..5).map { fact(it) }
        val prompt = engine.systemPrompt(knownFacts = facts, turnCount = 20)
        assertTrue(prompt.contains("get things sorted"))
    }

    @Test
    fun `known facts alone can push familiarity up without many turns`() {
        val facts = (1..10).map { fact(it) }
        val prompt = engine.systemPrompt(knownFacts = facts, turnCount = 1)
        assertTrue(prompt.contains("get things sorted"))
    }

    @Test
    fun `tone text differs between a new and an established conversation`() {
        val newPrompt = engine.systemPrompt(knownFacts = emptyList(), turnCount = 0)
        val establishedPrompt = engine.systemPrompt(knownFacts = (1..5).map { fact(it) }, turnCount = 20)
        assertNotEquals(newPrompt, establishedPrompt)
    }
}
