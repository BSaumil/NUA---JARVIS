package com.nua.assistant.voice

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME = "nua_owner_voice_profile"
private const val KEY_PROFILE_BYTES = "eagle_profile_base64"

/**
 * Stores the enrolled owner's Eagle speaker profile — a voiceprint, which is biometric
 * data, so it gets the same EncryptedSharedPreferences treatment as the Claude API key
 * (see memory/SecureKeyRepository.kt), not plain prefs.
 */
@Singleton
class OwnerVoiceProfileStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getProfileBytes(): ByteArray? =
        prefs.getString(KEY_PROFILE_BYTES, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun saveProfileBytes(bytes: ByteArray) {
        prefs.edit().putString(KEY_PROFILE_BYTES, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_PROFILE_BYTES).apply()
    }

    fun isEnrolled(): Boolean = getProfileBytes() != null
}
