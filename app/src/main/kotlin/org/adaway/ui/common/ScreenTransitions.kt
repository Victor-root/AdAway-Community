package org.adaway.ui.common

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset

/**
 * The screen changes of the Android activities the 6.x versions were made of: the new screen comes
 * from a tenth of the width on the right while it appears, the previous one moves a tenth to the
 * left; going back does the opposite. The back gesture follows the same motion.
 */
object ScreenTransitions {
    private const val DURATION_MILLIS = 450
    private const val FADE_DELAY_MILLIS = 50
    private const val FADE_MILLIS = 50
    private const val CLOSE_FADE_DELAY_MILLIS = 35

    /** The Android `fast_out_extra_slow_in` curve. */
    private val easing = PathEasing(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
    )
    private val slide = tween<IntOffset>(DURATION_MILLIS, easing = easing)

    val enter: EnterTransition = slideInHorizontally(slide) { it / 10 } +
        fadeIn(tween(FADE_MILLIS, FADE_DELAY_MILLIS, LinearEasing))

    val exit: ExitTransition = slideOutHorizontally(slide) { -it / 10 }

    val popEnter: EnterTransition = slideInHorizontally(slide) { -it / 10 }

    val popExit: ExitTransition = slideOutHorizontally(slide) { it / 10 } +
        fadeOut(tween(FADE_MILLIS, CLOSE_FADE_DELAY_MILLIS, LinearEasing))
}
