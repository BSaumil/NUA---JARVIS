package com.nua.assistant.automation.operators

import com.nua.assistant.ai.NuaActionType
import kotlinx.serialization.Serializable

@Serializable
enum class OperatorScheduleType { INTERVAL, DAILY }

@Serializable
data class OperatorSchedule(
    val type: OperatorScheduleType,
    val intervalMinutes: Long? = null,
    val hour: Int? = null,
    val minute: Int? = null,
)

@Serializable
data class SovereignOperatorManifest(
    val id: String,
    val name: String,
    val recipeId: Long,
    val allowedActions: Set<String>,
    val schedule: OperatorSchedule,
    val maxRunsPerDay: Int = 24,
    val cooldownMinutes: Long = 15,
    val enabled: Boolean = true,
    val version: Int = 1,
)

@Serializable
data class OperatorRuntimeState(
    val epochDayUtc: Long = -1,
    val runsToday: Int = 0,
    val lastRunAt: Long? = null,
    val lastOutcome: String? = null,
    val lastDetail: String? = null,
)

data class OperatorValidation(val valid: Boolean, val errors: List<String>)
data class OperatorGateDecision(val allowed: Boolean, val reason: String? = null)

private val ID_PATTERN = Regex("""[a-z0-9][a-z0-9._-]{1,63}""")
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L

fun validateOperator(manifest: SovereignOperatorManifest): OperatorValidation {
    val errors = mutableListOf<String>()
    if (!ID_PATTERN.matches(manifest.id)) errors += "id must be 2-64 lowercase URL-safe characters"
    if (manifest.name.isBlank()) errors += "name must not be blank"
    if (manifest.recipeId <= 0) errors += "recipeId must identify a persisted recipe"
    if (manifest.allowedActions.isEmpty()) errors += "allowedActions must be explicit and non-empty"
    manifest.allowedActions.forEach { name ->
        if (runCatching { NuaActionType.valueOf(name) }.isFailure) errors += "unknown action capability: $name"
    }
    if (manifest.maxRunsPerDay !in 1..96) errors += "maxRunsPerDay must be between 1 and 96"
    if (manifest.cooldownMinutes < 0) errors += "cooldownMinutes must be non-negative"
    when (manifest.schedule.type) {
        OperatorScheduleType.INTERVAL -> {
            val minutes = manifest.schedule.intervalMinutes
            if (minutes == null || minutes < 15) errors += "interval schedule must be at least 15 minutes"
        }
        OperatorScheduleType.DAILY -> {
            val hour = manifest.schedule.hour
            val minute = manifest.schedule.minute
            if (hour == null || hour !in 0..23) errors += "daily hour must be 0..23"
            if (minute == null || minute !in 0..59) errors += "daily minute must be 0..59"
        }
    }
    return OperatorValidation(errors.isEmpty(), errors)
}

fun operatorGate(
    manifest: SovereignOperatorManifest,
    state: OperatorRuntimeState,
    nowMillis: Long,
): OperatorGateDecision {
    if (!manifest.enabled) return OperatorGateDecision(false, "operator disabled")
    val epochDay = nowMillis / MILLIS_PER_DAY
    val runsToday = if (state.epochDayUtc == epochDay) state.runsToday else 0
    if (runsToday >= manifest.maxRunsPerDay) return OperatorGateDecision(false, "daily run limit reached")
    val lastRun = state.lastRunAt
    if (lastRun != null && nowMillis - lastRun < manifest.cooldownMinutes * MILLIS_PER_MINUTE) {
        return OperatorGateDecision(false, "cooldown active")
    }
    return OperatorGateDecision(true)
}

fun isActionEnvelopeSatisfied(
    manifest: SovereignOperatorManifest,
    recipeActions: Set<NuaActionType>,
): Boolean = recipeActions.isNotEmpty() && recipeActions.all { it.name in manifest.allowedActions }
