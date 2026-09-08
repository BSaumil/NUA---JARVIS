package com.nua.assistant.dreams

/** Type-string vocabulary for [DreamSource.type] and the `world_relationships` rows a
 *  recorded Dream writes — matching the RFC's existing `(type, id)` convention
 *  (`docs/WORLD_MODEL_RFC.md`), not a new one. */
object DreamSourceType {
    const val FACT = "FACT"
    const val GOAL = "GOAL"
    const val TRUST_LEDGER = "TRUST_LEDGER"
}

/**
 * One specific fact/goal/ledger entry a Dream actually connected — concrete provenance,
 * not prose claiming a connection happened. Dreams is the World Model's first writer
 * (the RFC's own recommendation): every recorded Dream writes one `world_relationships`
 * row per [DreamSource], `DREAM -[synthesized_from]-> source`.
 */
data class DreamSource(val type: String, val id: Long)

/**
 * The code-level enforcement of "must connect at least two different things" — this used
 * to be prompt-only instruction Claude could (and per the injection-firewall discipline
 * elsewhere in this codebase, should never be trusted to) simply ignore. Two identical
 * sources don't count twice.
 */
fun hasSufficientProvenance(sources: List<DreamSource>): Boolean = sources.distinct().size >= 2

/**
 * Filters Claude's claimed connected-item IDs down to ones that actually exist in what it
 * was given — a hallucinated or stale ID never becomes a stored relationship. Pure: no
 * Android/network/DB, directly unit-testable.
 */
fun validatedDreamSources(
    connectedFactIds: List<Long>,
    connectedGoalIds: List<Long>,
    connectedLedgerIds: List<Long>,
    availableFactIds: Set<Long>,
    availableGoalIds: Set<Long>,
    availableLedgerIds: Set<Long>,
): List<DreamSource> {
    val facts = connectedFactIds.filter { it in availableFactIds }.map { DreamSource(DreamSourceType.FACT, it) }
    val goals = connectedGoalIds.filter { it in availableGoalIds }.map { DreamSource(DreamSourceType.GOAL, it) }
    val ledger = connectedLedgerIds.filter { it in availableLedgerIds }.map { DreamSource(DreamSourceType.TRUST_LEDGER, it) }
    return (facts + goals + ledger).distinct()
}
