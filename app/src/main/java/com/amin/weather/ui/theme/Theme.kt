package com.amin.weather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Color definitions matching the HTML Material 3 palette
val SurfaceBg = Color(0xFF091327)
val SurfaceContainer = Color(0xFF161F34)
val SurfaceContainerHigh = Color(0xFF212A3F)
val SurfaceContainerHighest = Color(0xFF2C344B)
val SurfaceContainerLow = Color(0xFF121B30)
val SurfaceContainerLowest = Color(0xFF050D22)

val PrimarySky = Color(0xFF8ED5FF)
val PrimaryContainer = Color(0xFF38BDF8)
val SecondaryAmber = Color(0xFFFFC640)
val SecondaryContainer = Color(0xFFE3AA00)
val TertiaryEmerald = Color(0xFF4EE6AA)
val TertiaryContainer = Color(0xFF22C990)
val ErrorRed = Color(0xFFFFB4AB)
val ErrorContainer = Color(0xFF93000A)
val OutlineBorder = Color(0xFF87929A)
val OutlineVariant = Color(0xFF3E484F)
val TextOnSurface = Color(0xFFD9E2FF)
val TextOnSurfaceVariant = Color(0xFFBDC8D1)

// Backward-compatible color aliases
val SlateDeep = SurfaceBg
val SlateSurface = SurfaceContainerLow
val SlateCard = SurfaceContainer
val SlateBorder = OutlineVariant
val SkyAccent = PrimarySky
val SkySoft = PrimaryContainer
val AmberStorm = SecondaryAmber
val CoralAlert = ErrorRed
val EmeraldOk = TertiaryEmerald
val TextPrimary = TextOnSurface
val TextSecondary = TextOnSurfaceVariant

private val AtmosphericDarkColorScheme = darkColorScheme(
    primary = PrimarySky,
    onPrimary = Color(0xFF00354A),
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Color(0xFF004965),
    secondary = SecondaryAmber,
    onSecondary = Color(0xFF402D00),
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = Color(0xFF5A4100),
    tertiary = TertiaryEmerald,
    onTertiary = Color(0xFF003825),
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = Color(0xFF004E35),
    background = SurfaceBg,
    onBackground = TextOnSurface,
    surface = SurfaceBg,
    onSurface = TextOnSurface,
    surfaceVariant = SurfaceContainerHighest,
    onSurfaceVariant = TextOnSurfaceVariant,
    outline = OutlineBorder,
    outlineVariant = OutlineVariant,
    error = ErrorRed,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun DezfulWeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AtmosphericDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
