package com.nua.assistant.ai

/** Voice-first depth (directive item 17) — user-adjustable personality dials, distinct
 *  from [FamiliarityTier] (which NUA earns automatically) and the per-utterance
 *  prosody-reactive tone ([com.nua.assistant.voice.VoiceProsody]'s `toneDirective`,
 *  which [PersonalityEngine.systemPrompt] already composes separately). These are the
 *  user's own explicit, persisted preference — see `PersonalityPreferenceStore`. */
enum class HumorLevel { LOW, DEFAULT, HIGH }

enum class DirectnessLevel { GENTLE, DEFAULT, BLUNT }

/** The default of every axis means "NUA's base persona, unmodified" — [isDefault]
 *  lets [axesDirective] stay a true no-op (returns null, adds nothing to the prompt)
 *  rather than silently emitting a directive that just re-describes the base persona. */
data class PersonalityAxes(
    val humor: HumorLevel = HumorLevel.DEFAULT,
    val directness: DirectnessLevel = DirectnessLevel.DEFAULT,
) {
    val isDefault: Boolean get() = humor == HumorLevel.DEFAULT && directness == DirectnessLevel.DEFAULT
}

/**
 * Pure: the additive system-prompt directive for [axes], or null when every axis is at
 * its default (so a user who never touches these settings gets exactly the unmodified
 * base persona — this never overrides it, only nudges it).
 */
fun axesDirective(axes: PersonalityAxes): String? {
    if (axes.isDefault) return null
    val lines = mutableListOf<String>()
    when (axes.humor) {
        HumorLevel.LOW -> lines += "The user asked for less humor than your default register — dial the jokes, wordplay, and metaphors back noticeably, while staying warm, not flat."
        HumorLevel.HIGH -> lines += "The user explicitly wants more personality than your default register — lean further into wit, wordplay, and cheeky asides than you normally would."
        HumorLevel.DEFAULT -> Unit
    }
    when (axes.directness) {
        DirectnessLevel.GENTLE -> lines += "The user asked for a gentler delivery than your default — lead with warmth before any critique, correction, or bad news, more than you normally would."
        DirectnessLevel.BLUNT -> lines += "The user asked for more bluntness than your default — skip the cushioning and lead with the direct point, more than you normally would."
        DirectnessLevel.DEFAULT -> Unit
    }
    return lines.joinToString("\n")
}
