package com.nua.assistant.di

import android.content.Context
import androidx.room.Room
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.memory.NuaDatabase
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
        Room.databaseBuilder(context, NuaDatabase::class.java, DATABASE_NAME).build()

    @Provides
    fun provideMemoryDao(database: NuaDatabase): MemoryDao = database.memoryDao()

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
