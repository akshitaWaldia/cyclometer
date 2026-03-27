package com.cyclecomp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Cycling-themed color palette
object CycleCompColors {
    // Dark theme backgrounds
    val DarkBackground = Color(0xFF121212)
    val DarkSurface = Color(0xFF1E1E1E)
    val DarkTileBg = Color(0xFF2A2A2A)

    // Light theme backgrounds
    val LightBackground = Color(0xFFF5F5F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightTileBg = Color(0xFFE8E8E8)

    // Metric accent colors
    val PowerOrange = Color(0xFFFF9800)
    val SpeedGreen = Color(0xFF4CAF50)
    val HeartRateRed = Color(0xFFF44336)
    val CadenceBlue = Color(0xFF2196F3)
    val DistancePurple = Color(0xFF9C27B0)
    val TimeTeal = Color(0xFF009688)
    val ElevationAmber = Color(0xFF8BC34A) // Light green — readable on both light and dark
    val GradientCyan = Color(0xFF00BCD4)
    val CaloriesDeepOrange = Color(0xFFFF5722)
    val TssIndigo = Color(0xFF3F51B5)

    // Tile background tints (dark mode)
    val PowerTileBg = Color(0xFF2D1F00)
    val SpeedTileBg = Color(0xFF0D2210)
    val HeartRateTileBg = Color(0xFF2D0A0A)
    val CadenceTileBg = Color(0xFF0A1A2D)
    val DistanceTileBg = Color(0xFF1A0D22)
    val TimeTileBg = Color(0xFF0D1F1C)
    val MapPlaceholderBg = Color(0xFF1A1A2E)

    // Tile background tints (light mode)
    val PowerTileBgLight = Color(0xFFFFF3E0)
    val SpeedTileBgLight = Color(0xFFE8F5E9)
    val HeartRateTileBgLight = Color(0xFFFFEBEE)
    val CadenceTileBgLight = Color(0xFFE3F2FD)
    val DistanceTileBgLight = Color(0xFFF3E5F5)
    val TimeTileBgLight = Color(0xFFE0F2F1)
    val MapPlaceholderBgLight = Color(0xFFE8EAF6)
}

private val DarkColorScheme = darkColorScheme(
    primary = CycleCompColors.SpeedGreen,
    secondary = CycleCompColors.CadenceBlue,
    tertiary = CycleCompColors.PowerOrange,
    background = CycleCompColors.DarkBackground,
    surface = CycleCompColors.DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CycleCompColors.SpeedGreen,
    secondary = CycleCompColors.CadenceBlue,
    tertiary = CycleCompColors.PowerOrange,
    background = CycleCompColors.LightBackground,
    surface = CycleCompColors.LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

/** Font scale multiplier exposed via CompositionLocal */
val LocalFontScale = compositionLocalOf { 1f }

/** Whether night mode is active */
val LocalNightMode = compositionLocalOf { false }

@Composable
fun CycleCompTheme(
    nightMode: Boolean = isSystemInDarkTheme(),
    largeFontEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (nightMode) DarkColorScheme else LightColorScheme
    val fontScale = if (largeFontEnabled) 1.5f else 1f

    val baseTypography = Typography()
    val scaledTypography = Typography(
        displayLarge = baseTypography.displayLarge.copy(fontSize = (57 * fontScale).sp),
        displayMedium = baseTypography.displayMedium.copy(fontSize = (45 * fontScale).sp),
        displaySmall = baseTypography.displaySmall.copy(fontSize = (36 * fontScale).sp),
        headlineLarge = baseTypography.headlineLarge.copy(fontSize = (32 * fontScale).sp),
        headlineMedium = baseTypography.headlineMedium.copy(fontSize = (28 * fontScale).sp),
        headlineSmall = baseTypography.headlineSmall.copy(fontSize = (24 * fontScale).sp),
        titleLarge = baseTypography.titleLarge.copy(fontSize = (22 * fontScale).sp),
        titleMedium = baseTypography.titleMedium.copy(fontSize = (16 * fontScale).sp),
        titleSmall = baseTypography.titleSmall.copy(fontSize = (14 * fontScale).sp),
        bodyLarge = baseTypography.bodyLarge.copy(fontSize = (16 * fontScale).sp),
        bodyMedium = baseTypography.bodyMedium.copy(fontSize = (14 * fontScale).sp),
        bodySmall = baseTypography.bodySmall.copy(fontSize = (12 * fontScale).sp),
        labelLarge = baseTypography.labelLarge.copy(fontSize = (14 * fontScale).sp),
        labelMedium = baseTypography.labelMedium.copy(fontSize = (12 * fontScale).sp),
        labelSmall = baseTypography.labelSmall.copy(fontSize = (11 * fontScale).sp)
    )

    CompositionLocalProvider(
        LocalFontScale provides fontScale,
        LocalNightMode provides nightMode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            content = content
        )
    }
}
