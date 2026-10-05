package com.nua.assistant.memory

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first real Robolectric test (directive item 13) — proves `DecisionDao`'s generated
 * Room code actually round-trips through a real (Robolectric-shadowed) SQLite database,
 * something no pure-JVM test in this suite can exercise. This is the concrete
 * prerequisite `docs/DATABASE_MIGRATION_POLICY.md` names for writing and verifying a real
 * `Migration` + `MigrationTestHelper` test the next time `NuaDatabase`'s version bumps —
 * that test needs this same Robolectric infrastructure, now proven working.
 *
 * `@Config(application = Application::class)` deliberately runs against a bare
 * [Application], not the real Hilt-annotated `NuaApplication` — this test needs only a
 * [android.content.Context] for Room, not NUA's whole DI graph/WorkManager
 * initialization, which a plain unit test should never have to stand up. Pinned to
 * `sdk = [34]` rather than defaulting to `compileSdk` (35) — a stable, long-supported
 * Robolectric shadow API level, chosen deliberately rather than gambling on day-one
 * shadow availability for the newest API this project targets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DecisionDaoRobolectricTest {

    private lateinit var db: NuaDatabase
    private lateinit var dao: DecisionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NuaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.decisionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `an inserted decision round-trips through real SQLite with its real id`() = runTest {
        val id = dao.insert(DecisionEntity(decision = "pick option A", reasoning = "it was cheaper"))
        val recent = dao.recent(10)
        assertEquals(1, recent.size)
        assertEquals(id, recent[0].id)
        assertEquals("pick option A", recent[0].decision)
        assertEquals("it was cheaper", recent[0].reasoning)
    }

    @Test
    fun `recordOutcome persists both the outcome text and its timestamp together`() = runTest {
        val id = dao.insert(DecisionEntity(decision = "pick option A"))
        dao.recordOutcome(id, outcome = "worked out fine", recordedAt = 42_000L)
        val persisted = dao.recent(10).first()
        assertEquals("worked out fine", persisted.outcome)
        assertEquals(42_000L, persisted.outcomeRecordedAt)
    }

    @Test
    fun `delete actually removes the row from the real database, not just an in-memory cache`() = runTest {
        val id = dao.insert(DecisionEntity(decision = "pick option A"))
        dao.delete(id)
        assertTrue(dao.recent(10).isEmpty())
    }

    @Test
    fun `recent orders newest-first by decidedAt, the real ORDER BY clause, not insertion order`() = runTest {
        val olderId = dao.insert(DecisionEntity(decision = "older", decidedAt = 1_000L))
        val newerId = dao.insert(DecisionEntity(decision = "newer", decidedAt = 2_000L))
        val recent = dao.recent(10)
        assertEquals(listOf(newerId, olderId), recent.map { it.id })
    }
}
