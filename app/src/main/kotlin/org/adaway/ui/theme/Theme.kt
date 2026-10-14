package org.adaway.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.adaway.data.settings.ThemeMode

/** Brand red of the headers, the welcome screens and the launcher icons, in both themes. */
val BrandRed = Color(0xFFB71C1C)

private class Palette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
)

private val LightPalette = Palette(
    primary = Color(0xFFC62828),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF705C2E),
    onTertiary = Color.White,
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BE),
    inverseSurface = Color(0xFF392E2C),
    inverseOnSurface = Color(0xFFFFEDEA),
    inversePrimary = Color(0xFFFFB4A9),
    surfaceDim = Color(0xFFE8D6D3),
    surfaceBright = Color(0xFFFFF8F7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainer = Color(0xFFFCEAE8),
    surfaceContainerHigh = Color(0xFFF6E4E2),
    surfaceContainerHighest = Color(0xFFF1DEDC),
)

private val DarkPalette = Palette(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFDFC38C),
    onTertiary = Color(0xFF3E2E04),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DEDC),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DEDC),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534341),
    inverseSurface = Color(0xFFF1DEDC),
    inverseOnSurface = Color(0xFF392E2C),
    inversePrimary = Color(0xFFC62828),
    surfaceDim = Color(0xFF1A1110),
    surfaceBright = Color(0xFF423735),
    surfaceContainerLowest = Color(0xFF140C0B),
    surfaceContainerLow = Color(0xFF231918),
    surfaceContainer = Color(0xFF271D1C),
    surfaceContainerHigh = Color(0xFF322826),
    surfaceContainerHighest = Color(0xFF3D3231),
)

private fun Palette.applyTo(base: ColorScheme) = base.copy(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = secondary, onSecondary = onSecondary,
    secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = onTertiary,
    background = background, onBackground = onBackground,
    surface = surface, onSurface = onSurface,
    surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
    error = error, onError = onError,
    errorContainer = errorContainer, onErrorContainer = onErrorContainer,
    outline = outline, outlineVariant = outlineVariant,
    inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface, inversePrimary = inversePrimary,
    surfaceTint = primary, surfaceDim = surfaceDim, surfaceBright = surfaceBright,
    surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
)

/**
 * How many screens on display show red under the system navigation bar, which then takes white
 * icons, as the 6.x lists and first launch screens colored it.
 */
class NavigationBarAppearance {
    var redScreens by mutableIntStateOf(0)
}

val LocalNavigationBarAppearance = staticCompositionLocalOf { NavigationBarAppearance() }

/** Whether the app runs on a TV, driven by its remote. */
val LocalTelevision = staticCompositionLocalOf { false }

/** For a screen red under the navigation bar, while it is on display. */
@Composable
fun RedNavigationBar() {
    val appearance = LocalNavigationBarAppearance.current
    DisposableEffect(appearance) {
        appearance.redScreens++
        onDispose { appearance.redScreens-- }
    }
}

/** The theme until the user picks one: dark on TV, as on 6.x, else the one of the system. */
fun defaultThemeMode(isTelevision: Boolean) = if (isTelevision) ThemeMode.DARK else ThemeMode.SYSTEM

/** Applies the app colors to the Material components. */
@Composable
fun AdAwayTheme(themeMode: ThemeMode?, isTelevision: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode ?: defaultThemeMode(isTelevision)) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    val navigationBar = remember { NavigationBarAppearance() }
    if (!view.isInEditMode) {
        LaunchedEffect(dark) {
            val insetsController = WindowCompat.getInsetsController((view.context as Activity).window, view)
            // Every screen starts with the red brand header.
            insetsController.isAppearanceLightStatusBars = false
            snapshotFlow { navigationBar.redScreens }.collect { redScreens ->
                insetsController.isAppearanceLightNavigationBars = !dark && redScreens == 0
            }
        }
    }
    val palette = if (dark) DarkPalette else LightPalette
    val colorScheme = remember(dark) { palette.applyTo(if (dark) darkColorScheme() else lightColorScheme()) }
    val colors = if (dark) DarkAdAwayColors else LightAdAwayColors
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalAdAwayColors provides colors,
            LocalNavigationBarAppearance provides navigationBar,
            LocalRippleConfiguration provides controlHighlight(colors),
            LocalTelevision provides isTelevision,
            content = content,
        )
    }
}
