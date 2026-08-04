package com.nua.assistant.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "nua_language_prefs"
private const val KEY_PINNED_LANGUAGE = "pinned_language"

/**
 * The user's explicitly pinned reply/voice language, set from Settings. Null means
 * "auto" — NUA mirrors whatever language the user's message is in (text chat) or falls
 * back to English (voice, which needs a concrete locale hint up front; see VoiceManager).
 */
@Singleton
class LanguagePreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPinnedLanguage(): NuaLanguage? {
        val name = prefs.getString(KEY_PINNED_LANGUAGE, null) ?: return null
        return runCatching { NuaLanguage.valueOf(name) }.getOrNull()
    }

    fun setPinnedLanguage(language: NuaLanguage?) {
        prefs.edit().putString(KEY_PINNED_LANGUAGE, language?.name).apply()
    }
}
