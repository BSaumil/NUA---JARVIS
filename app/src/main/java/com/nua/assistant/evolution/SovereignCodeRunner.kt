package com.nua.assistant.evolution

import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
enum class SandboxLanguage {
    NUA_TRANSFORM_V1,
    JAVASCRIPT,
    PYTHON,
    SHELL,
    WASM,
}

@Serializable
enum class TransformOpcode {
    TRIM,
    LOWERCASE,
    UPPERCASE,
    REPLACE_LITERAL,
    PREFIX,
    SUFFIX,
    TAKE,
    DROP,
    EXTRACT_BETWEEN,
    JSON_FIELD,
    SHA256,
}

@Serializable
data class TransformInstruction(
    val opcode: TransformOpcode,
    val arg1: String? = null,
    val arg2: String? = null,
    val intArg: Int? = null,
)

@Serializable
data class SovereignProgram(
    val version: Int = 1,
    val inputKey: String,
    val instructions: List<TransformInstruction>,
)

data class RunnerResourceLimits(
    val maxInputBytes: Int = 256 * 1024,
    val maxOutputBytes: Int = 256 * 1024,
    val maxOperations: Int = 64,
    val maxWallTimeMillis: Long = 500,
)

data class CodeExecutionRequest(
    val language: SandboxLanguage,
    val program: SovereignProgram?,
    val inputs: Map<String, String>,
    val limits: RunnerResourceLimits = RunnerResourceLimits(),
)

enum class CodeExecutionStatus {
    SUCCEEDED,
    REFUSED,
    UNSUPPORTED_RUNTIME,
    LIMIT_EXCEEDED,
    INVALID_PROGRAM,
}

data class CodeExecutionResult(
    val status: CodeExecutionStatus,
    val output: String? = null,
    val detail: String? = null,
    val executedOperations: Int = 0,
)

@Singleton
class SovereignCodeRunner @Inject constructor(
    private val json: Json,
) {
    fun run(request: CodeExecutionRequest): CodeExecutionResult {
        if (request.language != SandboxLanguage.NUA_TRANSFORM_V1) {
            return CodeExecutionResult(
                CodeExecutionStatus.UNSUPPORTED_RUNTIME,
                detail = "${request.language} is not enabled inside the Android runtime sandbox.",
            )
        }
        val program = request.program
            ?: return CodeExecutionResult(CodeExecutionStatus.INVALID_PROGRAM, detail = "program required")
        if (program.version != 1) {
            return CodeExecutionResult(CodeExecutionStatus.INVALID_PROGRAM, detail = "unsupported program version")
        }
        if (program.instructions.size > request.limits.maxOperations.coerceIn(1, 64)) {
            return CodeExecutionResult(CodeExecutionStatus.LIMIT_EXCEEDED, detail = "operation budget exceeded")
        }

        val input = request.inputs[program.inputKey]
            ?: return CodeExecutionResult(CodeExecutionStatus.INVALID_PROGRAM, detail = "missing input")
        if (input.toByteArray().size > request.limits.maxInputBytes.coerceIn(1, 256 * 1024)) {
            return CodeExecutionResult(CodeExecutionStatus.LIMIT_EXCEEDED, detail = "input budget exceeded")
        }

        val started = System.nanoTime()
        var value = input
        var executed = 0
        for (instruction in program.instructions) {
            val transformed = runCatching { applyInstruction(value, instruction) }
                .getOrElse {
                    return CodeExecutionResult(
                        CodeExecutionStatus.INVALID_PROGRAM,
                        detail = it.message ?: "instruction failed",
                        executedOperations = executed,
                    )
                }
            value = transformed
            executed++

            if (value.toByteArray().size > request.limits.maxOutputBytes.coerceIn(1, 256 * 1024)) {
                return CodeExecutionResult(
                    CodeExecutionStatus.LIMIT_EXCEEDED,
                    detail = "output budget exceeded",
                    executedOperations = executed,
                )
            }
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000L
            if (elapsedMillis > request.limits.maxWallTimeMillis.coerceIn(1, 1_000)) {
                return CodeExecutionResult(
                    CodeExecutionStatus.LIMIT_EXCEEDED,
                    detail = "wall-time budget exceeded",
                    executedOperations = executed,
                )
            }
        }

        return CodeExecutionResult(
            status = CodeExecutionStatus.SUCCEEDED,
            output = value,
            executedOperations = executed,
        )
    }

    private fun applyInstruction(value: String, instruction: TransformInstruction): String =
        when (instruction.opcode) {
            TransformOpcode.TRIM -> value.trim()
            TransformOpcode.LOWERCASE -> value.lowercase(Locale.ROOT)
            TransformOpcode.UPPERCASE -> value.uppercase(Locale.ROOT)
            TransformOpcode.REPLACE_LITERAL -> {
                val from = requireNotNull(instruction.arg1) { "REPLACE_LITERAL requires arg1" }
                val to = requireNotNull(instruction.arg2) { "REPLACE_LITERAL requires arg2" }
                require(from.isNotEmpty()) { "REPLACE_LITERAL source must not be empty" }
                require(from.length <= 8_192 && to.length <= 8_192) { "literal too large" }
                value.replace(from, to)
            }
            TransformOpcode.PREFIX -> requireNotNull(instruction.arg1) { "PREFIX requires arg1" } + value
            TransformOpcode.SUFFIX -> value + requireNotNull(instruction.arg1) { "SUFFIX requires arg1" }
            TransformOpcode.TAKE -> value.take(requireNotNull(instruction.intArg) { "TAKE requires intArg" }.coerceAtLeast(0))
            TransformOpcode.DROP -> value.drop(requireNotNull(instruction.intArg) { "DROP requires intArg" }.coerceAtLeast(0))
            TransformOpcode.EXTRACT_BETWEEN -> extractBetween(
                value,
                requireNotNull(instruction.arg1) { "EXTRACT_BETWEEN requires arg1" },
                requireNotNull(instruction.arg2) { "EXTRACT_BETWEEN requires arg2" },
            )
            TransformOpcode.JSON_FIELD -> jsonField(
                value,
                requireNotNull(instruction.arg1) { "JSON_FIELD requires arg1" },
            )
            TransformOpcode.SHA256 -> MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

    private fun extractBetween(value: String, start: String, end: String): String {
        require(start.isNotEmpty() && end.isNotEmpty()) { "markers must not be empty" }
        val startIndex = value.indexOf(start)
        require(startIndex >= 0) { "start marker not found" }
        val from = startIndex + start.length
        val endIndex = value.indexOf(end, from)
        require(endIndex >= from) { "end marker not found" }
        return value.substring(from, endIndex)
    }

    private fun jsonField(value: String, path: String): String {
        require(path.isNotBlank()) { "JSON path required" }
        var current: JsonElement = json.parseToJsonElement(value)
        path.split('.').filter { it.isNotEmpty() }.forEach { part ->
            current = when (current) {
                is JsonObject -> current[part] ?: error("missing JSON field: $part")
                is JsonArray -> current.getOrNull(part.toIntOrNull() ?: error("array index required"))
                    ?: error("JSON array index out of bounds")
                else -> error("cannot descend through primitive JSON value")
            }
        }
        return when (current) {
            is JsonPrimitive -> current.content
            else -> current.toString()
        }
    }
}
