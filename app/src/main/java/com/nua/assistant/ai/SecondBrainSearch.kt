package com.nua.assistant.ai

import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.memory.UserFactEntity

private const val DEFAULT_LIMIT = 30

/** One search hit — either a remembered fact or a synthesized dream, ranked against the query. */
sealed class SecondBrainResult {
    abstract val score: Int

    data class FactHit(val fact: UserFactEntity, override val score: Int) : SecondBrainResult()
    data class DreamHit(val dream: DreamEntity, override val score: Int) : SecondBrainResult()
}

/**
 * Natural-language search over everything NUA has remembered or noticed: facts and dreams
 * together, ranked by the same lexical token-overlap approach as [FactRelevance] rather than
 * embeddings — this is a search box, not a new retrieval architecture. A blank query returns
 * nothing rather than dumping the whole memory store.
 */
object SecondBrainSearch {

    fun search(
        query: String,
        facts: List<UserFactEntity>,
        dreams: List<DreamEntity>,
        limit: Int = DEFAULT_LIMIT,
    ): List<SecondBrainResult> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val factHits = facts.mapNotNull { fact ->
            val overlap = (tokenize(fact.value) + tokenize(fact.category)).count { it in queryTokens }
            if (overlap > 0) SecondBrainResult.FactHit(fact, overlap) else null
        }
        val dreamHits = dreams.mapNotNull { dream ->
            val overlap = (tokenize(dream.text) + tokenize(dream.category.name)).count { it in queryTokens }
            if (overlap > 0) SecondBrainResult.DreamHit(dream, overlap) else null
        }

        return (factHits + dreamHits)
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
}
