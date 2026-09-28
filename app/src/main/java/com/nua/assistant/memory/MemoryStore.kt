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
import com.nua.assistant.goals.GoalType
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
    /** User-set sensitivity, e.g. for a future "exclude from briefings" filter. Defaults to STANDARD. */
    val privacyLevel: MemoryPrivacyLevel = MemoryPrivacyLevel.STANDARD,
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

    @Query("SELECT * FROM trust_ledger WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TrustLedgerEntity?
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
    /** Set only for actions that go through TrustRepository.wasRecentlyExecuted's dedup check
     *  (see trust/IdempotencyKey.kt) — null for outcomes that predate this field or don't need it. */
    val idempotencyKey: String? = null,
    /** Who this action was directed at — a phone number for SMS_SEND today. Null for
     *  action types with no single recipient (GET_WEATHER, OPEN_APP, ...). See
     *  trust/ThreadProvenance.kt for what this enables. */
    val recipient: String? = null,
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

    @Query("SELECT * FROM action_outcomes WHERE idempotencyKey = :key AND timestamp >= :sinceMillis ORDER BY timestamp DESC LIMIT 1")
    suspend fun mostRecentByIdempotencyKey(key: String, sinceMillis: Long): ActionOutcomeEntity?

    @Query("SELECT * FROM action_outcomes WHERE recipient = :recipient AND actionType = :actionType AND timestamp >= :sinceMillis ORDER BY timestamp DESC")
    suspend fun recentByRecipient(recipient: String, actionType: String, sinceMillis: Long): List<ActionOutcomeEntity>

    /** All outcomes for [actionType] since [sinceMillis], regardless of recipient — the
     *  Contextual Autonomy Contracts frequency-cap query (trust/AutonomyContract.kt). */
    @Query("SELECT * FROM action_outcomes WHERE actionType = :actionType AND timestamp >= :sinceMillis ORDER BY timestamp DESC")
    suspend fun recentByActionType(actionType: String, sinceMillis: Long): List<ActionOutcomeEntity>

    /** The [limit] most recent outcomes for [actionType], newest first, regardless of
     *  recipient or time — the drift-detection input for
     *  trust/AutonomyContract.kt's contractShouldSuspend. */
    @Query("SELECT * FROM action_outcomes WHERE actionType = :actionType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun mostRecentByActionType(actionType: String, limit: Int): List<ActionOutcomeEntity>
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
    /** When this grant needs re-confirming — see trust/AutonomyGrant.kt's isGrantActive.
     *  Null whenever autoApproveEnabled is false; always set when it's true. */
    val expiresAt: Long? = null,
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

/**
 * A context-bounded autonomy grant — Feature 5 (Contextual Autonomy Contracts) of the
 * 5-Year Standalone Master Directive. Extends, rather than replaces,
 * [AutonomyPreferenceEntity]'s unscoped 30-day grant: a contract additionally scopes by
 * recipient, caps frequency, and declares a risk ceiling, and can auto-suspend itself on
 * a failure spike (see trust/AutonomyContract.kt's contractShouldSuspend). One active
 * contract per actionType — the same granularity the legacy grant already uses; several
 * simultaneous per-recipient contracts for one action type is a named future extension,
 * not attempted this round. See trust/TrustRepository.kt's contractDecisionFor for how
 * this is evaluated, and trust/AutonomyContract.kt for why each dimension is here.
 */
@Entity(tableName = "autonomy_contracts", indices = [Index(value = ["actionType"], unique = true)])
data class AutonomyContractEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    /** Null = any recipient. Set = this contract only ever permits this exact recipient
     *  (a phone number for SMS_SEND, a notification title for REPLY_TO_NOTIFICATION). */
    val recipient: String? = null,
    /** Null = no explicit ceiling (the action type's own fixed tier always applies, since
     *  every actionType this contract can govern already has one — see
     *  trust/AutonomyTier.kt's autonomyTierFor). Set = void the contract the moment the
     *  action type's fixed tier exceeds it. */
    val riskCeiling: AutonomyTier? = null,
    /** Null = no frequency cap. Both this and [windowMillis] must be set together for a
     *  cap to apply — see trust/AutonomyContract.kt's evaluateContract. */
    val maxPerWindow: Int? = null,
    val windowMillis: Long? = null,
    val expiresAt: Long,
    /** Predict-but-don't-execute — see trust/TrustRepository.kt's contractDecisionFor and
     *  recordShadowPrediction. A shadow contract's decision is still fully computed every
     *  time; it's just never acted on. */
    val shadowMode: Boolean = false,
    /** Cleared to false by drift detection (contractShouldSuspend) on a failure spike, or
     *  by explicit user revocation — never re-activated automatically. */
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface AutonomyContractDao {
    @Query("SELECT * FROM autonomy_contracts WHERE actionType = :actionType LIMIT 1")
    suspend fun get(actionType: String): AutonomyContractEntity?

    @Query("SELECT * FROM autonomy_contracts")
    suspend fun getAll(): List<AutonomyContractEntity>

    @Query("SELECT * FROM autonomy_contracts")
    fun observeAll(): Flow<List<AutonomyContractEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AutonomyContractEntity)

    @Query("UPDATE autonomy_contracts SET active = 0 WHERE actionType = :actionType")
    suspend fun suspendContract(actionType: String)
}

/**
 * One shadow-mode prediction: what a contract would have decided, recorded without ever
 * acting on it, then resolved against what the user actually did with the same proposal
 * — the accuracy signal a shadow contract needs before anyone trusts it enough to go
 * live. Correlated to its proposal by id (see NuaViewModel's pendingShadowPredictionId),
 * not by a time-window guess.
 */
@Entity(tableName = "shadow_predictions")
data class ShadowPredictionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contractId: Long,
    val actionType: String,
    val recipient: String? = null,
    val predictedPermit: Boolean,
    val reason: String,
    /** Null until confirmPending-/dismissPending- resolves it — "APPROVED" or "REJECTED".
     *  No "EDITED" outcome exists yet: the UI has no edit-then-send flow for a pending
     *  proposal to observe, so that feedback channel the directive names is deferred. */
    val actualOutcome: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Dao
interface ShadowPredictionDao {
    @Insert
    suspend fun insert(entity: ShadowPredictionEntity): Long

    @Query("UPDATE shadow_predictions SET actualOutcome = :outcome WHERE id = :id")
    suspend fun resolve(id: Long, outcome: String)

    @Query("SELECT * FROM shadow_predictions WHERE actualOutcome IS NOT NULL ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentResolved(limit: Int): List<ShadowPredictionEntity>
}

/**
 * NUA Recipes (Feature 6) — a natural-language automation description compiled once into
 * a typed, inspectable plan and persisted for repeat manual runs. [stepsJson] holds the
 * compiled steps as a JSON-encoded `List<`[com.nua.assistant.recipes.RecipeStepData]`>`
 * (see recipes/RecipeStepData.kt for why steps, not `PlanStep`, are what's persisted —
 * authorization is never carried over between runs). [description] is kept verbatim
 * alongside the compiled result so re-compiling after a `KeywordIntentMatcher` change (or
 * a future smarter parser) is always possible without asking the user to retype it.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val stepsJson: String,
    /** How many clauses of [description] KeywordIntentMatcher couldn't resolve at compile
     *  time — surfaced so a recipe with gaps is never presented as fully understood. */
    val unresolvedClauseCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface RecipeDao {
    @Insert
    suspend fun insert(entity: RecipeEntity): Long

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    suspend fun getAll(): List<RecipeEntity>

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** One completed run of a recipe — the audit/health record the directive names, built
 *  from the [com.nua.assistant.automation.uaf.PlanRunState] WorkflowExecutor.run returns.
 *  Every step it covers is *also* recorded individually to the Flight Recorder's lineage
 *  chain (Feature 9) by WorkflowExecutor itself — this is the recipe-level rollup on top
 *  of that, not a second copy of the same per-step detail. */
@Entity(tableName = "recipe_runs")
data class RecipeRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val startedAt: Long,
    val completedAt: Long,
    val succeededSteps: Int,
    val failedSteps: Int,
    val awaitingUserSteps: Int,
)

@Dao
interface RecipeRunDao {
    @Insert
    suspend fun insert(entity: RecipeRunEntity)

    @Query("SELECT * FROM recipe_runs WHERE recipeId = :recipeId ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentForRecipe(recipeId: Long, limit: Int): List<RecipeRunEntity>
}

/** A durable goal the user has set — see goals/GoalRepository.kt. */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val active: Boolean = true,
    /** User-chosen at creation, never inferred — see goals/GoalType.kt. Defaults to GOAL
     *  for rows that predate this field. */
    val type: GoalType = GoalType.GOAL,
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

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: Long): GoalEntity?

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
    /** What was actually known/true when deciding — distinct from a hunch. Optional free text. */
    val facts: String? = null,
    /** What genuinely wasn't known at the time, named honestly rather than glossed over. */
    val unknowns: String? = null,
    /** What bounded the choice — time, money, other people's constraints, etc. */
    val constraints: String? = null,
    /** The alternatives actually considered, not just the one picked. */
    val options: String? = null,
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

    @Query("SELECT * FROM decisions ORDER BY decidedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DecisionEntity>
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

/**
 * One typed, attributed, confidence-scored edge between two existing entities — the
 * additive relationship layer the World Model RFC (`docs/WORLD_MODEL_RFC.md`) recommends
 * in place of a graph database or a rewrite of every existing table. [fromType]/[toType]
 * name an existing entity table (e.g. "DECISION", "GOAL", "FACT", "DREAM") and
 * [fromId]/[toId] are that table's own primary key — deliberately not a Room foreign key,
 * since a relationship naming a since-deleted row should be detectable (see
 * [com.nua.assistant.world.isActiveRelationship]) rather than silently cascade-deleted.
 */
@Entity(
    tableName = "world_relationships",
    indices = [Index(value = ["fromType", "fromId"]), Index(value = ["toType", "toId"])],
)
data class WorldRelationshipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromType: String,
    val fromId: Long,
    val relation: String,
    val toType: String,
    val toId: Long,
    /** 0-1, same scale as [UserFactEntity.confidence]. Lowered to 0 rather than the row
     *  being deleted once an endpoint no longer resolves — see the RFC's §8. */
    val confidence: Float,
    /** What produced this edge, e.g. "dream synthesis" — same idea as [UserFactEntity.source]. */
    val source: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface WorldRelationshipDao {
    @Insert
    suspend fun insert(entity: WorldRelationshipEntity): Long

    @Query("SELECT * FROM world_relationships WHERE fromType = :type AND fromId = :id")
    suspend fun outgoingFrom(type: String, id: Long): List<WorldRelationshipEntity>

    @Query("SELECT * FROM world_relationships WHERE toType = :type AND toId = :id")
    suspend fun incomingTo(type: String, id: Long): List<WorldRelationshipEntity>

    @Query("UPDATE world_relationships SET confidence = :confidence WHERE id = :id")
    suspend fun updateConfidence(id: Long, confidence: Float)
}

/**
 * One entry in the Flight Recorder's append-only execution lineage — see
 * `trust/lineage/LineageRecorder.kt`. [hash]/[previousHash] form a single global chain
 * across every row ever inserted (not scoped per [runId]); altering or deleting any past
 * row breaks every hash computed after it, detectably — a local, append-only tamper-
 * evidence mechanism, not hardware-backed immutability (never represented as more than
 * that; see `trust/lineage/LineageChain.kt`'s own doc comment).
 */
@Entity(tableName = "lineage_records")
data class LineageRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val stepId: String,
    val action: String,
    val adapterType: String,
    val authorizationKind: String,
    val outcomeState: String,
    val detail: String?,
    val hash: String,
    val previousHash: String?,
    val timestampMillis: Long = System.currentTimeMillis(),
)

@Dao
interface LineageDao {
    @Insert
    suspend fun insert(entity: LineageRecordEntity)

    @Query("SELECT * FROM lineage_records ORDER BY id DESC LIMIT 1")
    suspend fun mostRecent(): LineageRecordEntity?

    @Query("SELECT * FROM lineage_records WHERE runId = :runId ORDER BY id ASC")
    suspend fun forRun(runId: String): List<LineageRecordEntity>

    @Query("SELECT * FROM lineage_records ORDER BY id ASC")
    suspend fun all(): List<LineageRecordEntity>
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

    @Query("SELECT * FROM user_facts WHERE id = :id LIMIT 1")
    suspend fun getFactById(id: Long): UserFactEntity?

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

    @Query("UPDATE user_facts SET privacyLevel = :privacyLevel WHERE id = :id")
    suspend fun updatePrivacyLevel(id: Long, privacyLevel: MemoryPrivacyLevel)

    /** User-initiated correction of a fact NUA got wrong, keyed by [id] (not [UserFactEntity.key],
     * which callers editing from Settings don't have reason to know). Resets confidence to 1.0 —
     * a fact the user just typed themselves is as certain as memory gets. */
    @Query("UPDATE user_facts SET value = :value, confidence = 1.0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun correctFact(id: Long, value: String, updatedAt: Long)

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
        VisionMonitorEntity::class, DocumentEntity::class, WorldRelationshipEntity::class,
        LineageRecordEntity::class, AutonomyContractEntity::class, ShadowPredictionEntity::class,
        RecipeEntity::class, RecipeRunEntity::class,
    ],
    version = 20,
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
    abstract fun worldRelationshipDao(): WorldRelationshipDao
    abstract fun lineageDao(): LineageDao
    abstract fun autonomyContractDao(): AutonomyContractDao
    abstract fun shadowPredictionDao(): ShadowPredictionDao
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeRunDao(): RecipeRunDao
}
