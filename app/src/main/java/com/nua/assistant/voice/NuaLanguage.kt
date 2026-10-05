package com.nua.assistant.voice

/**
 * Languages NUA understands and can reply in over text, and — where the platform has a
 * voice for it — speak and listen in too. [speechTag] is the BCP-47 tag passed to both
 * SpeechRecognizer (STT) and TextToSpeech (TTS).
 *
 * Haryanvi has no distinct Android speech locale of its own (it isn't a standalone
 * write system the way Hindi is), so it shares Hindi's tag for voice; NUA still
 * understands and replies to Haryanvi as text on its own terms, just without a
 * dedicated voice for it.
 */
enum class NuaLanguage(val displayName: String, val speechTag: String) {
    ENGLISH("English", "en-IN"),
    HINDI("Hindi", "hi-IN"),
    GUJARATI("Gujarati", "gu-IN"),
    MARATHI("Marathi", "mr-IN"),
    ITALIAN("Italian", "it-IT"),
    SPANISH("Spanish", "es-ES"),
    HARYANVI("Haryanvi", "hi-IN"),
    PUNJABI("Punjabi", "pa-IN"),
    VIETNAMESE("Vietnamese", "vi-VN"),
    CHINESE("Chinese (Mandarin)", "zh-CN"),
    ;

    companion object {
        /** Comma-separated display names, for splicing into LLM prompts. */
        fun supportedNames(): String = entries.joinToString(", ") { it.displayName }

        /** Shared instruction for any Claude call that needs to understand/mirror the user's language. */
        fun mirrorDirective(): String = """
            You understand and can reply fluently in: ${supportedNames()}. Reply in
            whichever of these the user just wrote or spoke in — mirror them by
            default, even if your own last reply was in a different language. If a
            message mixes languages or you can't tell, default to English. Haryanvi
            has no script of its own distinct from Hindi in a chat interface — lean on
            Haryanvi vocabulary and phrasing when that's clearly what the user is
            using, but don't force it if plain Hindi reads more natural.
        """.trimIndent()

        /** Overrides mirroring when the user has explicitly pinned a reply language in Settings. */
        fun pinnedDirective(language: NuaLanguage): String =
            "The user has set their preferred language to ${language.displayName} in Settings — " +
                "always reply in ${language.displayName} regardless of what language they write in, " +
                "unless they explicitly ask you to switch for this conversation."

        /**
         * Voice-first depth (directive item 17): mid-conversation code-switching support
         * for voice output. When no language is pinned, Claude's reply text already
         * mirrors whichever language the user just spoke (see [mirrorDirective]) — but
         * [com.nua.assistant.voice.VoiceManager.speak] previously always spoke it with a
         * fixed English voice regardless, since nothing picked a different one per reply.
         * This picks a non-Latin-script [NuaLanguage] purely from which Unicode block
         * [text] actually contains, honest about what it can't do: English/Italian/
         * Spanish/Vietnamese all share the Latin script, so a user switching between
         * *those* languages mid-conversation still can't be voice-disambiguated this way
         * — that's a real, named limitation (would need actual language identification,
         * not script detection, a bigger decision this round doesn't make), not silently
         * pretended away. Returns null (caller should fall back to English) when [text]
         * contains none of the four scripts this can tell apart.
         */
        fun scriptDetectedLanguage(text: String): NuaLanguage? {
            for (char in text) {
                val code = char.code
                when {
                    code in 0x0A80..0x0AFF -> return GUJARATI
                    code in 0x0A00..0x0A7F -> return PUNJABI
                    code in 0x0900..0x097F -> return HINDI
                    code in 0x4E00..0x9FFF -> return CHINESE
                }
            }
            return null
        }
    }
}
