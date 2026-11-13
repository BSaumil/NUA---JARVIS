package com.nua.assistant.evolution

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SovereignCodeRunnerTest {
    private val runner = SovereignCodeRunner(Json { ignoreUnknownKeys = true })

    @Test
    fun `shell execution is explicitly unsupported in Android sandbox`() {
        val result = runner.run(
            CodeExecutionRequest(
                language = SandboxLanguage.SHELL,
                program = null,
                inputs = emptyMap(),
            ),
        )
        assertEquals(CodeExecutionStatus.UNSUPPORTED_RUNTIME, result.status)
        assertNull(result.output)
    }

    @Test
    fun `bounded transform program executes entirely in memory`() {
        val result = runner.run(
            CodeExecutionRequest(
                language = SandboxLanguage.NUA_TRANSFORM_V1,
                program = SovereignProgram(
                    inputKey = "payload",
                    instructions = listOf(
                        TransformInstruction(TransformOpcode.TRIM),
                        TransformInstruction(TransformOpcode.REPLACE_LITERAL, arg1 = "bad", arg2 = "good"),
                        TransformInstruction(TransformOpcode.UPPERCASE),
                    ),
                ),
                inputs = mapOf("payload" to "  bad result  "),
            ),
        )

        assertEquals(CodeExecutionStatus.SUCCEEDED, result.status)
        assertEquals("GOOD RESULT", result.output)
        assertEquals(3, result.executedOperations)
    }

    @Test
    fun `json field extraction is deterministic and capability free`() {
        val result = runner.run(
            CodeExecutionRequest(
                language = SandboxLanguage.NUA_TRANSFORM_V1,
                program = SovereignProgram(
                    inputKey = "payload",
                    instructions = listOf(
                        TransformInstruction(TransformOpcode.JSON_FIELD, arg1 = "result.value"),
                    ),
                ),
                inputs = mapOf("payload" to """{"result":{"value":"repaired"}}"""),
            ),
        )
        assertEquals(CodeExecutionStatus.SUCCEEDED, result.status)
        assertEquals("repaired", result.output)
    }

    @Test
    fun `operation budget fails closed`() {
        val result = runner.run(
            CodeExecutionRequest(
                language = SandboxLanguage.NUA_TRANSFORM_V1,
                program = SovereignProgram(
                    inputKey = "payload",
                    instructions = List(3) { TransformInstruction(TransformOpcode.TRIM) },
                ),
                inputs = mapOf("payload" to "x"),
                limits = RunnerResourceLimits(maxOperations = 2),
            ),
        )
        assertEquals(CodeExecutionStatus.LIMIT_EXCEEDED, result.status)
    }

    @Test
    fun `output budget prevents repair expansion bomb`() {
        val result = runner.run(
            CodeExecutionRequest(
                language = SandboxLanguage.NUA_TRANSFORM_V1,
                program = SovereignProgram(
                    inputKey = "payload",
                    instructions = listOf(
                        TransformInstruction(TransformOpcode.SUFFIX, arg1 = "1234567890"),
                    ),
                ),
                inputs = mapOf("payload" to "x"),
                limits = RunnerResourceLimits(maxOutputBytes = 4),
            ),
        )
        assertEquals(CodeExecutionStatus.LIMIT_EXCEEDED, result.status)
        assertTrue(result.executedOperations == 1)
    }
}
