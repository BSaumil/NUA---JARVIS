package com.nua.assistant.trust

import com.nua.assistant.ai.FamiliarityTier
import com.nua.assistant.memory.AutonomyPreferenceEntity
import com.nua.assistant.memory.TrustLedgerEntity

/**
 * Pure: whether this action type has been approved enough times to suggest "Always
 * allow", without already being auto-approved. Extracted from [TrustRepository
 * .autonomySuggestions] — the two-condition filter is small, but it's the sole gate on
 * a Settings suggestion that leads directly into [TrustRepository.setAutoApprove], the
 * flag whose downstream handling ([com.nua.assistant.ui.pendingEffectFor]) had to be
 * fixed after an architecture review found it bypassing biometric step-up. Worth pinning.
 */
fun isAutonomySuggested(preference: AutonomyPreferenceEntity, threshold: Int = 5): Boolean =
    preference.approvedCount >= threshold && !preference.autoApproveEnabled

/**
 * Pure: whether NUA is even allowed to unprompted self-report right now — familiarity
 * tier and rate limit only, no event data. Split from [selfReportMessage] so
 * [TrustRepository.pendingSelfReport] can check this first and skip the ledger query
 * entirely when it's false, exactly as the original inline version did.
 */
fun selfReportEligible(currentTier: FamiliarityTier, lastReportAt: Long, now: Long, minIntervalMillis: Long): Boolean =
    currentTier == FamiliarityTier.ESTABLISHED && now - lastReportAt >= minIntervalMillis

/**
 * Pure: what NUA would say if it self-reported right now, given what's happened since
 * the last one — or null if there's nothing to report. Silent on an empty ledger so this
 * can never become a recurring interruption. Extracted from
 * [TrustRepository.pendingSelfReport], which interleaves this with reading/writing
 * [android.content.SharedPreferences] — not itself JVM-testable, unlike this function.
 */
fun selfReportMessage(eventsSinceLastReport: List<TrustLedgerEntity>): String? {
    if (eventsSinceLastReport.isEmpty()) return null

    val breakdown = eventsSinceLastReport.groupingBy { it.type }.eachCount()
        .entries.joinToString(", ") { (type, count) -> "$count time${if (count == 1) "" else "s"} ${type.label}" }
    val count = eventsSinceLastReport.size
    return "Since we last talked about it, I've gotten $count thing${if (count == 1) "" else "s"} wrong: $breakdown. " +
        "Figured you'd rather hear it from me than notice it yourself."
}
