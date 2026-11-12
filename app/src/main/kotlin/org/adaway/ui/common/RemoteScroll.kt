package org.adaway.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import org.adaway.ui.theme.LocalTelevision

/** The scroll of an arrow of the remote: half the height in sight, as Android scrolls a text. */
fun ScrollState.remoteStep(down: Boolean): Int = viewportSize / 2 * if (down) 1 else -1

/**
 * On TV, makes this text take the focus, and its up and down arrows scroll [scroll] by a
 * [remoteStep]. At an end of the text, they move the focus as usual.
 */
@Composable
fun Modifier.remoteScroll(scroll: ScrollState): Modifier {
    if (!LocalTelevision.current) return this
    val scope = rememberCoroutineScope()
    return onPreviewKeyEvent { event ->
        val down = when (event.key) {
            Key.DirectionDown -> true
            Key.DirectionUp -> false
            else -> return@onPreviewKeyEvent false
        }
        if (!(if (down) scroll.canScrollForward else scroll.canScrollBackward)) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(scroll.remoteStep(down).toFloat()) }
        true
    }.focusable()
}
