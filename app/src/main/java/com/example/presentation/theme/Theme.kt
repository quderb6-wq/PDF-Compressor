package com.example.presentation.theme

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
import com.example.domain.model.ThemeMode

@Immutable
data class SemanticColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val accentCoral: Color,
    val accentCoralContainer: Color
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        success = EmeraldSuccessLight,
        successContainer = EmeraldSuccessContainerLight,
        warning = AmberWarningLight,
        warningContainer = AmberWarningContainerLight,
        accentCoral = SignalCoralLight,
        accentCoralContainer = SignalCoralContainerLight
    )
}

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimaryLight,
    onPrimary = CobaltOnPrimaryLight,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = CobaltOnContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = SlateOnContainerLight,
    tertiary = SignalCoralLight,
    onTertiary = SignalCoralOnLight,
    tertiaryContainer = SignalCoralContainerLight,
    onTertiaryContainer = SignalCoralOnContainerLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = RedErrorLight,
    onError = Color.White,
    errorContainer = RedErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = CobaltOnPrimaryDark,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = CobaltOnContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = SlateOnContainerDark,
    tertiary = SignalCoralDark,
    onTertiary = SignalCoralOnDark,
    tertiaryContainer = SignalCoralContainerDark,
    onTertiaryContainer = SignalCoralOnContainerDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = RedErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = RedErrorContainerDark
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PdfCompressorTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val semanticColors = if (useDark) {
        SemanticColors(
            success = EmeraldSuccessDark,
            successContainer = EmeraldSuccessContainerDark,
            warning = AmberWarningDark,
            warningContainer = AmberWarningContainerDark,
            accentCoral = SignalCoralDark,
            accentCoralContainer = SignalCoralContainerDark
        )
    } else {
        SemanticColors(
            success = EmeraldSuccessLight,
            successContainer = EmeraldSuccessContainerLight,
            warning = AmberWarningLight,
            warningContainer = AmberWarningContainerLight,
            accentCoral = SignalCoralLight,
            accentCoralContainer = SignalCoralContainerLight
        )
    }

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
