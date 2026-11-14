package com.nua.assistant.skills.importers

import com.nua.assistant.automation.packages.SkillPackageProvenance
import com.nua.assistant.automation.packages.SkillPackageSource
import com.nua.assistant.automation.packages.SovereignSkillPackageManifest
import com.nua.assistant.automation.packages.validateSkillPackageManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

enum class ExternalSkillFormat { OPENJARVIS, AGENTSKILLS, HERMES, OPENCLAW, GENERIC_JSON }

data class ImportedSkillCandidate(
    val format: ExternalSkillFormat,
    val manifest: SovereignSkillPackageManifest,
    val provenance: SkillPackageProvenance,
    val warnings: List<String>,
)

sealed interface SkillImportResult {
    data class Accepted(val candidate: ImportedSkillCandidate) : SkillImportResult
    data class Refused(val reasons: List<String>) : SkillImportResult
}

private val FORBIDDEN_EXECUTABLE_KEYS = setOf(
    "script", "scripts", "command", "commands", "shell", "exec", "executable",
    "python", "javascript", "node", "binary", "dockerfile", "entrypoint", "runtimecommand",
)

private fun executableFields(root: JsonElement): List<String> {
    val hits = mutableListOf<String>()
    fun visit(element: JsonElement, path: String) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                val next = if (path.isBlank()) key else path + "." + key
                val normalized = key.lowercase().replace("_", "").replace("-", "")
                if (normalized in FORBIDDEN_EXECUTABLE_KEYS) hits += next
                visit(value, next)
            }
            is JsonArray -> element.forEachIndexed { index, value ->
                visit(value, path + "[" + index + "]")
            }
            else -> Unit
        }
    }
    visit(root, "")
    return hits
}

private fun JsonObject.string(vararg names: String): String? =
    names.firstNotNullOfOrNull { name -> (this[name] as? JsonPrimitive)?.contentOrNull }

private fun JsonObject.stringSet(vararg names: String): Set<String> =
    names.firstNotNullOfOrNull { name ->
        when (val value = this[name]) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
            is JsonPrimitive -> value.contentOrNull
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.toSet()
            else -> null
        }
    }.orEmpty()

private fun JsonObject.longList(vararg names: String): List<Long> =
    names.firstNotNullOfOrNull { name ->
        (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }
    }.orEmpty()

private fun normalizeFormat(root: JsonObject): ExternalSkillFormat {
    val format = root.string("format", "schema", "sourceFormat")?.lowercase().orEmpty()
    return when {
        "openjarvis" in format || "open-jarvis" in format -> ExternalSkillFormat.OPENJARVIS
        "agentskills" in format || "agent-skills" in format -> ExternalSkillFormat.AGENTSKILLS
        "hermes" in format -> ExternalSkillFormat.HERMES
        "openclaw" in format -> ExternalSkillFormat.OPENCLAW
        else -> ExternalSkillFormat.GENERIC_JSON
    }
}

class ExternalSkillImporter(
    private val json: Json,
) {
    fun importJson(
        raw: String,
        sourceUri: String,
        sourceCommit: String,
        signatureVerified: Boolean,
    ): SkillImportResult {
        val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: return SkillImportResult.Refused(listOf("manifest is not valid JSON"))
        val root = parsed as? JsonObject
            ?: return SkillImportResult.Refused(listOf("skill manifest must be a JSON object"))

        val executable = executableFields(root)
        if (executable.isNotEmpty()) {
            return SkillImportResult.Refused(
                listOf("import contains executable-code fields: " + executable.sorted().joinToString()),
            )
        }

        val body = (root["manifest"] as? JsonObject) ?: root
        val id = body.string("id", "slug", "skillId")?.lowercase()
            ?: return SkillImportResult.Refused(listOf("skill id is required"))
        val manifest = SovereignSkillPackageManifest(
            id = id,
            version = body.string("version") ?: "0.0.0",
            name = body.string("name", "title") ?: id,
            description = body.string("description", "summary") ?: "Imported declarative skill",
            author = body.string("author", "publisher", "vendor") ?: "unknown",
            declaredActions = body.stringSet("declaredActions", "actions", "capabilities"),
            recipeIds = body.longList("recipeIds", "recipes"),
            dependencies = body.stringSet("dependencies", "requires"),
        )

        val errors = validateSkillPackageManifest(manifest).errors.toMutableList()
        if (sourceUri.isBlank()) errors += "immutable source URI is required"
        if (sourceCommit.isBlank()) errors += "source commit/digest is required"
        if (!signatureVerified) errors += "imported skill provenance has not been verified"
        if (errors.isNotEmpty()) return SkillImportResult.Refused(errors.distinct())

        val warnings = mutableListOf<String>()
        if (root["permissions"] != null) warnings += "external permissions are ignored; NUA derives authority from declaredActions"
        if (root["network"] != null) warnings += "external network declarations are ignored; network access must use NUA connectors/UAF"
        if (root["tools"] != null) warnings += "external tools are treated as declarations only"

        return SkillImportResult.Accepted(
            ImportedSkillCandidate(
                format = normalizeFormat(root),
                manifest = manifest,
                provenance = SkillPackageProvenance(
                    source = SkillPackageSource.IMPORTED,
                    sourceUri = sourceUri,
                    sourceCommit = sourceCommit,
                    signatureVerified = true,
                ),
                warnings = warnings,
            ),
        )
    }
}
