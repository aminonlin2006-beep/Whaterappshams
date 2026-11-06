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

val SlateDeep = Color(0xFF0B1120)
val SlateSurface = Color(0xFF131C31)
val SlateCard = Color(0xFF1B2640)
val SlateBorder = Color(0xFF2B3A5A)
val SkyAccent = Color(0xFF38BDF8)
val SkySoft = Color(0xFF7DD3FC)
val AmberStorm = Color(0xFFFBBF24)
val CoralAlert = Color(0xFFF87171)
val EmeraldOk = Color(0xFF34D399)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

private val AtmosphericDarkColorScheme = darkColorScheme(
    primary = SkyAccent,
    onPrimary = SlateDeep,
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = SkySoft,
    secondary = AmberStorm,
    onSecondary = SlateDeep,
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = EmeraldOk,
    onTertiary = SlateDeep,
    background = SlateDeep,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = CoralAlert,
    onError = SlateDeep
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
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
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
