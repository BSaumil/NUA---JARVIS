package com.nua.assistant.automation.operators

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS = "nua_sovereign_operators"
private const val INDEX_KEY = "operator_ids"
private const val MANIFEST_PREFIX = "manifest:"
private const val STATE_PREFIX = "state:"
private const val MILLIS_PER_DAY = 86_400_000L

@Singleton
class SovereignOperatorStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(manifest: SovereignOperatorManifest) {
        val validation = validateOperator(manifest)
        require(validation.valid) { validation.errors.joinToString() }
        val ids = prefs.getStringSet(INDEX_KEY, emptySet()).orEmpty().toMutableSet().apply { add(manifest.id) }
        prefs.edit()
            .putString(MANIFEST_PREFIX + manifest.id, json.encodeToString(manifest))
            .putStringSet(INDEX_KEY, ids)
            .apply()
    }

    fun get(id: String): SovereignOperatorManifest? =
        prefs.getString(MANIFEST_PREFIX + id, null)?.let {
            runCatching { json.decodeFromString<SovereignOperatorManifest>(it) }.getOrNull()
        }

    fun list(): List<SovereignOperatorManifest> =
        prefs.getStringSet(INDEX_KEY, emptySet()).orEmpty()
            .mapNotNull(::get)
            .sortedBy { it.name.lowercase() }

    fun delete(id: String) {
        val ids = prefs.getStringSet(INDEX_KEY, emptySet()).orEmpty().toMutableSet().apply { remove(id) }
        prefs.edit()
            .remove(MANIFEST_PREFIX + id)
            .remove(STATE_PREFIX + id)
            .putStringSet(INDEX_KEY, ids)
            .apply()
    }

    fun state(id: String): OperatorRuntimeState =
        prefs.getString(STATE_PREFIX + id, null)?.let {
            runCatching { json.decodeFromString<OperatorRuntimeState>(it) }.getOrNull()
        } ?: OperatorRuntimeState()

    fun recordRun(id: String, atMillis: Long, outcome: String, detail: String? = null) {
        val old = state(id)
        val day = atMillis / MILLIS_PER_DAY
        val count = if (old.epochDayUtc == day) old.runsToday + 1 else 1
        val next = OperatorRuntimeState(
            epochDayUtc = day,
            runsToday = count,
            lastRunAt = atMillis,
            lastOutcome = outcome,
            lastDetail = detail,
        )
        prefs.edit().putString(STATE_PREFIX + id, json.encodeToString(next)).apply()
    }

    fun recordHealth(id: String, outcome: String, detail: String? = null) {
        val old = state(id)
        prefs.edit()
            .putString(STATE_PREFIX + id, json.encodeToString(old.copy(lastOutcome = outcome, lastDetail = detail)))
            .apply()
    }
}
