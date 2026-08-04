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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

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

    @Query("SELECT * FROM user_facts ORDER BY updatedAt DESC")
    fun observeFacts(): Flow<List<UserFactEntity>>

    @Query("SELECT * FROM user_facts ORDER BY updatedAt DESC")
    suspend fun getAllFacts(): List<UserFactEntity>

    @Query("SELECT * FROM user_facts WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getFactsByCategory(category: String): List<UserFactEntity>

    @Query("SELECT * FROM user_facts WHERE key = :key LIMIT 1")
    suspend fun getFactByKey(key: String): UserFactEntity?

    @Query("DELETE FROM user_facts WHERE id = :id")
    suspend fun deleteFactById(id: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFact(fact: UserFactEntity): Long

    @Query("UPDATE user_facts SET value = :value, category = :category, updatedAt = :updatedAt WHERE key = :key")
    suspend fun updateFact(key: String, value: String, category: String, updatedAt: Long)

    /**
     * Insert a new fact or overwrite the value of an existing one with the same [UserFactEntity.key].
     * Room's REPLACE conflict strategy would delete-and-reinsert, losing [UserFactEntity.createdAt];
     * doing it as a read-then-write instead preserves it.
     */
    @Transaction
    suspend fun upsertFact(key: String, value: String, category: String) {
        val existing = getFactByKey(key)
        val now = System.currentTimeMillis()
        if (existing == null) {
            insertFact(UserFactEntity(key = key, value = value, category = category, createdAt = now, updatedAt = now))
        } else {
            updateFact(key = key, value = value, category = category, updatedAt = now)
        }
    }
}

@Database(
    entities = [MessageEntity::class, UserFactEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NuaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}
