package com.nua.assistant.privacy

import com.nua.assistant.dreams.DreamCategory
import com.nua.assistant.goals.GoalType
import com.nua.assistant.memory.DecisionEntity
import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.memory.GoalEntity
import com.nua.assistant.memory.MemoryType
import com.nua.assistant.memory.UserFactEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class DataExportTest {

    @Test
    fun `an empty export names every section as empty rather than omitting them`() {
        val export = buildDataExport(emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(export.contains("Facts (0)"))
        assertTrue(export.contains("Goals (0)"))
        assertTrue(export.contains("Decisions (0)"))
        assertTrue(export.contains("Dreams (0)"))
    }

    @Test
    fun `a populated export includes every category's real content`() {
        val facts = listOf(UserFactEntity(key = "k", value = "likes tea", category = "preference", memoryType = MemoryType.SEMANTIC))
        val goals = listOf(GoalEntity(text = "get mornings under control", type = GoalType.GOAL))
        val decisions = listOf(DecisionEntity(decision = "switch to 4-day week", reasoning = "burnout", outcome = "worked well"))
        val dreams = listOf(DreamEntity(category = DreamCategory.PATTERN, text = "you tend to overcommit on Tuesdays"))

        val export = buildDataExport(facts, goals, decisions, dreams)

        assertTrue(export.contains("likes tea"))
        assertTrue(export.contains("get mornings under control"))
        assertTrue(export.contains("switch to 4-day week"))
        assertTrue(export.contains("burnout"))
        assertTrue(export.contains("worked well"))
        assertTrue(export.contains("you tend to overcommit on Tuesdays"))
    }

    @Test
    fun `inactive goals are labeled as such`() {
        val goals = listOf(GoalEntity(text = "old goal", active = false))
        val export = buildDataExport(emptyList(), goals, emptyList(), emptyList())
        assertTrue(export.contains("(inactive)"))
    }
}
