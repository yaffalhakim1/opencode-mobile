package dev.opencode.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Surfaces the theme adds beyond `ColorScheme`.
 *
 * [brand] is the unmodified seed hue: it fails contrast as body text on light
 * surfaces, so it is only ever used for marks, carets and live pulses. Everything
 * else here is derived from the scheme's own roles, which is what keeps the
 * extras in step when the theme changes.
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

/**
 * The extras for one theme in one mode.
 *
 * They are taken from the scheme rather than hard-coded per theme, so a new
 * theme gets correct code blocks, bubbles and cards without listing them: a code
 * block is the theme's own "highest container" surface, which is the M3 role for
 * exactly this.
 */
private fun extendedFor(family: ThemeFamily, dark: Boolean): ExtendedColors {
    val scheme = family.scheme(dark)
    return ExtendedColors(
        brand = family.brand(dark),
        codeBackground = scheme.surfaceContainerHighest,
        codeBorder = scheme.outlineVariant,
        userBubble = scheme.primaryContainer,
        userBubbleText = scheme.onPrimaryContainer,
        cardSurface = scheme.surfaceContainerLow,
        cardBorder = scheme.outlineVariant,
        success = family.success(dark),
    )
}

/**
 * M3 Expressive cannot be enabled from this module: `MaterialExpressiveTheme`,
 * `MotionScheme` and `ExperimentalMaterial3ExpressiveApi` are all `internal` in
 * material3 1.4.0, so the spring motion system is not reachable from app code
 * until 1.5.0 ships (which needs compileSdk 37).
 *
 * The visible part of Expressive — the emphasized type scale, the ten-step corner
 * scale and the tonal palette — is applied directly through `MaterialTheme`.
 *
 * [family] is the palette; [darkTheme] is the mode. They are independent on
 * purpose: a theme supplies both, so pinning a theme does not pin it to one mode.
 */
@Composable
fun OpenCodeTheme(
    darkTheme: Boolean,
    family: ThemeFamily = ThemeFamily.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtendedColors provides extendedFor(family, darkTheme)) {
        MaterialTheme(
            colorScheme = family.scheme(darkTheme),
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
