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
    }
}
