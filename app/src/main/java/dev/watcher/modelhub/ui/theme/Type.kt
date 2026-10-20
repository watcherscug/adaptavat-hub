package dev.watcher.modelhub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

val ModelHubTypography = Typography(
    displaySmall = Default.displaySmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = Default.headlineLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = Default.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = Default.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = Default.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = Default.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = Default.bodyLarge.copy(
        lineHeight = 24.sp,
    ),
    labelLarge = Default.labelLarge.copy(
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = Default.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
)

val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
)
