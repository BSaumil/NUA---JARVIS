package com.nua.assistant.security.egress

import com.nua.assistant.memory.MemoryPrivacyLevel
import com.nua.assistant.memory.UserFactEntity

/**
 * A record of one egress decision — what category was requested, whether it was allowed,
 * and why. Deliberately does not carry the payload itself, only its classification and
 * count: this is what a future Flight Recorder entry (the directive's Feature 9) would
 * persist, and a lineage record should never duplicate whole sensitive content into a
 * second store. Returned to the caller today only for logging/testing; nothing persists
 * it yet — see this feature's "explicitly not attempted" note in docs/HISTORY.md.
 */
data class EgressDecision(
    val category: DataCategory,
    val provider: String,
    val purpose: String,
    val itemCountRequested: Int,
    val itemCountPermitted: Int,
) {
    val fullyPermitted: Boolean get() = itemCountPermitted == itemCountRequested
}

/**
 * The single point cloud-bound personal data passes through before it reaches a prompt —
 * the Privacy Capsules / Data Egress Gateway directive (Feature 7). A `null`
 * [PrivacyCapsule] is not "no policy," it's the strictest policy: no [DataCategory.SENSITIVE_FACTS]
 * item ever passes without an explicit, valid capsule authorizing exactly that category at
 * [DisclosureLevel.RAW] — the same fail-closed-on-ambiguity discipline this codebase
 * already applies to trust/autonomy state (see `trust/AutonomyGrant.kt`).
 *
 * Scope of this slice, stated honestly: [filterFacts] is wired into the one real call site
 * that assembles the main chat prompt's fact context
 * (`ui/NuaViewModel.kt`'s `replyConversationally`) — the highest-value existing egress
 * point, since [UserFactEntity.privacyLevel] is the one content classification this
 * codebase already has. Document/vision/conversation-history egress are not yet routed
 * through this gateway; each has its own existing protection today
 * (`security/UntrustedContent.kt`'s inbound firewall, `documents/DocumentRedaction.kt`'s
 * pattern-matched PII redaction) but neither is *capsule-gated* yet. Retrofitting every
 * remaining Claude call site is named as this feature's next slice, not claimed done here.
 */
object DataEgressGateway {

    /**
     * Filters [facts] down to what [capsule] actually authorizes to leave the device.
     * [MemoryPrivacyLevel.STANDARD] facts always pass — that's the classification's own
     * meaning (see `memory/MemoryPrivacyLevel.kt`'s doc comment: the user marks what's
     * sensitive, nothing here re-guesses it). A [MemoryPrivacyLevel.SENSITIVE] fact passes
     * only when [capsule] is non-null, authorizes [DataCategory.SENSITIVE_FACTS] at
     * [DisclosureLevel.RAW] (the only disclosure level this codebase can currently
     * honor for a fact — there is no per-fact redaction/derivation mechanism yet, so any
     * other requested level is treated as "cannot be honored, so refuse" rather than
     * silently downgrading to raw).
     */
    fun filterFacts(
        capsule: PrivacyCapsule?,
        facts: List<UserFactEntity>,
        provider: String = "anthropic-claude",
        purpose: String = "conversational reply",
        nowMillis: Long = System.currentTimeMillis(),
    ): Pair<List<UserFactEntity>, EgressDecision> {
        val sensitiveAuthorized = capsule != null &&
            capsule.disclosureLevel == DisclosureLevel.RAW &&
            capsuleAuthorizes(capsule, DataCategory.SENSITIVE_FACTS, nowMillis)

        val permitted = if (sensitiveAuthorized) facts else facts.filter { it.privacyLevel != MemoryPrivacyLevel.SENSITIVE }
        val decision = EgressDecision(
            category = DataCategory.SENSITIVE_FACTS,
            provider = provider,
            purpose = purpose,
            itemCountRequested = facts.count { it.privacyLevel == MemoryPrivacyLevel.SENSITIVE },
            itemCountPermitted = permitted.count { it.privacyLevel == MemoryPrivacyLevel.SENSITIVE },
        )
        return permitted to decision
    }
}
