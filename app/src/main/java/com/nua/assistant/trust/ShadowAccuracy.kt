package com.nua.assistant.trust

import com.nua.assistant.memory.ShadowPredictionEntity

/**
 * How a shadow-mode Contextual Autonomy Contract's predictions compared against what the
 * user actually did with the same proposals — the evidence a shadow contract needs before
 * anyone trusts it enough to go live (TrustRepository.recordShadowPrediction/
 * resolveShadowPrediction's own doc comments). [falsePositives] is the number that
 * actually matters for a go-live decision: a prediction that said "auto-approve" for
 * something the user went on to decline is exactly the failure mode shadow mode exists to
 * catch *before* it can happen live, not after. [falseNegatives] is the opposite, safe
 * direction — the contract would have asked when the user would have said yes anyway — a
 * usability cost, never a safety one.
 */
data class ShadowAccuracy(
    val totalResolved: Int,
    /** Predicted permit, user approved -- the contract would have gotten it right. */
    val truePositives: Int,
    /** Predicted permit, user declined -- the dangerous miss: live, this would have auto-approved something the user didn't want. */
    val falsePositives: Int,
    /** Predicted deny, user approved -- overly conservative, never dangerous. */
    val falseNegatives: Int,
    /** Predicted deny, user declined -- the contract would have correctly asked first. */
    val trueNegatives: Int,
) {
    /** Null when [totalResolved] is zero — not enough signal to report a rate at all, never a fabricated 0% or 100%. */
    val accuracyRate: Double? get() = if (totalResolved == 0) null else (truePositives + trueNegatives).toDouble() / totalResolved
}

/**
 * Pure: aggregates every resolved prediction for one contract's action type into a
 * [ShadowAccuracy]. Unresolved predictions ([ShadowPredictionEntity.actualOutcome] still
 * null — the proposal hasn't been confirmed or declined yet) are excluded entirely, never
 * counted as either outcome.
 */
fun shadowAccuracyFor(predictions: List<ShadowPredictionEntity>): ShadowAccuracy {
    val resolved = predictions.filter { it.actualOutcome != null }
    var truePositives = 0
    var falsePositives = 0
    var falseNegatives = 0
    var trueNegatives = 0
    resolved.forEach { prediction ->
        val approved = prediction.actualOutcome == "APPROVED"
        when {
            prediction.predictedPermit && approved -> truePositives++
            prediction.predictedPermit && !approved -> falsePositives++
            !prediction.predictedPermit && approved -> falseNegatives++
            else -> trueNegatives++
        }
    }
    return ShadowAccuracy(
        totalResolved = resolved.size,
        truePositives = truePositives,
        falsePositives = falsePositives,
        falseNegatives = falseNegatives,
        trueNegatives = trueNegatives,
    )
}
