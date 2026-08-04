package com.nua.assistant.memory

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_FILE_NAME = "nua_secure_prefs"
private const val KEY_CLAUDE_API_KEY = "claude_api_key"

/**
 * Stores the user-supplied Claude API key in EncryptedSharedPreferences (AES256-GCM,
 * key material in the Android Keystore). Nothing sensitive is ever stored in plain
 * SharedPreferences or Room.
 */
@Singleton
class SecureKeyRepository @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_CLAUDE_API_KEY, null)
    }

    suspend fun setApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_CLAUDE_API_KEY, apiKey).apply()
    }

    suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_CLAUDE_API_KEY).apply()
    }

    suspend fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()
}
