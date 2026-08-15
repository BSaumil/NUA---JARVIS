package com.nua.assistant.timeline

import com.nua.assistant.memory.DecisionEntity
import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.memory.GoalObservationEntity
import com.nua.assistant.memory.UserFactEntity

/** One moment in NUA's memory of you, from any source, ordered only by when it happened. */
sealed class TimelineEntry {
    abstract val timestamp: Long

    data class FactLearned(val fact: UserFactEntity, override val timestamp: Long) : TimelineEntry()
    data class DreamSurfaced(val dream: DreamEntity, override val timestamp: Long) : TimelineEntry()
    data class DecisionLogged(val decision: DecisionEntity, override val timestamp: Long) : TimelineEntry()
    data class DecisionOutcomeRecorded(val decision: DecisionEntity, override val timestamp: Long) : TimelineEntry()
    data class GoalObservationNoted(val observation: GoalObservationEntity, override val timestamp: Long) : TimelineEntry()
}

/**
 * Merges facts, dreams, decisions, and goal observations — each already durable on its own —
 * into one chronological feed. No new storage, no new inference; just the existing memory
 * surfaces read back in the order they actually happened.
 */
object TimelineBuilder {

    fun build(
        facts: List<UserFactEntity>,
        dreams: List<DreamEntity>,
        decisions: List<DecisionEntity>,
        goalObservations: List<GoalObservationEntity>,
    ): List<TimelineEntry> {
        val entries = mutableListOf<TimelineEntry>()
        facts.forEach { entries += TimelineEntry.FactLearned(it, it.createdAt) }
        dreams.forEach { entries += TimelineEntry.DreamSurfaced(it, it.timestamp) }
        decisions.forEach { decision ->
            entries += TimelineEntry.DecisionLogged(decision, decision.decidedAt)
            val outcomeAt = decision.outcomeRecordedAt
            if (decision.outcome != null && outcomeAt != null) {
                entries += TimelineEntry.DecisionOutcomeRecorded(decision, outcomeAt)
            }
        }
        goalObservations.forEach { entries += TimelineEntry.GoalObservationNoted(it, it.timestamp) }

        return entries.sortedByDescending { it.timestamp }
    }
}
