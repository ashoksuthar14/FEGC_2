package eu.feg.ambient.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Exact-name access to the palette. Components read `LocalPskColors.current.surfaceRaised`
 * rather than guessing which Material slot a token was mapped into.
 */
val LocalPskColors = staticCompositionLocalOf { PskColors() }

private val colors = PskColors()

/**
 * Every Material slot is filled so that no stock Material colour can ever surface.
 * There is no light scheme — the product ships dark (PRD section 3.1).
 */
private val PskDarkColorScheme = darkColorScheme(
    primary = colors.brandBlue,
    onPrimary = colors.textPrimary,
    primaryContainer = colors.brandBlueDark,
    onPrimaryContainer = colors.textPrimary,
    inversePrimary = colors.brandBlueDeep,
    secondary = colors.brandBlueDark,
    onSecondary = colors.textPrimary,
    secondaryContainer = colors.surfaceVariant,
    onSecondaryContainer = colors.textPrimary,
    tertiary = colors.jackpotYellow,
    onTertiary = colors.background,
    tertiaryContainer = colors.jackpotYellowDim,
    onTertiaryContainer = colors.background,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceVariant,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.brandBlue,
    inverseSurface = colors.textPrimary,
    inverseOnSurface = colors.background,
    error = colors.negative,
    onError = colors.textPrimary,
    errorContainer = colors.negative,
    onErrorContainer = colors.textPrimary,
    outline = colors.surfaceRaised,
    outlineVariant = colors.surfaceVariant,
    scrim = colors.background,
    surfaceBright = colors.surfaceRaised,
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.surfaceVariant,
    surfaceContainerHighest = colors.surfaceRaised,
    surfaceContainerLow = colors.surface,
    surfaceContainerLowest = colors.background,
    surfaceDim = colors.background,
)

@Composable
fun PskTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // White status-bar icons: the bar sits on brandBlue, which the app bar paints.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(LocalPskColors provides colors) {
        MaterialTheme(
            colorScheme = PskDarkColorScheme,
            typography = PskTypography,
            shapes = PskMaterialShapes,
            content = content,
        )
    }
}
