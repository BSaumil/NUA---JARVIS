package com.nua.assistant.research

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class ResearchRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs: SharedPreferences by lazy {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nua_research_sessions",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(session: ResearchSession) {
        val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        ids += session.id
        prefs.edit()
            .putString("session:" + session.id, json.encodeToString(session))
            .putStringSet("ids", ids)
            .apply()
    }

    fun get(id: String): ResearchSession? =
        prefs.getString("session:$id", null)?.let {
            runCatching { json.decodeFromString<ResearchSession>(it) }.getOrNull()
        }

    fun list(): List<ResearchSession> =
        prefs.getStringSet("ids", emptySet()).orEmpty()
            .mapNotNull(::get)
            .sortedByDescending { it.updatedAtMillis }
}
