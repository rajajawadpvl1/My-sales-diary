package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = OnNavyContainer,
    secondary = StatusCallBackFg,
    onSecondary = Color.White,
    secondaryContainer = StatusCallBackBg,
    onSecondaryContainer = StatusCallBackFg,
    tertiary = StatusInterestedFg,
    onTertiary = Color.White,
    tertiaryContainer = StatusInterestedBg,
    onTertiaryContainer = StatusInterestedFg,
    background = OffWhiteBackground,
    onBackground = InkPrimary,
    surface = PureWhiteSurface,
    onSurface = InkPrimary,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = StatusNotInterestedFg,
    onError = Color.White,
    errorContainer = StatusNotInterestedBg,
    onErrorContainer = StatusNotInterestedFg,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkNavyPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkNavyContainer,
    onPrimaryContainer = DarkInkPrimary,
    secondary = Color(0xFFFFB878),
    onSecondary = Color(0xFF472400),
    secondaryContainer = Color(0xFF5C3107),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFF7AD9A8),
    onTertiary = Color(0xFF003820),
    tertiaryContainer = Color(0xFF0E4F32),
    onTertiaryContainer = Color(0xFFA7F3CB),
    background = DarkBackground,
    onBackground = DarkInkPrimary,
    surface = DarkSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7A1C1C),
    onErrorContainer = Color(0xFFFFDAD6),
)

val SalesDiaryShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun MySalesDiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SalesDiaryTypography,
        shapes = SalesDiaryShapes,
        content = content,
    )
}
