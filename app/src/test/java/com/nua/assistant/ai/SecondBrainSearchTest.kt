package com.nua.assistant.ai

import com.nua.assistant.dreams.DreamCategory
import com.nua.assistant.memory.DecisionEntity
import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.memory.UserFactEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondBrainSearchTest {

    private fun fact(id: Long, value: String, category: String = "other") =
        UserFactEntity(id = id, key = "k$id", value = value, category = category)

    private fun dream(id: Long, text: String, category: DreamCategory = DreamCategory.PATTERN) =
        DreamEntity(id = id, category = category, text = text)

    private fun decision(id: Long, decision: String, reasoning: String? = null, outcome: String? = null) =
        DecisionEntity(id = id, decision = decision, reasoning = reasoning, outcome = outcome)

    @Test
    fun `blank query returns nothing`() {
        val results = SecondBrainSearch.search(
            query = "   ",
            facts = listOf(fact(1, "Likes jazz")),
            dreams = listOf(dream(1, "You tend to work late on Fridays")),
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `matches facts and dreams by token overlap`() {
        val results = SecondBrainSearch.search(
            query = "workout music",
            facts = listOf(fact(1, "Prefers energetic workout music"), fact(2, "Works remotely")),
            dreams = listOf(dream(1, "You always ask for workout playlists on Monday mornings")),
        )
        assertEquals(2, results.size)
        assertTrue(results.any { it is SecondBrainResult.FactHit && it.fact.id == 1L })
        assertTrue(results.any { it is SecondBrainResult.DreamHit && it.dream.id == 1L })
    }

    @Test
    fun `unrelated facts and dreams are excluded`() {
        val results = SecondBrainSearch.search(
            query = "finance budget",
            facts = listOf(fact(1, "Likes jazz")),
            dreams = listOf(dream(1, "You mention feeling tired most Tuesdays")),
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `higher overlap ranks first`() {
        val results = SecondBrainSearch.search(
            query = "budget finance spending review",
            facts = listOf(
                fact(1, "Mentioned budget once"),
                fact(2, "Does a monthly finance and budget spending review"),
            ),
            dreams = emptyList(),
        )
        assertEquals(2L, (results.first() as SecondBrainResult.FactHit).fact.id)
    }

    @Test
    fun `result never exceeds the limit`() {
        val facts = (1..50).map { fact(it.toLong(), "budget note $it") }
        val results = SecondBrainSearch.search(query = "budget", facts = facts, dreams = emptyList(), limit = 10)
        assertEquals(10, results.size)
    }

    @Test
    fun `matches decisions by decision, reasoning, and outcome text`() {
        val results = SecondBrainSearch.search(
            query = "four day week",
            facts = emptyList(),
            dreams = emptyList(),
            decisions = listOf(
                decision(1, "Switching to a four-day work week", reasoning = "Burnout was creeping in"),
                decision(2, "Adopted a new budgeting app", outcome = "Cut grocery spending by switching stores"),
            ),
        )
        assertEquals(1, results.size)
        assertEquals(1L, (results.first() as SecondBrainResult.DecisionHit).decision.id)
    }
}
