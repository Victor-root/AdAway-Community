package org.adaway.ui.theme

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.RippleConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The highlight the 6.x controls take with the focus or a press: [color] laid over them at [alpha]. */
fun highlight(color: Color, alpha: Float) = RippleConfiguration(color, RippleAlpha(draggedAlpha = alpha, focusedAlpha = alpha, hoveredAlpha = alpha / 3, pressedAlpha = alpha))

/**
 * The gray highlight of the 6.x controls, white on the dark theme, so that the remote shows where
 * the focus is on every screen.
 */
fun controlHighlight(colors: AdAwayColors) =
    if (colors.surface.luminance() < 0.5f) highlight(Color.White, 0.2f) else highlight(Color.Black, 0.12f)
