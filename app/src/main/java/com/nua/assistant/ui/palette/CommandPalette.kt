package com.nua.assistant.ui.palette

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.text.tokenize
import com.nua.assistant.trust.AutonomyTier
import com.nua.assistant.ui.nav.NuaDestination

/**
 * What a palette entry does when chosen. Deliberately a closed set — the palette is a
 * fast path to things NUA can already do, never a second, less-guarded way to do them.
 */
sealed class PaletteAction {
    /** Jump to a top-level destination. */
    data class Navigate(val destination: NuaDestination) : PaletteAction()

    /**
     * Run a skill. Anything above [AutonomyTier.T2] is *not* executed from here — it's
     * handed to the normal routing path so the existing confirmation dialog and biometric
     * step-up still gate it. The palette must not become a way to fire a T3 action with
     * one tap; see [PaletteEntry.executesImmediately].
     */
    data class RunSkill(val action: NuaActionType, val tier: AutonomyTier) : PaletteAction()

    /** Open a remembered item in Memory. */
    data class OpenMemory(val query: String) : PaletteAction()

    /** Send this straight to chat as an utterance — the catch-all when nothing else matches. */
    data class AskNua(val utterance: String) : PaletteAction()
}

/** One row in the palette. [score] is match strength; higher sorts first. */
data class PaletteEntry(
    val title: String,
    val subtitle: String,
    val action: PaletteAction,
    val score: Int,
) {
    /**
     * Whether choosing this does the thing outright. False for anything that still has to
     * pass a confirmation — the row says "Ask NUA to…" rather than implying a one-tap
     * commit it isn't going to perform.
     */
    val executesImmediately: Boolean
        get() = when (action) {
            is PaletteAction.Navigate, is PaletteAction.OpenMemory -> true
            is PaletteAction.AskNua -> false
            is PaletteAction.RunSkill -> action.tier.ordinal <= AutonomyTier.T2.ordinal
        }
}

/** One searchable capability, kept free of Compose/Android types so ranking stays testable. */
data class PaletteSkill(val action: NuaActionType, val displayName: String, val tier: AutonomyTier)

/** One searchable remembered item. */
data class PaletteMemory(val text: String, val kind: String)

private const val DEFAULT_LIMIT = 12

// Exact prefix beats a word match beats a loose substring, so typing "mem" puts Memory
// first rather than burying it under anything that merely contains "mem".
private const val SCORE_PREFIX = 100
private const val SCORE_WORD = 60
private const val SCORE_SUBSTRING = 30
private const val SCORE_MEMORY_TOKEN = 10

/**
 * Ranks one candidate title against the query. Pure, so the whole palette's behaviour is
 * JVM-testable — the same split used by `NuaPalette`, `NuaState`, `OrbState`, and
 * `SkillManifest`.
 */
internal fun matchScore(title: String, query: String): Int {
    val haystack = title.lowercase()
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return 0
    return when {
        haystack.startsWith(needle) -> SCORE_PREFIX
        // Same tokenizer as the memory path, so a word boundary means the same thing on
        // both. Splitting on ' ' alone treated "smart-home" as one word and demoted
        // "home" to a substring match.
        tokenize(haystack).any { it.startsWith(needle) } -> SCORE_WORD
        haystack.contains(needle) -> SCORE_SUBSTRING
        else -> 0
    }
}

/**
 * Builds the palette for a query (`#37`). One surface over destinations, capabilities, and
 * what NUA remembers.
 *
 * An empty query returns the destinations only — a palette that opens showing every skill
 * and every memory is a wall of text, not a shortcut. "Ask NUA" is always appended last
 * for a non-empty query, so the palette never dead-ends: if nothing matched, the thing
 * you typed is still a question you can ask.
 */
fun buildPalette(
    query: String,
    skills: List<PaletteSkill>,
    memories: List<PaletteMemory>,
    limit: Int = DEFAULT_LIMIT,
): List<PaletteEntry> {
    // A palette with no room for rows is an empty palette, not a crash: `take(limit - 1)`
    // below would throw on a non-positive limit.
    if (limit <= 0) return emptyList()

    val trimmed = query.trim()

    if (trimmed.isEmpty()) {
        return NuaDestination.entries.map { destination ->
            PaletteEntry(
                title = destination.label,
                subtitle = destination.contentDescription.substringAfter("— ", destination.contentDescription),
                action = PaletteAction.Navigate(destination),
                score = SCORE_PREFIX,
            )
        }.take(limit)
    }

    val destinationHits = NuaDestination.entries.mapNotNull { destination ->
        val score = matchScore(destination.label, trimmed)
        if (score == 0) return@mapNotNull null
        PaletteEntry(
            title = "Go to ${destination.label}",
            subtitle = destination.contentDescription.substringAfter("— ", destination.contentDescription),
            action = PaletteAction.Navigate(destination),
            score = score,
        )
    }

    val skillHits = skills.mapNotNull { skill ->
        val score = matchScore(skill.displayName, trimmed)
        if (score == 0) return@mapNotNull null
        val gated = skill.tier.ordinal > AutonomyTier.T2.ordinal
        PaletteEntry(
            // A gated skill is phrased as a request, not a command, because choosing it
            // opens the normal confirmation rather than doing the thing.
            title = if (gated) "Ask NUA to ${skill.displayName.replaceFirstChar { it.lowercase() }}" else skill.displayName,
            subtitle = skill.tier.label,
            action = PaletteAction.RunSkill(skill.action, skill.tier),
            score = score,
        )
    }

    // tokenize() already returns a distinct Set on both sides, so the score measures "how
    // much of what you typed is in this memory" rather than how often the memory repeats
    // itself — counting raw occurrences let "coffee coffee coffee" outrank a memory that
    // actually answers the query.
    val queryTokens = tokenize(trimmed)
    val memoryHits = memories.distinctBy { it.text }.mapNotNull { memory ->
        val memoryTokens = tokenize(memory.text)
        val overlap = queryTokens.count { it in memoryTokens }
        if (overlap == 0) return@mapNotNull null
        PaletteEntry(
            title = memory.text,
            subtitle = memory.kind,
            action = PaletteAction.OpenMemory(memory.text),
            score = overlap * SCORE_MEMORY_TOKEN,
        )
    }

    val askEntry = PaletteEntry(
        title = "Ask NUA: \"$trimmed\"",
        subtitle = "Send to chat",
        action = PaletteAction.AskNua(trimmed),
        score = 0,
    )

    return (destinationHits + skillHits + memoryHits)
        .sortedByDescending { it.score }
        .take(limit - 1)
        .plus(askEntry)
}
