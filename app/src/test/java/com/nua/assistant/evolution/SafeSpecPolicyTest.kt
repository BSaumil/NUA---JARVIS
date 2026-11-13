package com.nua.assistant.evolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeSpecPolicyTest {
    @Test fun `prompt and research spec policies are independently tunable in bounded ranges`() {
        val result = applySpecPatches(
            PromptSpecPolicy(),
            ResearchSpecPolicy(),
            listOf(
                SpecTuningPatch(SpecTuningKey.PROMPT_SCHEMA_REMINDERS, 2),
                SpecTuningPatch(SpecTuningKey.RESEARCH_MAX_SUBQUESTIONS, 10),
            ),
        )!!
        assertEquals(2, result.first.schemaReminderCount)
        assertEquals(1, result.first.maxRepairAttempts)
        assertEquals(10, result.second.maxSubquestions)
        assertEquals(2, result.second.crossCheckDepth)
    }

    @Test fun `unsafe spec range fails closed`() {
        assertNull(
            applySpecPatches(
                PromptSpecPolicy(),
                ResearchSpecPolicy(),
                listOf(SpecTuningPatch(SpecTuningKey.RESEARCH_CROSSCHECK_DEPTH, 99)),
            ),
        )
    }
}


    @Test fun `prompt spec only changes structured output tasks`() {
        val policy = PromptSpecPolicy(schemaReminderCount = 2, maxRepairAttempts = 1)
        assertEquals("base", applyPromptSpec("base", false, policy))
        val structured = applyPromptSpec("base", true, policy)!!
        assertEquals(2, Regex("Output contract reminder").findAll(structured).count())
        assert(structured.contains("self-check"))
    }
