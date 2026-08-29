package com.nua.assistant.memory

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.nua.assistant.documents.DocumentType
import com.nua.assistant.dreams.DreamCategory
import com.nua.assistant.trust.ActionOutcomeState
import com.nua.assistant.trust.AutonomyTier
import com.nua.assistant.trust.TrustEventType
import kotlinx.coroutines.flow.Flow

enum class MessageRole {
    USER,
    ASSISTANT,
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * A durable fact worth remembering about the user (a preference, a routine, a name).
 * [key] identifies the fact for upsert purposes, e.g. "workout_music_genre".
 */
@Entity(
    tableName = "user_facts",
    indices = [Index(value = ["key"], unique = true)],
)
data class UserFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val category: String,
    /** What kind of memory this is — see MemoryType. Defaults to SEMANTIC for facts that predate this field. */
    val memoryType: MemoryType = MemoryType.SEMANTIC,
    /** Where this came from, e.g. "said in conversation" or "morning briefing pattern" — answers "why do you remember this". */
    val source: String? = null,
    /** How sure NUA is this is still accurate, 0-1. Extracted facts default to fairly confident, not certain. */
    val confidence: Float = 0.9f,
    /** Last time this fact was actually pulled into a conversation — see MemoryDao.touchFactUsage. */
    val lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One Claude API call's token usage, for the cost dashboard (see ai/UsageTracker.kt). */
@Entity(tableName = "usage_logs")
data class UsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(entry: UsageLogEntity)

    @Query("SELECT * FROM usage_logs WHERE timestamp >= :sinceMillis")
    suspend fun since(sinceMillis: Long): List<UsageLogEntity>
}

/** A user-defined location trigger — see geofencing/GeofenceManager.kt. */
@Entity(tableName = "geofences")
data class GeofenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val message: String,
    val notifyOnEnter: Boolean = true,
)

@Dao
interface GeofenceDao {
    @Insert
    suspend fun insert(entity: GeofenceEntity): Long

    @Query("SELECT * FROM geofences")
    suspend fun getAll(): List<GeofenceEntity>

    @Query("SELECT * FROM geofences")
    fun observeAll(): Flow<List<GeofenceEntity>>

