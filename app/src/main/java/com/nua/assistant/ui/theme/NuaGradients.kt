package com.nua.assistant.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The orange→violet NUA gradient. Deliberately **not** a general-purpose decoration: it
 * is reserved for the four places that mean "NUA itself is doing something" —
 *
 *  - the Orb,
 *  - AI activity indicators,
 *  - hero cards,
 *  - the active-intelligence state.
 *
 * Using it on ordinary buttons, dividers, or list rows is what turns a distinctive
 * identity into generic AI-app chrome. If a surface isn't one of the four above, it takes
 * a flat colour from [NuaColors].
 *
 * These builders are functions rather than vals because a [Brush] gradient is resolved
 * against the size of the thing it paints; caching one globally would stretch wrong.
 */
object NuaGradients {

    private val stops = arrayOf(0f to NuaOrange, 1f to NeuralViolet)

    /** Left-to-right — hero cards, wide activity bars. */
    fun horizontal(): Brush = Brush.horizontalGradient(colorStops = stops)

    /** Top-to-bottom — vertical hero surfaces. */
    fun vertical(): Brush = Brush.verticalGradient(colorStops = stops)

    /**
     * Centre-out — the Orb's default fill. [radius] is in pixels; pass the Orb's current
     * animated radius so the gradient breathes with it rather than staying fixed.
     */
    fun radial(radius: Float): Brush = Brush.radialGradient(colorStops = stops, radius = radius)

    /**
     * Sweep — used for the Orb's "thinking" and "acting" states, where energy reads as
     * travelling around the orb rather than sitting still.
     */
    fun sweep(): Brush = Brush.sweepGradient(listOf(NuaOrange, NeuralViolet, NuaOrange))

    /**
     * The exceptional/active variant, reaching for Future Pink at the far end. Reserved
     * for genuinely exceptional states so pink keeps its meaning — see [NuaColors].
     */
    fun exceptional(): Brush = Brush.horizontalGradient(listOf(NuaOrange, NeuralViolet, FuturePink))

    /**
     * A soft-glass wash for card surfaces: the brand tint at very low alpha over an
     * elevated surface, giving depth without the gradient reading as decoration.
     */
    fun glassTint(base: Color = NuaSurfaceElevated): Brush = Brush.verticalGradient(
        listOf(NuaOrange.copy(alpha = 0.06f), base.copy(alpha = 0f)),
    )
}
