package com.nua.assistant.world

import com.nua.assistant.memory.GoalDao
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.memory.TrustLedgerDao
import com.nua.assistant.memory.WorldRelationshipDao
import com.nua.assistant.memory.WorldRelationshipEntity
import javax.inject.Inject
import javax.inject.Singleton

private const val ORPHAN_CONFIDENCE = 0f
private const val TYPE_FACT = "FACT"
private const val TYPE_GOAL = "GOAL"
private const val TYPE_TRUST_LEDGER = "TRUST_LEDGER"

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
 * Which (type, id) on [relationship] is "the other side" from the (type, id) a caller
 * queried with — a relationship row has a direction (`fromType`/`fromId` vs `toType`/
 * `toId`), but "what's this connected to" doesn't care which side was queried. Pure —
 * directly testable without touching either DAO.
 */
fun otherSideOf(relationship: WorldRelationshipEntity, type: String, id: Long): Pair<String, Long> =
    if (relationship.fromType == type && relationship.fromId == id) {
        relationship.toType to relationship.toId
    } else {
        relationship.fromType to relationship.fromId
    }

/**
 * One relationship, resolved: which entity is on the other side, and (when this resolver
 * knows how to read that type) a short human-readable summary of it. [summary] is null
 * for an orphan the stored confidence hasn't caught up to yet, or a type this resolver
 * doesn't know how to read yet — never a fabricated placeholder.
 */
data class ResolvedRelationship(
    val relation: String,
    val otherType: String,
    val otherId: Long,
    val summary: String?,
)

/**
 * Owns the additive relationship layer between existing entities (goals, decisions, facts,
 * dreams, documents, ...) — see `docs/WORLD_MODEL_RFC.md`. Dreams (`dreams/DreamRepository.kt`)
 * is the first writer; `DreamsCard` (Settings) is the first reader, via
 * [relationshipsWithSummaries]. Resolution covers exactly the entity types a real writer
 * produces today — FACT/GOAL/TRUST_LEDGER — not every type the RFC's examples name, per
 * "add resolution when a real reader exists to prove the shape it actually needs," not
 * ahead of one.
 */
@Singleton
class WorldModelRepository @Inject constructor(
    private val worldRelationshipDao: WorldRelationshipDao,
    private val memoryDao: MemoryDao,
    private val goalDao: GoalDao,
    private val trustLedgerDao: TrustLedgerDao,
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

    /** [relationshipsFor], with each connected entity resolved to a short summary. */
    suspend fun relationshipsWithSummaries(type: String, id: Long): List<ResolvedRelationship> =
        relationshipsFor(type, id).map { relationship ->
            val (otherType, otherId) = otherSideOf(relationship, type, id)
            ResolvedRelationship(
                relation = relationship.relation,
                otherType = otherType,
                otherId = otherId,
                summary = resolveSummary(otherType, otherId),
            )
        }

    private suspend fun resolveSummary(type: String, id: Long): String? = when (type) {
        TYPE_FACT -> memoryDao.getFactById(id)?.value
        TYPE_GOAL -> goalDao.getGoalById(id)?.text
        TYPE_TRUST_LEDGER -> trustLedgerDao.getById(id)?.description
        else -> null
    }
}
