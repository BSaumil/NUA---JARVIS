package com.nua.assistant.evolution

import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.extractJsonPayload
import com.nua.assistant.ai.mesh.InferenceTaskType
import com.nua.assistant.ai.mesh.ModelMesh
import com.nua.assistant.ai.mesh.PrivacySensitivity
import com.nua.assistant.ai.mesh.TaskContract
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_SYNTHESIS_EXAMPLES = 8
private const val MAX_EXAMPLE_CHARS = 16_384
private const val MAX_SYNTHESIZED_OPERATIONS = 16

fun validateSynthesizedProgram(
    program: SovereignProgram,
    expectedInputKey: String,
): Boolean =
    program.version == 1 &&
        program.inputKey == expectedInputKey &&
        program.instructions.isNotEmpty() &&
        program.instructions.size <= MAX_SYNTHESIZED_OPERATIONS &&
        program.instructions.all { instruction ->
            when (instruction.opcode) {
                TransformOpcode.REPLACE_LITERAL ->
                    !instruction.arg1.isNullOrEmpty() &&
                        instruction.arg1.length <= 8_192 &&
                        (instruction.arg2?.length ?: 0) <= 8_192
                TransformOpcode.PREFIX,
                TransformOpcode.SUFFIX,
                -> (instruction.arg1?.length ?: 0) <= 8_192
                TransformOpcode.TAKE,
                TransformOpcode.DROP,
                -> (instruction.intArg ?: -1) in 0..262_144
                TransformOpcode.EXTRACT_BETWEEN ->
                    !instruction.arg1.isNullOrEmpty() && !instruction.arg2.isNullOrEmpty()
                TransformOpcode.JSON_FIELD -> !instruction.arg1.isNullOrBlank()
                TransformOpcode.TRIM,
                TransformOpcode.LOWERCASE,
                TransformOpcode.UPPERCASE,
                TransformOpcode.SHA256,
                -> true
            }
        }

/**
 * Local-only repair synthesis for bounded parser/transform failures.
 *
 * Examples may contain private data, so this path is HIGH + requiresOffline. If Gemini
 * Nano/AICore is unavailable, synthesis simply returns null; it never falls back to
 * cloud. The generated program is still only a challenger until the Self-Repair Lab
 * proves it against Champion/Challenger/Known-Bad cases.
 */
@Singleton
class TransformRepairSynthesizer @Inject constructor(
    private val modelMesh: ModelMesh,
    private val json: Json,
) {
    suspend fun synthesize(
        failureSignature: String,
        inputKey: String,
        goal: String,
        examples: List<TransformRepairTestCase>,
    ): RepairCandidate? {
        val safeExamples = examples
            .take(MAX_SYNTHESIS_EXAMPLES)
            .filter { example ->
                example.inputs[inputKey]?.length?.let { it <= MAX_EXAMPLE_CHARS } == true &&
                    example.expectedOutput.length <= MAX_EXAMPLE_CHARS
            }
        if (safeExamples.isEmpty()) return null

        val system = """
            You are NUA's local Self-Repair Lab synthesizer.
            Return JSON only, matching SovereignProgram exactly:
            {"version":1,"inputKey":"...","instructions":[{"opcode":"TRIM",...}]}

            Allowed opcodes only:
            TRIM, LOWERCASE, UPPERCASE, REPLACE_LITERAL, PREFIX, SUFFIX,
            TAKE, DROP, EXTRACT_BETWEEN, JSON_FIELD, SHA256.

            No loops. No regex. No shell. No files. No network. No processes.
            Use at most $MAX_SYNTHESIZED_OPERATIONS instructions.
            The inputKey must remain exactly the one supplied by the caller.
        """.trimIndent()

        val prompt = buildString {
            appendLine("Input key: $inputKey")
            appendLine("Repair goal: $goal")
            appendLine("Examples:")
            append(json.encodeToString(safeExamples))
        }

        val result = modelMesh.completeValidated(
            contract = TaskContract(
                task = InferenceTaskType.SENSITIVE_LOCAL_TRANSFORM,
                privacySensitivity = PrivacySensitivity.HIGH,
                requiresOffline = true,
            ),
            userPrompt = prompt,
            system = system,
            maxTokens = 700,
            validator = { raw ->
                runCatching {
                    val program = json.decodeFromString<SovereignProgram>(extractJsonPayload(raw))
                    validateSynthesizedProgram(program, inputKey)
                }.getOrDefault(false)
            },
        )
        val text = (result as? ClaudeResult.Success)?.text ?: return null
        val program = runCatching {
            json.decodeFromString<SovereignProgram>(extractJsonPayload(text))
        }.getOrNull() ?: return null
        if (!validateSynthesizedProgram(program, inputKey)) return null

        return RepairCandidate(
            failureSignature = failureSignature,
            kind = RepairKind.TRANSFORM_PROGRAM,
            risk = RepairRisk.LOW,
            reason = "Private on-device model synthesized a bounded transform challenger.",
            reversible = true,
            transformProgram = program,
        )
    }
}
