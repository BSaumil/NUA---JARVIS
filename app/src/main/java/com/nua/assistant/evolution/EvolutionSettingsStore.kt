package com.nua.assistant.evolution

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "nua_evolution_settings"
private const val REPAIR_LEVEL_KEY = "repair_level"

@Singleton
class EvolutionSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun repairLevel(): RepairLevel =
        prefs.getString(REPAIR_LEVEL_KEY, null)
            ?.let { runCatching { RepairLevel.valueOf(it) }.getOrNull() }
            ?: RepairLevel.R2_AUTO_TEST

    fun setRepairLevel(level: RepairLevel) {
        prefs.edit().putString(REPAIR_LEVEL_KEY, level.name).apply()
    }
}
