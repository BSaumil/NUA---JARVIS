package com.nua.assistant.dreams

import com.nua.assistant.memory.DreamDao
import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.world.WorldModelRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val DREAM_SOURCE_RELATION = "synthesized_from"
private const val DREAM_SOURCE_ATTRIBUTION = "dream_synthesis"

@Singleton
class DreamRepository @Inject constructor(
    private val dreamDao: DreamDao,
    private val worldModelRepository: WorldModelRepository,
) {
    /**
     * Records the dream, then — for each item it actually connected — one
     * `world_relationships` row `DREAM -[synthesized_from]-> source`. Dreams is the World
     * Model's first writer (`docs/WORLD_MODEL_RFC.md`'s own recommendation); [sources]
     * should already be validated (see [validatedDreamSources]/[hasSufficientProvenance])
     * before this is called — this method itself doesn't re-check the >=2 rule, since a
     * caller that already enforced it shouldn't pay for a redundant check, and a caller
     * that hasn't is a bug at the call site, not something to silently paper over here.
     */
    suspend fun record(category: DreamCategory, text: String, sources: List<DreamSource> = emptyList()): Long {
        val dreamId = dreamDao.insert(DreamEntity(category = category, text = text))
        sources.distinct().forEach { source ->
            worldModelRepository.record(
                fromType = "DREAM",
                fromId = dreamId,
                relation = DREAM_SOURCE_RELATION,
                toType = source.type,
                toId = source.id,
                confidence = 1f,
                source = DREAM_SOURCE_ATTRIBUTION,
            )
        }
        return dreamId
    }

    /** The oldest dream NUA hasn't surfaced to the user yet, or null if it's caught up. */
    suspend fun nextUnshown(): DreamEntity? = dreamDao.oldestUnshown()

    suspend fun markShown(id: Long) = dreamDao.markShown(id)

    suspend fun recent(limit: Int = 20): List<DreamEntity> = dreamDao.recent(limit)

    fun observeRecent(limit: Int = 20) = dreamDao.observeRecent(limit)
}
