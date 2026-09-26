package com.nua.assistant.context

import com.nua.assistant.goals.GoalType
import com.nua.assistant.memory.GoalEntity
import com.nua.assistant.notifications.NotificationSummary
import org.junit.Assert.assertEquals
import org.junit.Test

private fun snapshot(goals: List<GoalEntity>) = ContextSnapshot(
    timestampMillis = 0L,
    isOnline = true,
    weather = null,
    eventsToday = emptyList(),
    notificationSummary = NotificationSummary(emptyList(), emptyList()),
    activeGoals = goals,
    recentDecisions = emptyList(),
    currentPlace = null,
)

class ContextSnapshotTest {

    @Test
    fun `routines are only the goals explicitly typed ROUTINE`() {
        val routine = GoalEntity(text = "Keep up with the gym", type = GoalType.ROUTINE)
        val aspiration = GoalEntity(text = "Learn Spanish", type = GoalType.ASPIRATION)
        val commitment = GoalEntity(text = "Call Mom back", type = GoalType.COMMITMENT)

        val result = snapshot(listOf(routine, aspiration, commitment)).routines

        assertEquals(listOf(routine), result)
    }

    @Test
    fun `no active goals means no routines, not an error`() {
        assertEquals(emptyList<GoalEntity>(), snapshot(emptyList()).routines)
    }
}
