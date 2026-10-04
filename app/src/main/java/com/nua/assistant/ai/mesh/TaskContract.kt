package com.nua.assistant.ai.mesh

/**
 * The classes of inference work NUA actually performs today, named per the Sovereign
 * Model Mesh directive (Feature 2) rather than invented fresh. Only the members with a
 * real call site are used by production code right now — the rest exist so a future
 * migration doesn't have to invent a new task taxonomy, the same reasoning
 * `automation/uaf/CapabilityDescriptor.kt`'s `ExecutionAdapterType` already applies to
 * unbound adapter types.
 */
enum class InferenceTaskType {
    INTENT_CLASSIFICATION,
    EMBEDDING_RETRIEVAL,
    SUMMARIZATION,
    ENTITY_EXTRACTION,
    SPEECH_LANGUAGE_DETECTION,
    PLANNING,
    REASONING,
    VISION,
    DOCUMENT_QA,
    SENSITIVE_LOCAL_TRANSFORM,
    LONG_CONTEXT_SYNTHESIS,
}

/** How sensitive the data a task touches is — informs, but doesn't yet enforce, routing (see this feature's "explicitly not attempted"). */
enum class PrivacySensitivity { LOW, MEDIUM, HIGH }

/**
 * What a caller needs from one inference call — the Mesh's routing input.
 * [requiresOffline] restricts [fallbackOrder] to on-device tiers only, honestly returning
 * no candidates when none of those exist yet, rather than silently trying a network call
 * anyway. [requiresFrontierCapability] mirrors the existing, already-real distinction this
 * codebase draws per call site today (`CLAUDE_MODEL_CONVERSATION` for chat/reasoning vs
 * `CLAUDE_MODEL_UTILITY` for classification/extraction) — formalized here instead of
 * re-decided ad hoc at each call site.
 */
data class TaskContract(
    val task: InferenceTaskType,
    val privacySensitivity: PrivacySensitivity,
    val requiresOffline: Boolean = false,
    val requiresFrontierCapability: Boolean = false,
)
