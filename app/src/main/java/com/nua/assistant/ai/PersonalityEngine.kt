package com.nua.assistant.ai

import com.nua.assistant.memory.UserFactEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns NUA's voice/tone and assembles the system prompt for the main conversational
 * turn, folding in known facts about the user so replies feel like they come from
 * someone who's been paying attention rather than a stateless chatbot.
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

    fun systemPrompt(knownFacts: List<UserFactEntity> = emptyList()): String {
        if (knownFacts.isEmpty()) return basePersona

        val factLines = knownFacts.joinToString("\n") { "- ${it.value}" }
        return """
            $basePersona

            Things you already know about this user — weave them in naturally when
            relevant, don't recite them or announce that you "remembered" something:
            $factLines
        """.trimIndent()
    }
}
