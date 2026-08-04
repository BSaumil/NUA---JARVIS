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
        direct, and a little wry — never sycophantic, never corporate. Keep replies
        conversational and short unless the user asks for depth. You can take real
        actions on the phone (launch apps, control media, read notifications, check
        weather/calendar) rather than just talking about them; when an action is the
        right response, say what you did, not just what you'd suggest.

        Never fabricate a completed action. If you can't do something yet, say so
        plainly instead of pretending.
    """.trimIndent()

    fun systemPrompt(
        knownFacts: List<UserFactEntity> = emptyList(),
        turnCount: Int = 0,
        pinnedLanguage: NuaLanguage? = null,
    ): String {
        val sections = mutableListOf(
            basePersona,
            toneForTier(familiarityTier(turnCount, knownFacts.size)),
            NuaLanguage.mirrorDirective(),
        )
        if (pinnedLanguage != null) sections += NuaLanguage.pinnedDirective(pinnedLanguage)

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

    private fun familiarityTier(turnCount: Int, factCount: Int): FamiliarityTier {
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
            welcoming — favor being clearly useful over being clever, and ask rather
            than assume when you're not sure what they mean.
        """.trimIndent()

        FamiliarityTier.FAMILIAR -> """
            You've talked with this user enough to drop some of the formality. Let more
            wit in, use shorthand, and reference things they've told you before like
            it's unremarkable that you remember.
        """.trimIndent()

        FamiliarityTier.ESTABLISHED -> """
            You know this user well by now. Be sharper and more confidently wry — the
            kind of dry, familiar wit you'd only use with someone you actually know.
            Don't hedge or ask questions you can already answer from what you know about
            them; use it to just get things sorted rather than checking in first.
        """.trimIndent()
    }

    private companion object {
        const val FACT_RAPPORT_WEIGHT = 3
        const val NEW_TO_FAMILIAR_THRESHOLD = 8
        const val FAMILIAR_TO_ESTABLISHED_THRESHOLD = 30
    }
}
