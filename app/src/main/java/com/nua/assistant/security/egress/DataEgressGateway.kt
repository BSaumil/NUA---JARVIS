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
 * Scope, stated honestly, as of the personal-test deployment directive's Privacy
 * Capsules completion pass: [filterFacts] remains the only *filtering* decision this
 * gateway makes, because [UserFactEntity.privacyLevel] is the only per-item sensitivity
 * classification this codebase has — there's no analogous per-document or per-photo
 * marker to filter on (`memory/DocumentEntity` carries no privacy level at all), and
 * inventing one is a UI feature, not a wiring change. [recordEgress] is the other half:
 * every remaining cloud-bound personal-data flow (a document's text for summarization/
 * Q&A, a photo for vision analysis, the conversation history sent with every chat turn)
 * now passes through this one gateway and produces a real [EgressDecision] for it,
 * where before each simply reached [com.nua.assistant.ai.ClaudeApiClient] or
 * [com.nua.assistant.ai.mesh.ModelMesh] directly with no decision point at all. Today
 * that decision is unconditionally "permitted" for [DataCategory.DOCUMENT_CONTENT],
 * [DataCategory.VISION_CONTENT], [DataCategory.CONVERSATION_HISTORY], and (as of the
 * Counterfactual Decision Simulator, Feature 4) [DataCategory.DECISION_CONTENT] — sending
 * the document/photo/conversation/decision to the model *is* what those features do, the same way a
 * capsule can't sensibly withhold [DataCategory.SENSITIVE_FACTS] "for chat in general"
 * — but it is no longer a silent, un-auditable bypass: `tools/egress_boundary_audit.py`
 * statically proves every such call site calls through here first, and the resulting
 * [EgressDecision] is exactly what a future Flight Recorder lineage entry or a future
 * per-document sensitivity toggle would need to act on, without re-plumbing the call
 * site again.
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

    /**
     * Records the egress decision for a category with no per-item filter to apply —
     * [DataCategory.DOCUMENT_CONTENT], [DataCategory.VISION_CONTENT], and
     * [DataCategory.CONVERSATION_HISTORY] today. Unlike [filterFacts], this never
     * withholds anything: sending the document/photo/conversation to the model is the
     * feature itself, not an optional disclosure a capsule could sensibly refuse while
     * the feature still works. What this buys over not calling it at all is that the
     * decision is no longer implicit — every one of these calls now produces a real,
     * inspectable [EgressDecision] a Flight Recorder lineage entry (or a future
     * per-item sensitivity control) can act on, and `tools/egress_boundary_audit.py`
     * can statically verify every such call site actually calls through here first.
     */
    fun recordEgress(
        category: DataCategory,
        itemCount: Int,
        provider: String = "anthropic-claude",
        purpose: String,
    ): EgressDecision = EgressDecision(
        category = category,
        provider = provider,
        purpose = purpose,
        itemCountRequested = itemCount,
        itemCountPermitted = itemCount,
    )
}
