package dev.watcher.modelhub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import dev.watcher.modelhub.data.ThemeMode

@Composable
fun ModelHubTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentArgb: Int? = null,
    cornerScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }

    val scheme = if (!dynamicColor && accentArgb != null) {
        base.withAccent(Color(accentArgb), dark)
    } else {
        base
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = ModelHubTypography,
        shapes = modelHubShapes(cornerScale),
        content = content,
    )
}

private fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White
    val container = if (dark) lerp(accent, Color.Black, 0.55f) else lerp(accent, Color.White, 0.82f)
    val onContainer = if (dark) lerp(accent, Color.White, 0.75f) else lerp(accent, Color.Black, 0.45f)
    val secondary = if (dark) lerp(accent, Color.White, 0.35f) else lerp(accent, Color.Black, 0.15f)
    val secondaryContainer = if (dark) lerp(accent, Color.Black, 0.6f) else lerp(accent, Color.White, 0.86f)
    val onSecondaryContainer = if (dark) lerp(accent, Color.White, 0.7f) else lerp(accent, Color.Black, 0.5f)

    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = lerp(accent, if (dark) Color.Black else Color.White, 0.6f),
        secondary = secondary,
        onSecondary = onAccent,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        surfaceTint = accent,
    )
}
