package com.nua.assistant.evolution

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformRepairExperimentRunnerTest {
    private val runner = TransformRepairExperimentRunner(
        SovereignCodeRunner(Json { ignoreUnknownKeys = true }),
    )

    @Test
    fun `challenger is measured against champion and known bad control`() {
        val champion = SovereignProgram(
            inputKey = "payload",
            instructions = listOf(TransformInstruction(TransformOpcode.TRIM)),
        )
        val challenger = SovereignProgram(
            inputKey = "payload",
            instructions = listOf(
                TransformInstruction(TransformOpcode.TRIM),
                TransformInstruction(TransformOpcode.REPLACE_LITERAL, arg1 = "colour", arg2 = "color"),
            ),
        )
        val knownBad = SovereignProgram(
            inputKey = "payload",
            instructions = listOf(TransformInstruction(TransformOpcode.UPPERCASE)),
        )
        val cases = listOf(
            TransformRepairTestCase(mapOf("payload" to " colour "), "color"),
            TransformRepairTestCase(mapOf("payload" to "color"), "color"),
            TransformRepairTestCase(mapOf("payload" to " colour"), "color"),
            TransformRepairTestCase(mapOf("payload" to "color "), "color"),
            TransformRepairTestCase(mapOf("payload" to " colour "), "color"),
        )

        val experiment = runner.run(TransformRepairPrograms(champion, challenger, knownBad), cases)
        val result = evaluateRepairExperiment(experiment)

        assertTrue(experiment.knownBadDetected)
        assertTrue(experiment.challenger.targetFailuresFixed > 0)
        assertTrue(result.eligibleForPromotion)
    }

    @Test
    fun `challenger regression is rejected by lab`() {
        val champion = SovereignProgram(
            inputKey = "payload",
            instructions = listOf(TransformInstruction(TransformOpcode.TRIM)),
        )
        val challenger = SovereignProgram(
            inputKey = "payload",
            instructions = listOf(TransformInstruction(TransformOpcode.UPPERCASE)),
        )
        val knownBad = SovereignProgram(
            inputKey = "payload",
            instructions = listOf(TransformInstruction(TransformOpcode.SUFFIX, arg1 = "!")),
        )
        val cases = List(5) { TransformRepairTestCase(mapOf("payload" to " ok "), "ok") }

        val result = evaluateRepairExperiment(
            runner.run(TransformRepairPrograms(champion, challenger, knownBad), cases),
        )

        assertFalse(result.eligibleForPromotion)
    }
}
