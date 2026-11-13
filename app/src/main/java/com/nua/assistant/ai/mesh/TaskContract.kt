package com.nua.assistant.ai.mesh

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

enum class PrivacySensitivity { LOW, MEDIUM, HIGH }

/**
 * One inference request's routing constraints.
 *
 * HIGH-sensitivity work is local-only by default. A future caller may explicitly set
 * [allowCloud] true after passing NUA's Data Egress Gateway/consent policy, but cloud
 * transmission is no longer an accidental consequence of "local model unavailable".
 */
data class TaskContract(
    val task: InferenceTaskType,
    val privacySensitivity: PrivacySensitivity,
    val requiresOffline: Boolean = false,
    val requiresFrontierCapability: Boolean = false,
    val allowCloud: Boolean = privacySensitivity != PrivacySensitivity.HIGH,
    val profile: TaskProfile = TaskProfile(),
    val hybridMode: HybridMode = HybridMode.LOCAL_FIRST,
)
