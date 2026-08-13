package com.nua.assistant.timeline

import com.nua.assistant.dreams.DreamCategory
import com.nua.assistant.memory.DecisionEntity
import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.memory.GoalObservationEntity
import com.nua.assistant.memory.UserFactEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineBuilderTest {

    private fun fact(id: Long, createdAt: Long) =
        UserFactEntity(id = id, key = "k$id", value = "fact $id", category = "other", createdAt = createdAt, updatedAt = createdAt)

    private fun dream(id: Long, timestamp: Long) =
        DreamEntity(id = id, category = DreamCategory.PATTERN, text = "dream $id", timestamp = timestamp)

    private fun decision(id: Long, decidedAt: Long, outcome: String? = null, outcomeRecordedAt: Long? = null) =
        DecisionEntity(id = id, decision = "decision $id", decidedAt = decidedAt, outcome = outcome, outcomeRecordedAt = outcomeRecordedAt)

    private fun observation(id: Long, timestamp: Long) =
        GoalObservationEntity(id = id, goalId = 1, text = "observation $id", timestamp = timestamp)

    @Test
    fun `entries are ordered most recent first regardless of source`() {
        val entries = TimelineBuilder.build(
            facts = listOf(fact(1, createdAt = 100)),
            dreams = listOf(dream(1, timestamp = 300)),
            decisions = listOf(decision(1, decidedAt = 200)),
            goalObservations = listOf(observation(1, timestamp = 400)),
        )
        assertEquals(listOf(400L, 300L, 200L, 100L), entries.map { it.timestamp })
    }

    @Test
    fun `a decision with a recorded outcome produces two entries`() {
        val entries = TimelineBuilder.build(
            facts = emptyList(),
            dreams = emptyList(),
            decisions = listOf(decision(1, decidedAt = 100, outcome = "Worked out great", outcomeRecordedAt = 500)),
            goalObservations = emptyList(),
        )
        assertEquals(2, entries.size)
        assertTrue(entries.any { it is TimelineEntry.DecisionLogged && it.timestamp == 100L })
        assertTrue(entries.any { it is TimelineEntry.DecisionOutcomeRecorded && it.timestamp == 500L })
    }

    @Test
    fun `a decision without an outcome produces only the logged entry`() {
        val entries = TimelineBuilder.build(
            facts = emptyList(),
            dreams = emptyList(),
            decisions = listOf(decision(1, decidedAt = 100)),
            goalObservations = emptyList(),
        )
        assertEquals(1, entries.size)
        assertTrue(entries.single() is TimelineEntry.DecisionLogged)
    }

    @Test
    fun `empty inputs produce an empty timeline`() {
        val entries = TimelineBuilder.build(emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(entries.isEmpty())
    }
}
