package com.nua.assistant.decisions

import com.nua.assistant.memory.DecisionDao
import com.nua.assistant.memory.DecisionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecisionRepository @Inject constructor(
    private val decisionDao: DecisionDao,
) {
    suspend fun record(decision: String, reasoning: String?) {
        decisionDao.insert(DecisionEntity(decision = decision, reasoning = reasoning))
    }

    suspend fun recordOutcome(id: Long, outcome: String) =
        decisionDao.recordOutcome(id, outcome, System.currentTimeMillis())

    suspend fun delete(id: Long) = decisionDao.delete(id)

    fun observeAll() = decisionDao.observeAll()
}
