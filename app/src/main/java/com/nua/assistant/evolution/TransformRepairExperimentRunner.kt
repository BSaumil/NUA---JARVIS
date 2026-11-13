package com.nua.assistant.evolution

import javax.inject.Inject
import javax.inject.Singleton

data class TransformRepairTestCase(
    val inputs: Map<String, String>,
    val expectedOutput: String,
)

data class TransformRepairPrograms(
    val champion: SovereignProgram,
    val challenger: SovereignProgram,
    /** Deliberately broken control. If this is not caught, the lab refuses to trust itself. */
    val knownBad: SovereignProgram,
)

/**
 * Executes Champion / Challenger / Known-Bad against the same ephemeral test set and
 * derives metrics for [evaluateRepairExperiment]. Test inputs are never persisted by this
 * class. It is suitable for parser/transform repairs that can be expressed in the bounded
 * SovereignCodeRunner DSL.
 */
@Singleton
class TransformRepairExperimentRunner @Inject constructor(
    private val codeRunner: SovereignCodeRunner,
) {
    fun run(
        programs: TransformRepairPrograms,
        cases: List<TransformRepairTestCase>,
    ): RepairExperiment {
        require(cases.isNotEmpty()) { "at least one repair test case is required" }

        val champion = evaluate(programs.champion, cases)
        val challenger = evaluate(programs.challenger, cases)
        val knownBad = evaluate(programs.knownBad, cases)

        val targetFixed = cases.indices.count { index ->
            !champion.correct[index] && challenger.correct[index]
        }
        val regressions = cases.indices.count { index ->
            champion.correct[index] && !challenger.correct[index]
        }

        return RepairExperiment(
            champion = EvaluationMetrics(
                totalCases = cases.size,
                passedCases = champion.correct.count { it },
                targetFailuresFixed = 0,
                regressionFailures = 0,
                guardianViolations = 0,
                privacyViolations = 0,
                authorityViolations = 0,
                meanLatencyMillis = champion.meanLatencyMillis,
            ),
            challenger = EvaluationMetrics(
                totalCases = cases.size,
                passedCases = challenger.correct.count { it },
                targetFailuresFixed = targetFixed,
                regressionFailures = regressions,
                guardianViolations = 0,
                privacyViolations = 0,
                authorityViolations = 0,
                meanLatencyMillis = challenger.meanLatencyMillis,
            ),
            knownBadDetected = knownBad.correct.any { !it },
        )
    }

    private data class ProgramEvaluation(
        val correct: List<Boolean>,
        val meanLatencyMillis: Long,
    )

    private fun evaluate(
        program: SovereignProgram,
        cases: List<TransformRepairTestCase>,
    ): ProgramEvaluation {
        var totalNanos = 0L
        val correctness = cases.map { case ->
            val started = System.nanoTime()
            val result = codeRunner.run(
                CodeExecutionRequest(
                    language = SandboxLanguage.NUA_TRANSFORM_V1,
                    program = program,
                    inputs = case.inputs,
                ),
            )
            totalNanos += System.nanoTime() - started
            result.status == CodeExecutionStatus.SUCCEEDED && result.output == case.expectedOutput
        }
        val meanMillis = (totalNanos / cases.size / 1_000_000L).coerceAtLeast(0)
        return ProgramEvaluation(correctness, meanMillis)
    }
}
