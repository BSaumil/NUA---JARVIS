package com.nua.assistant.ai

import com.nua.assistant.memory.UserFactEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FactRelevanceTest {

    private fun fact(id: Long, value: String, category: String = "other", updatedAt: Long = 0L) =
        UserFactEntity(id = id, key = "k$id", value = value, category = category, updatedAt = updatedAt)

    @Test
    fun `small fact lists pass through unfiltered`() {
        val facts = listOf(fact(1, "Likes jazz"), fact(2, "Works remotely"))
        val ranked = FactRelevance.rank(facts, currentMessage = "anything", limit = 12)
        assertEquals(2, ranked.size)
    }

    @Test
    fun `over the limit, keyword overlap wins over plain recency`() {
        val facts = (1..20).map { fact(it.toLong(), "fact number $it about nothing relevant", updatedAt = it.toLong()) } +
            fact(21, "Prefers energetic workout music", updatedAt = 1)
        val ranked = FactRelevance.rank(facts, currentMessage = "put on some workout music", limit = 5)
        assertTrue(ranked.any { it.id == 21L })
    }

    @Test
    fun `with no message, falls back to recency`() {
        val facts = (1..20).map { fact(it.toLong(), "fact $it", updatedAt = it.toLong()) }
        val ranked = FactRelevance.rank(facts, currentMessage = null, limit = 3)
        assertEquals(listOf(20L, 19L, 18L), ranked.map { it.id })
    }

    @Test
    fun `result never exceeds the limit`() {
        val facts = (1..50).map { fact(it.toLong(), "fact $it") }
        val ranked = FactRelevance.rank(facts, currentMessage = "hello", limit = 12)
        assertEquals(12, ranked.size)
    }
}
