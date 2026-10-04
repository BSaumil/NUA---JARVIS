package com.nua.assistant.trust.lineage

import com.nua.assistant.memory.LineageDao
import com.nua.assistant.memory.LineageRecordEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records one [LineageEntry] into the Flight Recorder's chain. An interface (rather than
 * a concrete class, like most singletons in this codebase) specifically so
 * [com.nua.assistant.automation.uaf.WorkflowExecutorTest] can inject a no-op fake without
 * touching Room — the same reason `EmailRepository`/`SmartHomeRepository` are interfaces
 * with a swappable binding.
 */
interface LineageRecorder {
    suspend fun record(entry: LineageEntry)
}

/**
 * The real, persisted recorder. Reads the current chain tail, computes the new entry's
 * hash against it, and appends — see [nextLineageHash] for what the hash actually commits
 * to. Not wrapped in a DB transaction with the read: two concurrent writers could each
 * read the same tail and both append with the same [LineageRecordEntity.previousHash],
 * which [verifyLineageChain] would still detect as a broken chain (two rows can't both be
 * *the* successor and pass verification) rather than silently accept — a real, named
 * limitation of this slice, not a defect hidden from the record.
 */
@Singleton
class RoomLineageRecorder @Inject constructor(
    private val lineageDao: LineageDao,
) : LineageRecorder {
    override suspend fun record(entry: LineageEntry) {
        val previous = lineageDao.mostRecent()
        val hash = nextLineageHash(previous?.hash, entry)
        lineageDao.insert(
            LineageRecordEntity(
                runId = entry.runId,
                stepId = entry.stepId,
                action = entry.action,
                adapterType = entry.adapterType,
                authorizationKind = entry.authorizationKind,
                outcomeState = entry.outcomeState,
                detail = entry.detail,
                hash = hash,
                previousHash = previous?.hash,
                timestampMillis = entry.timestampMillis,
            ),
        )
    }
}
