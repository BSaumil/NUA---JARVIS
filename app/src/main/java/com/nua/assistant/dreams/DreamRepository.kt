package com.nua.assistant.dreams

import com.nua.assistant.memory.DreamDao
import com.nua.assistant.memory.DreamEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DreamRepository @Inject constructor(
    private val dreamDao: DreamDao,
) {
    suspend fun record(category: DreamCategory, text: String) {
        dreamDao.insert(DreamEntity(category = category, text = text))
    }

    /** The oldest dream NUA hasn't surfaced to the user yet, or null if it's caught up. */
    suspend fun nextUnshown(): DreamEntity? = dreamDao.oldestUnshown()

    suspend fun markShown(id: Long) = dreamDao.markShown(id)

    suspend fun recent(limit: Int = 20): List<DreamEntity> = dreamDao.recent(limit)

    fun observeRecent(limit: Int = 20) = dreamDao.observeRecent(limit)
}
