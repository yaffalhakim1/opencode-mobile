package dev.opencode.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Palette.DarkPrimary,
    onPrimary = Palette.DarkOnPrimary,
    primaryContainer = Palette.DarkPrimaryContainer,
    onPrimaryContainer = Palette.DarkOnPrimaryContainer,
    secondary = Palette.DarkSecondary,
    onSecondary = Palette.DarkOnSecondary,
    secondaryContainer = Palette.DarkSecondaryContainer,
    onSecondaryContainer = Palette.DarkOnSecondaryContainer,
    tertiary = Palette.DarkTertiary,
    onTertiary = Palette.DarkOnTertiary,
    tertiaryContainer = Palette.DarkTertiaryContainer,
    onTertiaryContainer = Palette.DarkOnTertiaryContainer,
    error = Palette.DarkError,
    onError = Palette.DarkOnError,
    errorContainer = Palette.DarkErrorContainer,
    onErrorContainer = Palette.DarkOnErrorContainer,
    background = Palette.DarkBackground,
    onBackground = Palette.DarkOnBackground,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkOnSurface,
    surfaceVariant = Palette.DarkSurfaceVariant,
    onSurfaceVariant = Palette.DarkOnSurfaceVariant,
    outline = Palette.DarkOutline,
    outlineVariant = Palette.DarkOutlineVariant,
)

private val LightScheme = lightColorScheme(
    primary = Palette.LightPrimary,
    onPrimary = Palette.LightOnPrimary,
    primaryContainer = Palette.LightPrimaryContainer,
    onPrimaryContainer = Palette.LightOnPrimaryContainer,
    secondary = Palette.LightSecondary,
    onSecondary = Palette.LightOnSecondary,
    secondaryContainer = Palette.LightSecondaryContainer,
    onSecondaryContainer = Palette.LightOnSecondaryContainer,
    tertiary = Palette.LightTertiary,
    onTertiary = Palette.LightOnTertiary,
    tertiaryContainer = Palette.LightTertiaryContainer,
    onTertiaryContainer = Palette.LightOnTertiaryContainer,
    error = Palette.LightError,
    onError = Palette.LightOnError,
    errorContainer = Palette.LightErrorContainer,
    onErrorContainer = Palette.LightOnErrorContainer,
    background = Palette.LightBackground,
    onBackground = Palette.LightOnBackground,
    surface = Palette.LightSurface,
    onSurface = Palette.LightOnSurface,
    surfaceVariant = Palette.LightSurfaceVariant,
    onSurfaceVariant = Palette.LightOnSurfaceVariant,
    outline = Palette.LightOutline,
    outlineVariant = Palette.LightOutlineVariant,
)

/**
 * Surfaces the theme adds beyond `ColorScheme`.
 *
 * [brand] is the unmodified logo green: it fails contrast as text on light
 * surfaces, so it is only ever used for marks, carets and live pulses.
 */
@Immutable
data class ExtendedColors(
    val brand: Color,
    val codeBackground: Color,
    val codeBorder: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val success: Color,
)

internal val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        brand = Color.Unspecified,
        codeBackground = Color.Unspecified,
        codeBorder = Color.Unspecified,
        userBubble = Color.Unspecified,
        userBubbleText = Color.Unspecified,
        cardSurface = Color.Unspecified,
        cardBorder = Color.Unspecified,
        success = Color.Unspecified,
    )
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

private val DarkExtended = ExtendedColors(
    brand = Palette.BrandDark,
    codeBackground = Color(0xFF11170F),
    codeBorder = Color(0xFF2A332D),
    userBubble = Palette.DarkPrimaryContainer,
    userBubbleText = Palette.DarkOnPrimaryContainer,
    cardSurface = Color(0xFF141A16),
    cardBorder = Color(0xFF2A332D),
    success = Palette.BrandDark,
)

private val LightExtended = ExtendedColors(
    brand = Palette.BrandLight,
    codeBackground = Color(0xFFEFF5F0),
    codeBorder = Color(0xFFD3DDD6),
    userBubble = Palette.LightPrimaryContainer,
    userBubbleText = Palette.LightOnPrimaryContainer,
    cardSurface = Color(0xFFF1F6F2),
    cardBorder = Color(0xFFD9E3DC),
    success = Color(0xFF0A7B45),
)

/**
 * M3 Expressive cannot be enabled from this module: `MaterialExpressiveTheme`,
 * `MotionScheme` and `ExperimentalMaterial3ExpressiveApi` are all `internal` in
 * material3 1.4.0, so the spring motion system is not reachable from app code
 * until 1.5.0 ships (which needs compileSdk 37).
 *
 * The visible part of Expressive — the emphasized type scale, the ten-step corner
 * scale and the tonal palette — is applied directly through `MaterialTheme`.
 */
@Composable
fun OpenCodeTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val extended = if (darkTheme) DarkExtended else LightExtended

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
