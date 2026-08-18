package com.nua.assistant.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------
// Brand. These three are deliberately NOT equal partners: orange is NUA's identity and
// carries it everywhere, violet marks intelligence, pink marks an exceptional/active
// state. Reaching for violet or pink as a general-purpose accent flattens that hierarchy
// and is the fastest way to make NUA look like every other purple AI app.
// ---------------------------------------------------------------------------------------

/** NUA Orange — identity. Intelligence, energy, human warmth. */
val NuaOrange = Color(NuaPalette.ORANGE)

/** Neural Violet — intelligence. Accent only. */
val NeuralViolet = Color(NuaPalette.VIOLET)

/** Future Pink — exceptional / active state. Accent only, used most sparingly of the three. */
val FuturePink = Color(NuaPalette.PINK)

// ---------------------------------------------------------------------------------------
// Dark surfaces. Dark isn't a variant here, it's the design — see NuaTheme's doc comment.
// ---------------------------------------------------------------------------------------

val NuaBackground = Color(NuaPalette.BACKGROUND)
val NuaSurface = Color(NuaPalette.SURFACE)
val NuaSurfaceElevated = Color(NuaPalette.SURFACE_ELEVATED)

val NuaTextPrimary = Color(NuaPalette.TEXT_PRIMARY)
val NuaTextSecondary = Color(NuaPalette.TEXT_SECONDARY)

val NuaSuccess = Color(NuaPalette.SUCCESS)
val NuaWarning = Color(NuaPalette.WARNING)
val NuaCritical = Color(NuaPalette.CRITICAL)

/**
 * Hairline border for glass surfaces. Not a spec token — derived from primary text at low
 * alpha so it tracks the palette instead of being a fourth grey to keep in sync.
 */
val NuaBorder = NuaTextPrimary.copy(alpha = 0.08f)

/**
 * The semantic slots Material3's [androidx.compose.material3.ColorScheme] has no home for:
 * a third surface level, the status triad, and the hairline border. Everything that *does*
 * map onto Material3 (primary/secondary/tertiary/background/surface/error) is set there
 * too, so ordinary Material components inherit NUA's palette without reaching for this.
 */
@Immutable
data class NuaColors(
    val brandIdentity: Color = NuaOrange,
    val brandIntelligence: Color = NeuralViolet,
    val brandExceptional: Color = FuturePink,
    val background: Color = NuaBackground,
    val surface: Color = NuaSurface,
    val surfaceElevated: Color = NuaSurfaceElevated,
    val textPrimary: Color = NuaTextPrimary,
    val textSecondary: Color = NuaTextSecondary,
    val success: Color = NuaSuccess,
    val warning: Color = NuaWarning,
    val critical: Color = NuaCritical,
    val border: Color = NuaBorder,
)

/** Static because the palette never changes at runtime — there's exactly one. */
val LocalNuaColors = staticCompositionLocalOf { NuaColors() }
