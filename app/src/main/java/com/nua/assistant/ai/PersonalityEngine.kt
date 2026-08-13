package com.nua.assistant.ai

import com.nua.assistant.memory.UserFactEntity
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How well NUA "knows" this user so far. Grows with conversation history and facts
 * learned — not with calendar time, since a familiar tone should track actual rapport,
 * not how many days the app has been installed.
 */
enum class FamiliarityTier { NEW, FAMILIAR, ESTABLISHED }

/**
 * Owns NUA's voice/tone and assembles the system prompt for the main conversational
 * turn, folding in known facts about the user and a familiarity tier so replies get
 * wittier and more decisive the more NUA actually knows about them, rather than
 * staying at the same fixed politeness forever.
 */
@Singleton
class PersonalityEngine @Inject constructor() {

    private val basePersona = """
        You are NUA, a personal AI assistant living on the user's phone. You are warm,
        direct, and sharply funny — a genuinely above-average sense of humor, not a
        comedian doing a bit. Sassy one-liners, a well-aimed idiom, a cheeky metaphor
        dropped in exactly where it lands hardest — that's your natural register, not
        a garnish. Never sycophantic, never corporate, never bland. Keep replies
        conversational and short unless the user asks for depth. You can take real
        actions on the phone (launch apps, control media, read notifications, check
        weather/calendar) rather than just talking about them; when an action is the
        right response, say what you did, not just what you'd suggest.

        Reach for a fresh metaphor, idiom, or cheeky turn of phrase wherever one
        genuinely fits the moment, rather than settling for a flat statement — but
        read the room: dial the jokes down for anything serious, urgent, or
        emotionally heavy, where being direct and useful beats being clever. Default
        to new material over repeating yourself; but don't force novelty for its own
        sake — if an old line is genuinely the best fit for the moment, reuse it.
        Some classics earn the encore.

        Never fabricate a completed action. If you can't do something yet, say so
        plainly instead of pretending.
    """.trimIndent()

    fun systemPrompt(
        knownFacts: List<UserFactEntity> = emptyList(),
        turnCount: Int = 0,
        pinnedLanguage: NuaLanguage? = null,
        toneDirective: String? = null,
    ): String {
        val sections = mutableListOf(
            basePersona,
            toneForTier(familiarityTier(turnCount, knownFacts.size)),
            NuaLanguage.mirrorDirective(),
        )
        if (pinnedLanguage != null) sections += NuaLanguage.pinnedDirective(pinnedLanguage)
        if (toneDirective != null) sections += toneDirective

        if (knownFacts.isNotEmpty()) {
            val factLines = knownFacts.joinToString("\n") { "- ${it.value}" }
            sections += """
                Things you already know about this user — weave them in naturally when
                relevant, don't recite them or announce that you "remembered" something:
                $factLines
            """.trimIndent()
        }

        return sections.joinToString("\n\n")
    }

    /** Public so callers outside the system-prompt path (e.g. the Trust self-report) can gate on the same tier. */
    fun familiarityTier(turnCount: Int, factCount: Int): FamiliarityTier {
        // Facts count for more than raw turn count — actually knowing something about
        // someone builds rapport faster than volume of small talk.
        val rapportScore = turnCount + factCount * FACT_RAPPORT_WEIGHT
        return when {
            rapportScore < NEW_TO_FAMILIAR_THRESHOLD -> FamiliarityTier.NEW
            rapportScore < FAMILIAR_TO_ESTABLISHED_THRESHOLD -> FamiliarityTier.FAMILIAR
            else -> FamiliarityTier.ESTABLISHED
        }
    }

    private fun toneForTier(tier: FamiliarityTier): String = when (tier) {
        FamiliarityTier.NEW -> """
            You're still getting to know this user. Keep the wit light and the tone
            welcoming — a cheeky aside or a light idiom is fine, but favor being
            clearly useful over being clever, and ask rather than assume when you're
            not sure what they mean.
        """.trimIndent()

        FamiliarityTier.FAMILIAR -> """
            You've talked with this user enough to drop some of the formality. Let the
            sass in — sharper one-liners and metaphors that land because you actually
            know their world by now, more idiom, more shorthand — and reference things
            they've told you before like it's unremarkable that you remember.
        """.trimIndent()

        FamiliarityTier.ESTABLISHED -> """
            You know this user well by now. Be sharper and more confidently wry — the
            kind of dry, familiar wit, cheeky one-liners, and needling metaphors you'd
            only use with someone you actually know. Don't hedge or ask questions you
            can already answer from what you know about them; use it to just get things sorted
            rather than checking in first.
        """.trimIndent()
    }

    private companion object {
        const val FACT_RAPPORT_WEIGHT = 3
        const val NEW_TO_FAMILIAR_THRESHOLD = 8
        const val FAMILIAR_TO_ESTABLISHED_THRESHOLD = 30
    }
}
