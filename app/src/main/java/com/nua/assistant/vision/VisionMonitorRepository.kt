package com.nua.assistant.vision

import com.nua.assistant.memory.VisionMonitorDao
import com.nua.assistant.memory.VisionMonitorEntity
import javax.inject.Inject
import javax.inject.Singleton

private const val DAY_MS = 24 * 60 * 60 * 1000L

@Singleton
class VisionMonitorRepository @Inject constructor(
    private val visionMonitorDao: VisionMonitorDao,
) {
    suspend fun start(subject: String, baselineDescription: String, baselineImagePath: String, intervalDays: Int) {
        visionMonitorDao.insert(
            VisionMonitorEntity(
                subject = subject,
                baselineDescription = baselineDescription,
                baselineImagePath = baselineImagePath,
                intervalDays = intervalDays,
            ),
        )
    }

    suspend fun stop(id: Long) = visionMonitorDao.delete(id)

    suspend fun markChecked(id: Long) = visionMonitorDao.updateLastChecked(id, System.currentTimeMillis())

    /** Monitors whose interval has elapsed since they were last checked — due for a reminder. */
    suspend fun due(): List<VisionMonitorEntity> {
        val now = System.currentTimeMillis()
        return visionMonitorDao.getActive().filter { now - it.lastCheckedAt >= it.intervalDays * DAY_MS }
    }

    fun observeActive() = visionMonitorDao.observeActive()
}
