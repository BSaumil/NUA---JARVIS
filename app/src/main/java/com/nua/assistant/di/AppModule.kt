package com.nua.assistant.di

import android.content.Context
import androidx.room.Room
import com.nua.assistant.memory.ActionOutcomeDao
import com.nua.assistant.memory.AutonomyPreferenceDao
import com.nua.assistant.memory.GeofenceDao
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.memory.NuaDatabase
import com.nua.assistant.memory.TrustLedgerDao
import com.nua.assistant.memory.UsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

private const val DATABASE_NAME = "nua.db"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNuaDatabase(@ApplicationContext context: Context): NuaDatabase =
        Room.databaseBuilder(context, NuaDatabase::class.java, DATABASE_NAME)
            // No migration path exists yet (pre-release) — destructive is fine until
            // there's a real installed base to preserve across schema changes.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMemoryDao(database: NuaDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideUsageDao(database: NuaDatabase): UsageDao = database.usageDao()

    @Provides
    fun provideGeofenceDao(database: NuaDatabase): GeofenceDao = database.geofenceDao()

    @Provides
    fun provideTrustLedgerDao(database: NuaDatabase): TrustLedgerDao = database.trustLedgerDao()

    @Provides
    fun provideActionOutcomeDao(database: NuaDatabase): ActionOutcomeDao = database.actionOutcomeDao()

    @Provides
    fun provideAutonomyPreferenceDao(database: NuaDatabase): AutonomyPreferenceDao = database.autonomyPreferenceDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
