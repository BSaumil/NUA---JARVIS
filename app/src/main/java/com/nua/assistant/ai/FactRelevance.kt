package com.nua.assistant.ai

import com.nua.assistant.memory.UserFactEntity

private const val MAX_FACTS_IN_PROMPT = 12
private const val RECENCY_BONUS_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Ranks facts by keyword overlap with the current message plus recency, and caps how
 * many go into the system prompt. Dumping the entire user_facts table into every turn
 * is fine at a dozen rows; it stops being fine once someone's been talking to NUA for
 * months. This is lexical (token overlap), not semantic/embedding search — embeddings
 * would need bundling a model on-device, out of proportion for what this solves. Good
 * enough to bound prompt size and favor what's actually relevant to the current turn.
 */
object FactRelevance {

    fun rank(facts: List<UserFactEntity>, currentMessage: String? = null, limit: Int = MAX_FACTS_IN_PROMPT): List<UserFactEntity> {
        if (facts.size <= limit) return facts.sortedByDescending { it.updatedAt }

        val messageTokens = currentMessage?.let { tokenize(it) } ?: emptySet()
        val now = System.currentTimeMillis()

        return facts
            .map { fact ->
                val factTokens = tokenize(fact.value) + tokenize(fact.category)
                val overlap = if (messageTokens.isEmpty()) 0 else factTokens.count { it in messageTokens }
                val isRecent = now - fact.updatedAt < RECENCY_BONUS_WINDOW_MS
                Triple(fact, overlap, isRecent)
            }
            .sortedWith(
                compareByDescending<Triple<UserFactEntity, Int, Boolean>> { it.second }
                    .thenByDescending { it.third }
                    .thenByDescending { it.first.updatedAt },
            )
            .take(limit)
            .map { it.first }
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
}
