package com.nua.assistant.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "nua_personality_prefs"
private const val KEY_HUMOR = "humor_level"
private const val KEY_DIRECTNESS = "directness_level"

/** Persists the user's own [PersonalityAxes] — same simple getter/setter shape as
 *  `voice/LanguagePreferenceStore.kt`. */
@Singleton
class PersonalityPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAxes(): PersonalityAxes = PersonalityAxes(
        humor = runCatching { HumorLevel.valueOf(prefs.getString(KEY_HUMOR, null) ?: "") }.getOrDefault(HumorLevel.DEFAULT),
        directness = runCatching { DirectnessLevel.valueOf(prefs.getString(KEY_DIRECTNESS, null) ?: "") }.getOrDefault(DirectnessLevel.DEFAULT),
    )

    fun setAxes(axes: PersonalityAxes) {
        prefs.edit()
            .putString(KEY_HUMOR, axes.humor.name)
            .putString(KEY_DIRECTNESS, axes.directness.name)
            .apply()
    }
}
