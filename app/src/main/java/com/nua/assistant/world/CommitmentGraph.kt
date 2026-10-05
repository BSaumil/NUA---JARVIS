package com.nua.assistant.world

/**
 * Temporal World Model 2.0 + CommitmentGraph — see `docs/TEMPORAL_WORLD_MODEL_RFC.md`.
 * Pure logic only: no persistence exists yet for either a commitment or an
 * interval-valid relationship (§6 of that RFC — both need a `NuaDatabase` schema bump,
 * gated on real `Migration`/`MigrationTestHelper` coverage, which needs Robolectric,
 * not yet adopted). This file is the part of the feature that needs no schema at all,
 * built now so it's ready to back real persistence the moment that migration lands.
 */

/** A commitment's derived state — never stored, always computed from [commitmentStateFor]. */
enum class CommitmentState {
    /** Not yet fulfilled, and no due date or the due date hasn't passed. */
    PENDING,

    /** Fulfilled — always wins over every other condition, including a due date that
     *  already passed. Fulfilling something late is still fulfilling it. */
    FULFILLED,

    /** Not fulfilled, has a due date, and that due date has passed. Deliberately not a
     *  richer "BROKEN"/"CANCELLED" distinction — see the RFC's §4 for why. */
    EXPIRED,
}

/**
 * Pure: a commitment's current state given [dueAt] (null = no deadline),
 * [fulfilledAt] (null = not yet fulfilled), and [now].
 */
fun commitmentStateFor(dueAt: Long?, fulfilledAt: Long?, now: Long): CommitmentState = when {
    fulfilledAt != null -> CommitmentState.FULFILLED
    dueAt != null && now >= dueAt -> CommitmentState.EXPIRED
    else -> CommitmentState.PENDING
}

/**
 * Pure: whether a `world_relationships` row with interval bounds [validFrom]/[validUntil]
 * (both null = unbounded on that side, matching every relationship written before these
 * columns existed) is valid at instant [at]. [validUntil] is exclusive — the instant a
 * relationship is superseded is the first instant the new one is valid, not a moment both
 * are.
 */
fun isRelationshipValidAt(validFrom: Long?, validUntil: Long?, at: Long): Boolean {
    if (validFrom != null && at < validFrom) return false
    if (validUntil != null && at >= validUntil) return false
    return true
}
