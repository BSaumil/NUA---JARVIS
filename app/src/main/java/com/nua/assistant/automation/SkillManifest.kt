package com.nua.assistant.automation

/** One parameter a skill accepts. [required] means the sandbox won't call the skill without it. */
data class SkillParameter(val name: String, val required: Boolean = false)

/** Local, no I/O — enough for a content-provider read or an intent launch. */
const val TIMEOUT_LOCAL_MILLIS = 10_000L

/** One network round trip (weather, smart home, email). */
const val TIMEOUT_NETWORK_MILLIS = 20_000L

/** A Claude generation, which is slower and streams (planning, briefing). */
const val TIMEOUT_CLAUDE_MILLIS = 45_000L

/**
 * A skill's declared contract, checked by [SkillSandbox] before the skill ever runs —
 * the "declared input schema, permission, timeout" half of the Agent Sandbox. Risk level
 * isn't duplicated here: it already lives in `trust/AutonomyTier.autonomyTierFor`, keyed
 * by the same `NuaActionType`, and the audit log already lives in `TrustRepository`.
 *
 * [requiredPermissions] lists only genuine hard preconditions — a permission the skill
 * cannot do its job at all without. Permissions that merely *improve* a skill are
 * deliberately left out (e.g. `READ_CONTACTS` for SMS: without it "text Sam" can't
 * resolve a name, but "text 555-0100" still works, so blocking the whole skill on it
 * would break a case that currently succeeds).
 */
data class SkillManifest(
    val parameters: List<SkillParameter> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
    val timeoutMillis: Long = TIMEOUT_LOCAL_MILLIS,
)

/** Pure: which declared-required parameters are absent or blank. Empty means the call may proceed. */
fun missingRequiredParameters(manifest: SkillManifest, parameters: Map<String, String>): List<String> =
    manifest.parameters
        .filter { it.required }
        .map { it.name }
        .filter { parameters[it].isNullOrBlank() }

/**
 * Pure: drops anything the skill didn't declare, so a classifier that invents an extra
 * parameter can't smuggle it into a skill's execution. This is the "no arbitrary tool
 * execution path" rule applied to a tool's *inputs*, not just to which tools exist.
 */
fun filterToDeclaredParameters(manifest: SkillManifest, parameters: Map<String, String>): Map<String, String> {
    val declared = manifest.parameters.mapTo(mutableSetOf()) { it.name }
    return parameters.filterKeys { it in declared }
}

/** Pure: turns an Android permission string into something worth saying out loud. */
fun describePermission(permission: String): String = when (permission.substringAfterLast('.')) {
    "ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION" -> "location"
    "READ_CALENDAR" -> "calendar access"
    "WRITE_CALENDAR" -> "permission to add calendar events"
    "READ_CONTACTS" -> "contacts access"
    "SEND_SMS" -> "permission to send texts"
    "RECORD_AUDIO" -> "microphone access"
    else -> permission.substringAfterLast('.').lowercase().replace('_', ' ')
}
