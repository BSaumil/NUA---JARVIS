package com.nua.assistant.security.egress

/** One class of data a [PrivacyCapsule] can grant or withhold. */
enum class DataCategory {
    STANDARD_FACTS,
    SENSITIVE_FACTS,
    DOCUMENT_CONTENT,
    VISION_CONTENT,
    CONVERSATION_HISTORY,
}

/** How much of a permitted category may actually leave the device. */
enum class DisclosureLevel { RAW, REDACTED, DERIVED_ONLY }

/**
 * Explicit, purpose-bound authorization for one cloud-bound call — the Privacy Capsules
 * directive's policy object. [DataEgressGateway] is the only place this is ever
 * consulted; nothing else in the codebase should branch on privacy policy directly.
 *
 * [recipientProvider] and [purpose] exist so a future Flight Recorder entry can record
 * *what* left the device, *why*, and *to whom* without needing to inspect the payload
 * itself — see the directive's Feature 9. Today only [DataEgressGateway.presetForChat]
 * constructs one; nothing user-facing issues a capsule yet (see this feature's own
 * "explicitly not attempted" note in docs/HISTORY.md).
 */
data class PrivacyCapsule(
    val purpose: String,
    val recipientProvider: String,
    val permittedCategories: Set<DataCategory>,
    val disclosureLevel: DisclosureLevel,
    val networkEgressPermitted: Boolean,
    val reusable: Boolean = true,
    val expiresAtMillis: Long? = null,
)

/**
 * Pure: whether [capsule] currently authorizes [category] to leave the device. Null
 * [expiresAtMillis] never expires (a reusable, standing capsule); a set one must be
 * strictly in the future relative to [nowMillis].
 */
fun capsuleAuthorizes(capsule: PrivacyCapsule, category: DataCategory, nowMillis: Long): Boolean {
    if (!capsule.networkEgressPermitted) return false
    if (category !in capsule.permittedCategories) return false
    val expiry = capsule.expiresAtMillis ?: return true
    return nowMillis < expiry
}
