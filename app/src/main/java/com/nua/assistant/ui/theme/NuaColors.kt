package com.nua.assistant.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------
// Brand — NUA Sovereign (docs/BRAND_NUA_SOVEREIGN.md). These three are deliberately NOT
// equal partners: Burgundy is NUA's identity and carries it everywhere, Violet marks
// intelligence (listening/thinking/reasoning/processing/acting specifically, not a
// generic accent), Deep Wine extends the brand gradient for genuinely exceptional/active
// states. Burgundy and Deep Wine are both *filled-surface* colours — see NuaPalette.kt's
// own doc comment for the contrast math behind that — never a foreground/text colour
// directly against NuaObsidian/NuaGraphite.
// ---------------------------------------------------------------------------------------

/** NUA Burgundy — identity. Calm, powerful, premium. A filled-surface colour; see NuaPalette.kt. */
val NuaBurgundy = Color(NuaPalette.BURGUNDY)

/** Intelligence Violet — reserved for listening/thinking/reasoning/processing/acting. Accent only. */
val NeuralViolet = Color(NuaPalette.VIOLET)

/** Deep Wine — deep premium surfaces/gradients; the exceptional/active gradient stop. Filled-surface only. */
val NuaDeepWine = Color(NuaPalette.DEEP_WINE)

// ---------------------------------------------------------------------------------------
// Dark surfaces. Dark isn't a variant here, it's the design — see NuaTheme's doc comment.
// ---------------------------------------------------------------------------------------

val NuaBackground = Color(NuaPalette.OBSIDIAN)
val NuaSurface = Color(NuaPalette.GRAPHITE)
val NuaSurfaceElevated = Color(NuaPalette.GRAPHITE_ELEVATED)

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
    val brandIdentity: Color = NuaBurgundy,
    val brandIntelligence: Color = NeuralViolet,
    val brandExceptional: Color = NuaDeepWine,
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
