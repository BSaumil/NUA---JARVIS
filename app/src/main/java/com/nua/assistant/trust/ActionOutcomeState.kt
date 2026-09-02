package com.nua.assistant.trust

/**
 * How definitively an action's result is known — replaces a bare succeeded/failed
 * boolean that let a "no synchronous exception was thrown" result get recorded and
 * displayed identically to "this was confirmed to have happened." See
 * TrustRepository.recordOutcome and ActionOutcomeEntity, and NuaViewModel's
 * confirmPendingSms/Reply/Plan for where these are now produced.
 *
 * Only [ATTEMPTED], [ACCEPTED], [COMPLETED], and [FAILED] are actually produced anywhere
 * in this codebase today. [VERIFIED] and [UNKNOWN] are part of the same vocabulary but
 * have no producer yet — no skill here can independently confirm delivery after the
 * fact, and no code path loses track of an outcome entirely — so they're kept as named,
 * documented, and exhaustively handled everywhere this type is consumed, not wired to
 * anything speculative.
 */
enum class ActionOutcomeState {
    /** Dispatched; no result is known yet. Transient — nothing in this codebase persists this. */
    ATTEMPTED,

    /** The platform API accepted the request with no synchronous error, but the API itself
     *  gives no confirmation the action actually completed. `SmsManager.sendMultipartTextMessage`
     *  and `PendingIntent.send()` are both fire-and-forget across a process boundary — a clean
     *  call here means "handed off," not "delivered." */
    ACCEPTED,

    /** NUA has direct, synchronous evidence the action ran to completion — e.g. every step in a
     *  batch came back success, or a local write it performed itself succeeded. */
    COMPLETED,

    /** Completion was independently confirmed after the fact (e.g. a delivery receipt). No skill
     *  in this codebase can produce this state yet — reserved for when one can. */
    VERIFIED,

    /** Known to have failed — an exception was thrown, the platform explicitly rejected the
     *  request, or every step in a batch failed. */
    FAILED,

    /** The outcome could not be determined at all. Reserved — no code path here loses track of
     *  an outcome; every producer can always distinguish at least accepted/completed from failed. */
    UNKNOWN,
}

/**
 * Whether this state should weigh against the trust score / show as an error in the UI.
 * Only a definite failure counts — ACCEPTED, COMPLETED, and VERIFIED are all "no evidence
 * this went wrong," and ATTEMPTED/UNKNOWN are honesty about uncertainty, not evidence NUA
 * did something wrong.
 */
fun ActionOutcomeState.countsAsFailure(): Boolean = this == ActionOutcomeState.FAILED

/** Whether this state means the real-world side effect actually went out — the set an
 *  idempotency check must treat as "already done, don't repeat." A FAILED or unresolved
 *  (ATTEMPTED/UNKNOWN) prior attempt must never block a retry. */
fun ActionOutcomeState.countsAsCommitted(): Boolean = this == ActionOutcomeState.ACCEPTED ||
    this == ActionOutcomeState.COMPLETED ||
    this == ActionOutcomeState.VERIFIED

/** Short badge label for Settings' action-outcome list (e.g. "OK", "Failed"). */
fun ActionOutcomeState.badgeLabel(): String = when (this) {
    ActionOutcomeState.ATTEMPTED -> "Pending"
    ActionOutcomeState.ACCEPTED -> "Sent*"
    ActionOutcomeState.COMPLETED -> "OK"
    ActionOutcomeState.VERIFIED -> "Verified"
    ActionOutcomeState.FAILED -> "Failed"
    ActionOutcomeState.UNKNOWN -> "Unclear"
}

/** Past-tense clause for Act's outcome list, e.g. "T3 · sent, unconfirmed". */
fun ActionOutcomeState.pastTenseClause(): String = when (this) {
    ActionOutcomeState.ATTEMPTED -> "in progress"
    ActionOutcomeState.ACCEPTED -> "sent, unconfirmed"
    ActionOutcomeState.COMPLETED -> "worked"
    ActionOutcomeState.VERIFIED -> "confirmed"
    ActionOutcomeState.FAILED -> "failed"
    ActionOutcomeState.UNKNOWN -> "unclear"
}
