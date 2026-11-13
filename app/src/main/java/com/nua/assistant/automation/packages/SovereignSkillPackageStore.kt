package com.nua.assistant.automation.packages

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS = "nua_sovereign_skill_packages"
private const val INDEX = "ids"
private const val PREFIX = "package:"

@Singleton
class SovereignSkillPackageStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(id: String): InstalledSkillPackage? =
        prefs.getString(PREFIX + id, null)?.let {
            runCatching { json.decodeFromString<InstalledSkillPackage>(it) }.getOrNull()
        }

    fun list(): List<InstalledSkillPackage> =
        prefs.getStringSet(INDEX, emptySet()).orEmpty()
            .mapNotNull(::get)
            .sortedBy { it.manifest.name.lowercase() }

    fun put(value: InstalledSkillPackage) {
        val ids = prefs.getStringSet(INDEX, emptySet()).orEmpty().toMutableSet()
        ids += value.manifest.id
        prefs.edit()
            .putString(PREFIX + value.manifest.id, json.encodeToString(value))
            .putStringSet(INDEX, ids)
            .apply()
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        val current = get(id) ?: return false
        put(current.copy(enabled = enabled))
        return true
    }

    fun delete(id: String) {
        val ids = prefs.getStringSet(INDEX, emptySet()).orEmpty().toMutableSet()
        ids -= id
        prefs.edit().remove(PREFIX + id).putStringSet(INDEX, ids).apply()
    }
}
