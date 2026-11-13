package com.nua.assistant.evolution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformRepairSynthesizerTest {
    @Test
    fun `synthesized program must keep caller input key and bounded instruction count`() {
        val valid = SovereignProgram(
            inputKey = "payload",
            instructions = listOf(
                TransformInstruction(TransformOpcode.TRIM),
                TransformInstruction(TransformOpcode.REPLACE_LITERAL, arg1 = "bad", arg2 = "good"),
            ),
        )
        assertTrue(validateSynthesizedProgram(valid, "payload"))
        assertFalse(validateSynthesizedProgram(valid.copy(inputKey = "other"), "payload"))
        assertFalse(
            validateSynthesizedProgram(
                valid.copy(instructions = List(17) { TransformInstruction(TransformOpcode.TRIM) }),
                "payload",
            ),
        )
    }

    @Test
    fun `synthesized program refuses dangerous expansion-shaped operands`() {
        val invalid = SovereignProgram(
            inputKey = "payload",
            instructions = listOf(
                TransformInstruction(
                    TransformOpcode.REPLACE_LITERAL,
                    arg1 = "x",
                    arg2 = "y".repeat(9_000),
                ),
            ),
        )
        assertFalse(validateSynthesizedProgram(invalid, "payload"))
    }
}
