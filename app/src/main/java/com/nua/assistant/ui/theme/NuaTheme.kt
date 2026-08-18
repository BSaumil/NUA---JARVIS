package com.nua.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Maps NUA's tokens onto Material3's slots so ordinary Material components (Card, Button,
 * AlertDialog, TopAppBar) inherit the palette without every call site reaching for
 * [LocalNuaColors]. The brand hierarchy is preserved in the mapping: orange is `primary`
 * because it carries NUA's identity, violet is `secondary` (intelligence), pink is
 * `tertiary` (exceptional/active).
 *
 * `onPrimary`/`onSecondary`/`onTertiary` are the near-black background rather than white:
 * all three brand colours are bright enough that dark text on them is the legible choice.
 */
private val NuaDarkColorScheme = darkColorScheme(
    primary = NuaOrange,
    onPrimary = NuaBackground,
    secondary = NeuralViolet,
    onSecondary = NuaBackground,
    tertiary = FuturePink,
    onTertiary = NuaBackground,
    background = NuaBackground,
    onBackground = NuaTextPrimary,
    surface = NuaSurface,
    onSurface = NuaTextPrimary,
    surfaceVariant = NuaSurfaceElevated,
    onSurfaceVariant = NuaTextSecondary,
    error = NuaCritical,
    onError = NuaBackground,
    outline = NuaTextSecondary,
    outlineVariant = NuaBorder,
)

/**
 * NUA's theme. Dark isn't a mode here, it's the design — the spec defines exactly one
 * palette, built around a near-black ground that the orange identity and the orange→violet
 * gradient read against. There is deliberately no light variant: inventing eleven light
 * tokens the spec doesn't define would mean shipping a second, unspecified identity and
 * calling it NUA. If a light theme is wanted later it needs its own designed palette, not
 * an inversion of this one.
 *
 * Because of that, this composable takes no `useDarkTheme` flag and ignores the system
 * setting. `res/values/themes.xml` sets the same near-black as the pre-Compose launch
 * window background, so there's no white flash before the first frame.
 */
@Composable
fun NuaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNuaColors provides NuaColors()) {
        MaterialTheme(
            colorScheme = NuaDarkColorScheme,
            typography = NuaTypography,
            shapes = NuaShapes,
            content = content,
        )
    }
}

/** Shorthand for the semantic tokens Material3 has no slot for. */
object NuaTheme {
    val colors: NuaColors
        @Composable get() = LocalNuaColors.current
}
