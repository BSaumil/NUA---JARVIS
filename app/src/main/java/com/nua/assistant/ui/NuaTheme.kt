package com.nua.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NuaSeed = Color(0xFF3D8B7D)

private val NuaDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6FE3C9),
    secondary = Color(0xFFB5CCC5),
    background = Color(0xFF11141A),
    surface = Color(0xFF171B22),
)

private val NuaLightColorScheme = lightColorScheme(
    primary = NuaSeed,
    secondary = Color(0xFF4C6360),
    background = Color(0xFFFAFDFB),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun NuaTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) NuaDarkColorScheme else NuaLightColorScheme,
        content = content,
    )
}