    @Query("SELECT * FROM geofences WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): GeofenceEntity?

    @Query("DELETE FROM geofences WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** One recorded time NUA got caught being wrong — see trust/TrustRepository.kt. */
@Entity(tableName = "trust_ledger")
data class TrustLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TrustEventType,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Dao
interface TrustLedgerDao {
    @Insert
    suspend fun insert(entity: TrustLedgerEntity)

    @Query("SELECT * FROM trust_ledger ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<TrustLedgerEntity>

    @Query("SELECT * FROM trust_ledger ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TrustLedgerEntity>>

    @Query("SELECT * FROM trust_ledger WHERE timestamp >= :sinceMillis ORDER BY timestamp ASC")
    suspend fun since(sinceMillis: Long): List<TrustLedgerEntity>
}

/** One logged result of a NuaSkill dispatch or a plan/reply confirmation — the audit trail. */
@Entity(tableName = "action_outcomes")
data class ActionOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val tier: AutonomyTier,
    val summary: String,
    /** How definitively this result is known — see ActionOutcomeState's own doc for why this
     *  isn't a plain succeeded/failed boolean. */
    val outcomeState: ActionOutcomeState,
    /** True when this outcome came from the user declining a proposed reply/plan, not an execution failure. */
    val wasRejection: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

@Dao
interface ActionOutcomeDao {
    @Insert
    suspend fun insert(entity: ActionOutcomeEntity)

    @Query("SELECT * FROM action_outcomes ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ActionOutcomeEntity>

    @Query("SELECT * FROM action_outcomes ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActionOutcomeEntity>>

    @Query("SELECT * FROM action_outcomes")
    suspend fun getAll(): List<ActionOutcomeEntity>
}

/**
 * How many times the user has approved a given action type's proposal (reply/plan
 * confirmations), and whether it's been promoted to auto-approve. See
 * trust/TrustRepository.kt — Settings surfaces a toggle once approvedCount crosses a
 * threshold rather than NUA proposing it mid-conversation.
 */
@Entity(tableName = "autonomy_preferences", indices = [Index(value = ["actionType"], unique = true)])
data class AutonomyPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val approvedCount: Int = 0,
    val autoApproveEnabled: Boolean = false,
)

@Dao
interface AutonomyPreferenceDao {
    @Query("SELECT * FROM autonomy_preferences WHERE actionType = :actionType LIMIT 1")
    suspend fun get(actionType: String): AutonomyPreferenceEntity?

    @Query("SELECT * FROM autonomy_preferences")
    suspend fun getAll(): List<AutonomyPreferenceEntity>

    @Query("SELECT * FROM autonomy_preferences")
    fun observeAll(): Flow<List<AutonomyPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AutonomyPreferenceEntity)

    @Query("UPDATE autonomy_preferences SET autoApproveEnabled = :enabled WHERE actionType = :actionType")
    suspend fun setAutoApprove(actionType: String, enabled: Boolean)
}

/** A durable goal the user has set — see goals/GoalRepository.kt. */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One NUA-generated observation/proposal against a goal — see goals/GoalReviewWorker.kt. */
@Entity(tableName = "goal_observations")
data class GoalObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Dao
interface GoalDao {
    @Insert
    suspend fun insertGoal(goal: GoalEntity): Long

    @Query("SELECT * FROM goals WHERE active = 1 ORDER BY createdAt DESC")
    suspend fun getActiveGoals(): List<GoalEntity>

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun observeAllGoals(): Flow<List<GoalEntity>>

    @Query("UPDATE goals SET active = 0 WHERE id = :goalId")
    suspend fun deactivateGoal(goalId: Long)

    @Insert
    suspend fun insertObservation(observation: GoalObservationEntity)

    @Query("SELECT * FROM goal_observations WHERE goalId = :goalId ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestObservation(goalId: Long): GoalObservationEntity?

    @Query("SELECT * FROM goal_observations ORDER BY timestamp DESC")
    fun observeAllObservations(): Flow<List<GoalObservationEntity>>
}

/** One NUA Dream — a rate-limited, non-obvious insight synthesized across memory. See dreams/DreamSynthesisWorker.kt. */
@Entity(tableName = "dreams")
data class DreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: DreamCategory,
    val text: String,
    val shown: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * One entry in the Decision Journal — a decision the user made, why, and (once it's known)
 * how it turned out. Written at decision time, [outcome] filled in later on reflection.
 */
@Entity(tableName = "decisions")
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val decision: String,
    val reasoning: String? = null,
    val outcome: String? = null,
    val decidedAt: Long = System.currentTimeMillis(),
    val outcomeRecordedAt: Long? = null,
)

@Dao
interface DecisionDao {
    @Insert
    suspend fun insert(decision: DecisionEntity): Long

    @Query("UPDATE decisions SET outcome = :outcome, outcomeRecordedAt = :recordedAt WHERE id = :id")
    suspend fun recordOutcome(id: Long, outcome: String, recordedAt: Long)

    @Query("DELETE FROM decisions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM decisions ORDER BY decidedAt DESC")
    fun observeAll(): Flow<List<DecisionEntity>>
}

/**
 * A subject NUA is watching on the user's behalf — "tell me if this changes." NUA can't
 * take photos on its own (capture is delegated to the system camera app), so a monitor
 * doesn't poll anything by itself; it just records a baseline and, once due, prompts the
 * user for a fresh photo to compare against it. See vision/VisionMonitorWorker.kt.
 */
@Entity(tableName = "vision_monitors")
data class VisionMonitorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val baselineDescription: String,
    val baselineImagePath: String,
    val intervalDays: Int,
    val active: Boolean = true,
    val lastCheckedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface VisionMonitorDao {
    @Insert
    suspend fun insert(monitor: VisionMonitorEntity): Long

    @Query("SELECT * FROM vision_monitors WHERE active = 1 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<VisionMonitorEntity>>

    @Query("SELECT * FROM vision_monitors WHERE active = 1")
    suspend fun getActive(): List<VisionMonitorEntity>

    @Query("UPDATE vision_monitors SET lastCheckedAt = :checkedAt WHERE id = :id")
    suspend fun updateLastChecked(id: Long, checkedAt: Long)

    @Query("DELETE FROM vision_monitors WHERE id = :id")
    suspend fun delete(id: Long)
}

/**
 * A PDF, Word doc, or image the user asked NUA to read — transcribed in full so
 * `DocumentAnalyzer` can answer questions and detect expiry dates from the real text,
 * not a one-line description. See documents/DocumentAnalyzer.kt.
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val type: DocumentType,
    val extractedText: String,
    val summary: String? = null,
    val expiryDate: Long? = null,
    val expiryReminded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface DocumentDao {
    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE expiryDate IS NOT NULL AND expiryReminded = 0")
    suspend fun getWithPendingExpiry(): List<DocumentEntity>

    @Query("UPDATE documents SET expiryReminded = 1 WHERE id = :id")
    suspend fun markExpiryReminded(id: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DreamDao {
    @Insert
    suspend fun insert(dream: DreamEntity): Long

    @Query("SELECT * FROM dreams WHERE shown = 0 ORDER BY timestamp ASC LIMIT 1")
    suspend fun oldestUnshown(): DreamEntity?

    @Query("UPDATE dreams SET shown = 1 WHERE id = :id")
    suspend fun markShown(id: Long)

    @Query("SELECT * FROM dreams ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DreamEntity>

    @Query("SELECT * FROM dreams ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DreamEntity>>
}

@Dao
interface MemoryDao {

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentMessages(limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE role = 'USER'")
    suspend fun countUserMessages(): Int

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countMessages(): Int

    @Query("SELECT * FROM messages ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getOldestMessages(limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<Long>)

    @Query("SELECT * FROM user_facts ORDER BY updatedAt DESC")
    fun observeFacts(): Flow<List<UserFactEntity>>

    @Query("SELECT * FROM user_facts ORDER BY updatedAt DESC")
    suspend fun getAllFacts(): List<UserFactEntity>

    @Query("SELECT COUNT(*) FROM user_facts")
    suspend fun countFacts(): Int

    @Query("SELECT * FROM user_facts WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getFactsByCategory(category: String): List<UserFactEntity>

    @Query("SELECT * FROM user_facts WHERE key = :key LIMIT 1")
    suspend fun getFactByKey(key: String): UserFactEntity?

    @Query("DELETE FROM user_facts WHERE id = :id")
    suspend fun deleteFactById(id: Long)

    @Query("DELETE FROM user_facts WHERE memoryType = :memoryType")
    suspend fun deleteFactsByType(memoryType: MemoryType)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFact(fact: UserFactEntity): Long

    @Query("UPDATE user_facts SET value = :value, category = :category, updatedAt = :updatedAt WHERE key = :key")
    suspend fun updateFact(key: String, value: String, category: String, updatedAt: Long)

    @Query("UPDATE user_facts SET lastUsedAt = :usedAt WHERE key = :key")
    suspend fun touchFactUsage(key: String, usedAt: Long)

    /**
     * Insert a new fact or overwrite the value of an existing one with the same [UserFactEntity.key].
     * Room's REPLACE conflict strategy would delete-and-reinsert, losing [UserFactEntity.createdAt];
     * doing it as a read-then-write instead preserves it. [memoryType]/[source] only apply on first
     * insert — re-classifying an existing fact isn't something callers need yet.
     */
    @Transaction
    suspend fun upsertFact(
        key: String,
        value: String,
        category: String,
        memoryType: MemoryType = MemoryType.SEMANTIC,
        source: String? = null,
    ) {
        val existing = getFactByKey(key)
        val now = System.currentTimeMillis()
        if (existing == null) {
            insertFact(
                UserFactEntity(
                    key = key,
                    value = value,
                    category = category,
                    memoryType = memoryType,
                    source = source,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            updateFact(key = key, value = value, category = category, updatedAt = now)
        }
    }
}

@Database(
    entities = [
        MessageEntity::class, UserFactEntity::class, UsageLogEntity::class, GeofenceEntity::class,
        TrustLedgerEntity::class, ActionOutcomeEntity::class, AutonomyPreferenceEntity::class,
        GoalEntity::class, GoalObservationEntity::class, DreamEntity::class, DecisionEntity::class,
        VisionMonitorEntity::class, DocumentEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class NuaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun usageDao(): UsageDao
    abstract fun geofenceDao(): GeofenceDao
    abstract fun trustLedgerDao(): TrustLedgerDao
    abstract fun actionOutcomeDao(): ActionOutcomeDao
    abstract fun autonomyPreferenceDao(): AutonomyPreferenceDao
    abstract fun goalDao(): GoalDao
    abstract fun dreamDao(): DreamDao
    abstract fun decisionDao(): DecisionDao
    abstract fun visionMonitorDao(): VisionMonitorDao
    abstract fun documentDao(): DocumentDao
}
