package org.adaway.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.ln

/** A pastel disc and the color of the icon drawn on it. */
@Immutable
data class Pastel(val background: Color, val foreground: Color)

/** The colors of the 6.x interface, in the light or the dark theme. */
@Immutable
data class AdAwayColors(
    val primary: Color,
    val onPrimary: Color,
    /** Header while ad blocking is off. */
    val headerOff: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val homeBackground: Color,
    val homeCardSubtitle: Color,
    /** Plain cards, as the counters straddling the home header or the sources. */
    val card: Color,
    val snackbarBackground: Color,
    val snackbarText: Color,
    val snackbarAction: Color,
    /** The cards of the scrolling home zone, lifted by their elevation in the dark theme. */
    val homeCard: Color,
    val pastelRed: Pastel,
    val pastelBlue: Pastel,
    val pastelAmber: Pastel,
    val pastelGrey: Pastel,
    val pastelGreen: Pastel,
    val dialogSurface: Color,
    val bumpBarBackground: Color,
    val bumpBarStroke: Color,
    /** The marks of the names of the TV DNS log that a list blocks or allows. */
    val logBlocked: Color,
    val logAllowed: Color,
)

/** How Material 2 lightens a surface in the dark theme for an elevation in dp. */
private fun Color.elevated(elevation: Float): Color =
    Color.White.copy(alpha = ((4.5f * ln(elevation + 1) + 2f) / 100f)).compositeOver(this)

private const val HOME_CARD_ELEVATION = 2f
private val DarkSurface = Color(0xFF121212)

val LightAdAwayColors = AdAwayColors(
    primary = Color(0xFFC62828),
    onPrimary = Color.White,
    headerOff = Color(0xFF888888),
    surface = Color.White,
    textPrimary = Color(0xDE000000),
    textSecondary = Color(0x8A000000),
    homeBackground = Color(0xFFF4F5F7),
    homeCardSubtitle = Color(0xFF6B7280),
    card = Color.White,
    snackbarBackground = Color(0xFF333333),
    snackbarText = Color(0xDEFFFFFF),
    snackbarAction = Color(0xFFEF9A9A),
    homeCard = Color.White,
    pastelRed = Pastel(Color(0xFFFCE4E4), Color(0xFFC62828)),
    pastelBlue = Pastel(Color(0xFFE3F2FD), Color(0xFF1565C0)),
    pastelAmber = Pastel(Color(0xFFFFF1DE), Color(0xFFEF6C00)),
    pastelGrey = Pastel(Color(0xFFECEFF1), Color(0xFF455A64)),
    pastelGreen = Pastel(Color(0xFFE3F6E8), Color(0xFF2E7D32)),
    dialogSurface = Color.White,
    bumpBarBackground = Color(0xFFFAFAFC),
    bumpBarStroke = Color(0xFFE0E0E8),
    logBlocked = Color(0xFFFF5252),
    logAllowed = Color(0xFF64FFDA),
)

val DarkAdAwayColors = AdAwayColors(
    primary = BrandRed,
    onPrimary = Color.White,
    headerOff = Color(0xFF888888),
    surface = DarkSurface,
    textPrimary = Color.White,
    textSecondary = Color(0xB3FFFFFF),
    homeBackground = Color(0xFF121212),
    homeCardSubtitle = Color(0xFF9AA0A6),
    card = Color(0xFF424242),
    snackbarBackground = Color(0xFFE0E0E0),
    snackbarText = Color(0xDE000000),
    snackbarAction = Color(0xFFC62828),
    homeCard = DarkSurface.elevated(HOME_CARD_ELEVATION),
    pastelRed = Pastel(Color(0xFF3A2222), Color(0xFFEF9A9A)),
    pastelBlue = Pastel(Color(0xFF1E2A3A), Color(0xFF90CAF9)),
    pastelAmber = Pastel(Color(0xFF33291A), Color(0xFFFFCC80)),
    pastelGrey = Pastel(Color(0xFF263238), Color(0xFFB0BEC5)),
    pastelGreen = Pastel(Color(0xFF1E3322), Color(0xFFA5D6A7)),
    dialogSurface = Color(0xFF2B2B2B),
    bumpBarBackground = Color(0xFF2C2C31),
    bumpBarStroke = Color(0xFF3D3D43),
    logBlocked = Color(0xFFEF9A9A),
    logAllowed = Color(0xFF80CBC4),
)

val LocalAdAwayColors = staticCompositionLocalOf { LightAdAwayColors }
