package com.nua.assistant.ui.theme

/**
 * The palette's single source of truth, as plain ARGB longs with no Compose or Android
 * types — so the exact spec values and their contrast ratios can be unit tested on the
 * JVM. `NuaColors.kt` builds its `Color` instances from these; nothing should hard-code a
 * hex literal anywhere else.
 */
object NuaPalette {
    // Brand — not equal partners; see NuaColors.kt.
    const val ORANGE = 0xFFF58C14L
    const val VIOLET = 0xFF8B5CF6L
    const val PINK = 0xFFEC4899L

    // Dark surfaces.
    const val BACKGROUND = 0xFF08090DL
    const val SURFACE = 0xFF11131AL
    const val SURFACE_ELEVATED = 0xFF181B24L

    // Text.
    const val TEXT_PRIMARY = 0xFFF5F7FAL
    const val TEXT_SECONDARY = 0xFF9299A8L

    // Status.
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
