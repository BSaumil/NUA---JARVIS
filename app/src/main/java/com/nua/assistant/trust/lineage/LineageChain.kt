package com.nua.assistant.trust.lineage

import com.nua.assistant.memory.LineageRecordEntity
import java.security.MessageDigest

/**
 * One step's worth of execution lineage — the Verifiable Agent Runtime / Flight Recorder
 * directive (Feature 9). [runId] groups every entry belonging to one agent run (today,
 * one [com.nua.assistant.automation.uaf.ActionPlan.id]); the hash chain itself is global
 * across every entry ever recorded, not scoped per run — see [nextLineageHash].
 */
data class LineageEntry(
    val runId: String,
    val stepId: String,
    val action: String,
    val adapterType: String,
    val authorizationKind: String,
    val outcomeState: String,
    val detail: String?,
    val timestampMillis: Long,
)

/**
 * Pure: the next entry's hash, committing to [previousHash] plus every field of [entry].
 * Because each row's hash depends on the one before it, altering, reordering, or deleting
 * any past row changes every hash computed after it — detectable by
 * [verifyLineageChain] re-walking the table and recomputing. This is a local, append-only
 * tamper-*evidence* mechanism (SHA-256 over the chain), not hardware-backed immutability —
 * never claim the stronger property this doesn't provide.
 */
fun nextLineageHash(previousHash: String?, entry: LineageEntry): String {
    val payload = listOf(
        previousHash.orEmpty(),
        entry.runId,
        entry.stepId,
        entry.action,
        entry.adapterType,
        entry.authorizationKind,
        entry.outcomeState,
        entry.detail.orEmpty(),
        entry.timestampMillis.toString(),
    ).joinToString("|")
    val digestBytes = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
    return digestBytes.joinToString("") { "%02x".format(it) }
}

/**
 * Pure: re-walks [records] (already in insertion order) recomputing each hash from its
 * predecessor and comparing against what's stored — the actual tamper check. Returns true
 * only if every single row's stored [LineageRecordEntity.hash] matches what
 * [nextLineageHash] recomputes from the *previous row's* stored hash and that row's own
 * fields. A single altered field anywhere in the chain, or a deleted row, breaks this for
 * every row after the tampered point, not just the tampered row itself.
 */
fun verifyLineageChain(records: List<LineageRecordEntity>): Boolean {
    var previousHash: String? = null
    for (record in records) {
        val entry = LineageEntry(
            runId = record.runId,
            stepId = record.stepId,
            action = record.action,
            adapterType = record.adapterType,
            authorizationKind = record.authorizationKind,
            outcomeState = record.outcomeState,
            detail = record.detail,
            timestampMillis = record.timestampMillis,
        )
        if (record.previousHash != previousHash) return false
        if (nextLineageHash(previousHash, entry) != record.hash) return false
        previousHash = record.hash
    }
    return true
}
