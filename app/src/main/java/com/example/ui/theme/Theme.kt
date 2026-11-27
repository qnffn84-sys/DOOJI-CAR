package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class IosExtendedColors(
    val green: Color,
    val orange: Color,
    val red: Color,
    val indigo: Color,
    val teal: Color,
    val separator: Color,
    val secondaryLabel: Color,
    val groupedSecondary: Color,
    val isDark: Boolean
)

val LocalIosColors = staticCompositionLocalOf {
    IosExtendedColors(
        green = IosGreenLight,
        orange = IosOrangeLight,
        red = IosRedLight,
        indigo = IosIndigoLight,
        teal = IosTealLight,
        separator = IosSeparatorLight,
        secondaryLabel = IosSecondaryLabelLight,
        groupedSecondary = IosGroupedSecondaryLight,
        isDark = false
    )
}

private val DoojicarLightColors = lightColorScheme(
    primary = IosBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF003A7A),
    secondary = IosIndigoLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E7FA),
    onSecondaryContainer = Color(0xFF282680),
    tertiary = IosTealLight,
    onTertiary = Color.White,
    background = IosBgLight,
    onBackground = IosLabelLight,
    surface = IosCardLight,
    onSurface = IosLabelLight,
    surfaceVariant = IosGroupedSecondaryLight,
    onSurfaceVariant = IosSecondaryLabelLight,
    outline = IosSeparatorLight,
    outlineVariant = Color(0xFFE5E5EA),
    error = IosRedLight,
    onError = Color.White
)

private val DoojicarDarkColors = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A2E5C),
    onPrimaryContainer = Color(0xFFBBD9FF),
    secondary = IosIndigoDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF24235C),
    onSecondaryContainer = Color(0xFFD2D1FF),
    tertiary = IosTealDark,
    onTertiary = Color.Black,
    background = IosBgDark,
    onBackground = IosLabelDark,
    surface = IosCardDark,
    onSurface = IosLabelDark,
    surfaceVariant = IosGroupedSecondaryDark,
    onSurfaceVariant = IosSecondaryLabelDark,
    outline = IosSeparatorDark,
    outlineVariant = Color(0xFF2C2C2E),
    error = IosRedDark,
    onError = Color.White
)

private val DoojicarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun DoojicarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DoojicarDarkColors else DoojicarLightColors
    val extendedColors = if (darkTheme) {
        IosExtendedColors(
            green = IosGreenDark,
            orange = IosOrangeDark,
            red = IosRedDark,
            indigo = IosIndigoDark,
            teal = IosTealDark,
            separator = IosSeparatorDark,
            secondaryLabel = IosSecondaryLabelDark,
            groupedSecondary = IosGroupedSecondaryDark,
            isDark = true
        )
    } else {
        IosExtendedColors(
            green = IosGreenLight,
            orange = IosOrangeLight,
            red = IosRedLight,
            indigo = IosIndigoLight,
            teal = IosTealLight,
            separator = IosSeparatorLight,
            secondaryLabel = IosSecondaryLabelLight,
            groupedSecondary = IosGroupedSecondaryLight,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalIosColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DoojicarTypography,
            shapes = DoojicarShapes,
            content = content
        )
    }
}
