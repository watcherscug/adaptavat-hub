package dev.watcher.modelhub.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.watcher.modelhub.data.AppSettings
import dev.watcher.modelhub.data.Density

data class UiConfig(
    val density: Density,
    val pagePadding: Dp,
    val gap: Dp,
    val cardPadding: Dp,
    val showTags: Boolean,
    val showStats: Boolean,
)

val LocalUiConfig = staticCompositionLocalOf {
    UiConfig(
        density = Density.COMFORTABLE,
        pagePadding = 20.dp,
        gap = 12.dp,
        cardPadding = 16.dp,
        showTags = true,
        showStats = true,
    )
}

fun uiConfigFor(settings: AppSettings): UiConfig {
    val scale = settings.density.scale
    return UiConfig(
        density = settings.density,
        pagePadding = (20 * scale).dp,
        gap = (12 * scale).dp,
        cardPadding = (16 * scale).dp,
        showTags = settings.showTags,
        showStats = settings.showStats,
    )
}
