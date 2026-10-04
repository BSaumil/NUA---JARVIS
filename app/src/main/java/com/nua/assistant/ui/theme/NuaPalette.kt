package com.nua.assistant.ui.theme

/**
 * The palette's single source of truth, as plain ARGB longs with no Compose or Android
 * types — so the exact spec values and their contrast ratios can be unit tested on the
 * JVM. `NuaColors.kt` builds its `Color` instances from these; nothing should hard-code a
 * hex literal anywhere else.
 *
 * NUA Sovereign brand refresh (`docs/BRAND_NUA_SOVEREIGN.md`). [BURGUNDY] and
 * [DEEP_WINE] are a genuinely different kind of brand colour than the [ORANGE] they
 * replace: both are *dark* (confirmed by [contrastRatio] against every dark surface
 * below 1.7 — nowhere near the 3.0 AA-large floor), where Orange was bright. That means
 * they work as **filled surfaces** (buttons, hero cards, the identity wordmark's own
 * canvas) with light text on top — never as foreground text, icon, or thin-stroke colour
 * directly against [OBSIDIAN]/[GRAPHITE]. [VIOLET] is unchanged from the prior palette
 * and keeps carrying that foreground-accent role, exactly as before. See
 * `NuaPaletteTest.kt`'s own contrast assertions for the numbers this reasoning rests on.
 */
object NuaPalette {
    // Brand — NUA Sovereign. Not equal partners; see NuaColors.kt.
    /** Primary identity / key brand emphasis. A filled-surface colour, not a foreground one — see class doc. */
    const val BURGUNDY = 0xFF6B1738L
    /** Deep premium surfaces / gradients. Even darker than [BURGUNDY] — filled-surface only. */
    const val DEEP_WINE = 0xFF3C0B21L
    /** Intelligence Violet. Reserved for listening/thinking/reasoning/processing/acting — never a generic accent. */
    const val VIOLET = 0xFF8B5CF6L

    // Dark surfaces — NUA Sovereign.
    const val OBSIDIAN = 0xFF111113L
    const val GRAPHITE = 0xFF25252AL
    /** A third elevation above [GRAPHITE] — a lightened derivative of it, not an independent brand hue. */
    const val GRAPHITE_ELEVATED = 0xFF2F2F36L

    // Text.
    /** Warm Ivory — doubles as the primary light-contrast token and this app's dark-theme primary text. */
    const val TEXT_PRIMARY = 0xFFF6F0E5L
    const val TEXT_SECONDARY = 0xFF9299A8L

    // Status — deliberately untouched by the brand refresh; see NuaColors.kt's doc comment.
    const val SUCCESS = 0xFF34D399L
    const val WARNING = 0xFFFBBF24L
    const val CRITICAL = 0xFFF87171L
}

/** WCAG 2.1 relative luminance for an opaque ARGB colour. */
internal fun relativeLuminance(argb: Long): Double {
    val channels = listOf(
        ((argb shr 16) and 0xFF) / 255.0,
        ((argb shr 8) and 0xFF) / 255.0,
        (argb and 0xFF) / 255.0,
    ).map { channel ->
        if (channel <= 0.03928) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
}

/**
 * WCAG 2.1 contrast ratio between two opaque colours, from 1.0 (identical) to 21.0
 * (black on white). AA wants 4.5 for body text and 3.0 for large text or UI components;
 * AAA wants 7.0.
 */
fun contrastRatio(foreground: Long, background: Long): Double {
    val a = relativeLuminance(foreground)
    val b = relativeLuminance(background)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}
