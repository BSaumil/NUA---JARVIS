package com.nua.assistant.evolution

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS = "nua_evolution_memory"
private const val MEMORY_KEY = "failure_memory"
private const val CANDIDATES_KEY = "repair_candidates"
private const val OUTCOMES_KEY = "repair_outcomes"
private const val MAX_MEMORY_ENTRIES = 256
private const val MAX_CANDIDATES = 128
private const val MAX_OUTCOMES = 256

fun failureFingerprint(
    source: FailureSource,
    component: String,
    category: FailureCategory,
    code: String,
): String {
    val canonical = listOf(source.name, component.trim(), category.name, code.trim()).joinToString("|")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

fun mergeEvolutionMemoryEntry(
    existing: EvolutionMemoryEntry?,
    observation: FailureObservation,
): EvolutionMemoryEntry =
    if (existing == null) {
        EvolutionMemoryEntry(
            signature = observation.signature,
            source = observation.source,
            component = observation.component,
            category = observation.category,
            code = observation.code,
            severity = observation.severity,
            firstSeenAt = observation.occurredAt,
            lastSeenAt = observation.occurredAt,
            occurrences = 1,
        )
    } else {
        existing.copy(
            lastSeenAt = maxOf(existing.lastSeenAt, observation.occurredAt),
            occurrences = existing.occurrences + 1,
            severity = maxOf(existing.severity, observation.severity),
        )
    }

@Singleton
class EvolutionMemoryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun recordFailure(observation: FailureObservation): EvolutionMemoryEntry {
        val entries = memory().toMutableList()
        val index = entries.indexOfFirst { it.signature == observation.signature }
        val merged = mergeEvolutionMemoryEntry(entries.getOrNull(index), observation)
        if (index >= 0) entries[index] = merged else entries += merged
        saveMemory(entries.sortedByDescending { it.lastSeenAt }.take(MAX_MEMORY_ENTRIES))
        return merged
    }

    @Synchronized
    fun recordCandidate(candidate: RepairCandidate) {
        val items = candidates().filterNot { it.id == candidate.id }.toMutableList()
        items += candidate
        prefs.edit().putString(
            CANDIDATES_KEY,
            json.encodeToString(items.sortedByDescending { it.createdAt }.take(MAX_CANDIDATES)),
        ).apply()
        updateMemory(candidate.failureSignature) { it.copy(lastCandidateId = candidate.id) }
    }

    @Synchronized
    fun recordOutcome(record: RepairOutcomeRecord) {
        val items = outcomes().toMutableList()
        items += record
        prefs.edit().putString(
            OUTCOMES_KEY,
            json.encodeToString(items.sortedByDescending { it.recordedAt }.take(MAX_OUTCOMES)),
        ).apply()
        val candidate = candidates().firstOrNull { it.id == record.candidateId }
        if (candidate != null) {
            updateMemory(candidate.failureSignature) { it.copy(lastOutcome = record.decision) }
        }
    }

    fun memory(): List<EvolutionMemoryEntry> =
        prefs.getString(MEMORY_KEY, null)?.let {
            runCatching { json.decodeFromString<List<EvolutionMemoryEntry>>(it) }.getOrNull()
        }.orEmpty()

    fun candidates(): List<RepairCandidate> =
        prefs.getString(CANDIDATES_KEY, null)?.let {
            runCatching { json.decodeFromString<List<RepairCandidate>>(it) }.getOrNull()
        }.orEmpty()

    fun outcomes(): List<RepairOutcomeRecord> =
        prefs.getString(OUTCOMES_KEY, null)?.let {
            runCatching { json.decodeFromString<List<RepairOutcomeRecord>>(it) }.getOrNull()
        }.orEmpty()

    fun candidate(id: String): RepairCandidate? = candidates().firstOrNull { it.id == id }

    @Synchronized
    private fun updateMemory(signature: String, update: (EvolutionMemoryEntry) -> EvolutionMemoryEntry) {
        val entries = memory().toMutableList()
        val index = entries.indexOfFirst { it.signature == signature }
        if (index < 0) return
        entries[index] = update(entries[index])
        saveMemory(entries)
    }

    private fun saveMemory(entries: List<EvolutionMemoryEntry>) {
        prefs.edit().putString(MEMORY_KEY, json.encodeToString(entries)).apply()
    }
}
