package dev.watcher.modelhub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

fun modelHubShapes(scale: Float = 1f): Shapes {
    fun corner(base: Int) = RoundedCornerShape((base * scale).coerceAtLeast(0f).dp)
    return Shapes(
        extraSmall = corner(8),
        small = corner(12),
        medium = corner(18),
        large = corner(24),
        extraLarge = corner(32),
    )
}
