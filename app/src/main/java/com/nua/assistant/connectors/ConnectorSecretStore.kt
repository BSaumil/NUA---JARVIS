package com.nua.assistant.connectors

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ConnectorSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nua_connector_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun set(connectorId: String, secret: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(connectorId, secret).apply()
    }

    suspend fun get(connectorId: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(connectorId, null)
    }

    suspend fun clear(connectorId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(connectorId).apply()
    }
}
