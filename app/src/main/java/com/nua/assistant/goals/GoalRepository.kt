package com.nua.assistant.goals

import com.nua.assistant.memory.GoalDao
import com.nua.assistant.memory.GoalEntity
import com.nua.assistant.memory.GoalObservationEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable goals the user sets ("get my mornings under control") — distinct from a
 * one-shot TaskPlanner request. GoalReviewWorker is what actually generates observations
 * against these; this class just owns CRUD so the ViewModel/Settings don't touch GoalDao
 * directly.
 */
@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao,
) {
    suspend fun addGoal(text: String, type: GoalType = GoalType.GOAL) {
        goalDao.insertGoal(GoalEntity(text = text, type = type))
    }

    suspend fun deactivateGoal(goalId: Long) {
        goalDao.deactivateGoal(goalId)
    }

    suspend fun activeGoals(): List<GoalEntity> = goalDao.getActiveGoals()

    fun observeGoals() = goalDao.observeAllGoals()

    fun observeObservations() = goalDao.observeAllObservations()

    suspend fun latestObservation(goalId: Long): GoalObservationEntity? = goalDao.latestObservation(goalId)

    suspend fun recordObservation(goalId: Long, text: String) {
        goalDao.insertObservation(GoalObservationEntity(goalId = goalId, text = text))
    }
}
