package com.nua.assistant.world

import com.nua.assistant.memory.WorldRelationshipDao
import com.nua.assistant.memory.WorldRelationshipEntity
import javax.inject.Inject
import javax.inject.Singleton

private const val ORPHAN_CONFIDENCE = 0f

/**
 * Whether a relationship's stored confidence should survive a check that its endpoint
 * entity still resolves — never as-is; an unresolvable endpoint (the fact/goal/decision it
 * named was deleted) drops confidence to zero rather than deleting the row, so what NUA
 * once believed and why stays auditable, the same reasoning the Trust Ledger already uses
 * for keeping a record of past mistakes instead of erasing them. See the World Model RFC's
 * §8 (`docs/WORLD_MODEL_RFC.md`) for the two options this chose between.
 */
fun confidenceAfterResolutionCheck(currentConfidence: Float, resolved: Boolean): Float =
    if (resolved) currentConfidence else ORPHAN_CONFIDENCE

/** A relationship at zero confidence is an orphaned edge — excluded from active retrieval. */
fun isActiveRelationship(confidence: Float): Boolean = confidence > ORPHAN_CONFIDENCE

/**
 * Owns the additive relationship layer between existing entities (goals, decisions, facts,
 * dreams, documents, ...) — see `docs/WORLD_MODEL_RFC.md`. Deliberately does not resolve
 * `(type, id)` pairs back into real entity objects yet: nothing reads this data yet (the
 * first writer, Dreams, is a follow-up seam — see the RFC's §15), and building read-side
 * resolution machinery nothing calls would be exactly the speculative work the RFC argues
 * against. `relationshipsFor` returns raw rows for now; add resolution when a real reader
 * exists to prove the shape it actually needs.
 */
@Singleton
class WorldModelRepository @Inject constructor(
    private val worldRelationshipDao: WorldRelationshipDao,
) {
    suspend fun record(
        fromType: String,
        fromId: Long,
        relation: String,
        toType: String,
        toId: Long,
        confidence: Float,
        source: String? = null,
    ) {
        worldRelationshipDao.insert(
            WorldRelationshipEntity(
                fromType = fromType,
                fromId = fromId,
                relation = relation,
                toType = toType,
                toId = toId,
                confidence = confidence,
                source = source,
            ),
        )
    }

    /** Every relationship touching (type, id) in either direction, active ones only. */
    suspend fun relationshipsFor(type: String, id: Long): List<WorldRelationshipEntity> =
        (worldRelationshipDao.outgoingFrom(type, id) + worldRelationshipDao.incomingTo(type, id))
            .filter { isActiveRelationship(it.confidence) }
}
