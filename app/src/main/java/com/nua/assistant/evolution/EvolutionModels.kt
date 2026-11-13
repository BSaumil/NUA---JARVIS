package com.nua.assistant.evolution

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class RepairLevel {
    R0_OBSERVE,
    R1_SUGGEST,
    R2_AUTO_TEST,
    R3_AUTO_PROMOTE_RUNTIME,
    R4_CODE_PROMOTION_REQUIRES_APPROVAL,
}

@Serializable
enum class FailureSource {
    MODEL_MESH,
    UAF,
    RECIPE,
    OPERATOR,
    PARSER,
    STORAGE,
    NETWORK,
    UI,
    UNKNOWN,
}

@Serializable
enum class FailureCategory {
    PROVIDER_FAILURE,
    INVALID_OUTPUT,
    ACTION_EXECUTION,
    PARSER_SHAPE,
    TIMEOUT,
    PERMISSION,
    DATA_CORRUPTION,
    REGRESSION,
    UNKNOWN,
}

@Serializable
enum class FailureSeverity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class FailureObservation(
    val id: String,
    val source: FailureSource,
    val component: String,
    val category: FailureCategory,
    val code: String,
    val signature: String,
    val severity: FailureSeverity,
    val occurredAt: Long,
    val repairable: Boolean = true,
)

@Serializable
data class EvolutionMemoryEntry(
    val signature: String,
    val source: FailureSource,
    val component: String,
    val category: FailureCategory,
    val code: String,
    val severity: FailureSeverity,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val occurrences: Int,
    val lastCandidateId: String? = null,
    val lastOutcome: String? = null,
)

@Serializable
enum class RepairKind {
    RUNTIME_TUNING,
    SPEC_TUNING,
    TRANSFORM_PROGRAM,
    RECIPE_PATCH,
    SKILL_PACKAGE_PATCH,
    SOURCE_PATCH,
}

@Serializable
enum class RepairRisk { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class RepairImpact(
    val newAndroidPermissions: Set<String> = emptySet(),
    val newCapabilities: Set<String> = emptySet(),
    val addsCloudEgress: Boolean = false,
    val addsNetworkAccess: Boolean = false,
    val addsExternalCodeExecution: Boolean = false,
    val irreversibleDataChange: Boolean = false,
    val bypassesUaf: Boolean = false,
    val weakensGuardian: Boolean = false,
) {
    fun hasAuthorityExpansion(): Boolean =
        newAndroidPermissions.isNotEmpty() ||
            newCapabilities.isNotEmpty() ||
            addsCloudEgress ||
            addsNetworkAccess ||
            addsExternalCodeExecution

    fun isForbidden(): Boolean = bypassesUaf || weakensGuardian
}

@Serializable
enum class RuntimeTuningKey {
    MODEL_ROUTING_MIN_SAMPLES,
    MODEL_ROUTING_MIN_SUCCESS_RATE_BPS,
    MODEL_ROUTING_MEMORY_DAYS,
}

@Serializable
data class RuntimeTuningPatch(
    val key: RuntimeTuningKey,
    val intValue: Int,
)

@Serializable
enum class SpecTuningKey {
    PROMPT_SCHEMA_REMINDERS,
    PROMPT_REPAIR_ATTEMPTS,
    RESEARCH_MAX_SUBQUESTIONS,
    RESEARCH_CROSSCHECK_DEPTH,
}

@Serializable
data class SpecTuningPatch(
    val key: SpecTuningKey,
    val intValue: Int,
)

@Serializable
data class RepairCandidate(
    val id: String = UUID.randomUUID().toString(),
    val failureSignature: String,
    val kind: RepairKind,
    val risk: RepairRisk,
    val reason: String,
    val reversible: Boolean,
    val impact: RepairImpact = RepairImpact(),
    val runtimeTunings: List<RuntimeTuningPatch> = emptyList(),
    val specTunings: List<SpecTuningPatch> = emptyList(),
    val transformProgram: SovereignProgram? = null,
    val artifactRef: String? = null,
    val artifactSha256: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class RepairOutcomeRecord(
    val candidateId: String,
    val decision: String,
    val detail: String?,
    val recordedAt: Long = System.currentTimeMillis(),
)
