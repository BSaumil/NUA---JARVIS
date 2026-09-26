package com.nua.assistant.trust

/** How long an auto-approve grant lasts before it needs re-confirming — a review cadence,
 *  not indefinite standing autonomy. Matches the directive's own "conservative defaults"
 *  language for earned autonomy. */
const val AUTONOMY_GRANT_DURATION_MILLIS = 30L * 24 * 60 * 60 * 1000

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * Whether an autonomy grant is actually still in effect right now — enabled AND not past
 * its own expiry. A grant with [autoApproveEnabled] true but no [expiresAt] recorded
 * (shouldn't happen going forward, since every grant is issued with one — but could for a
 * row that predates this field) is treated as expired, not perpetually valid: fail closed
 * on ambiguity, never open.
 */
fun isGrantActive(autoApproveEnabled: Boolean, expiresAt: Long?, now: Long): Boolean =
    autoApproveEnabled && expiresAt != null && expiresAt > now

/** Whole days until [expiresAt], floored at zero — for display only ("expires in 3 days");
 *  [isGrantActive] is the actual active/expired check, not this. */
fun daysUntilExpiry(expiresAt: Long, now: Long): Long =
    ((expiresAt - now) / MILLIS_PER_DAY).coerceAtLeast(0)
