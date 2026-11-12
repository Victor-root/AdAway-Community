package org.adaway.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.LocalTelevision

class DialogButton(val text: String, val onClick: () -> Unit, val enabled: Boolean = true) {
    /** Does what a press of the button does, nothing while it is disabled. */
    fun press() {
        if (enabled) onClick()
    }
}

/** An alert dialog: its [message], and the uppercase red buttons side by side, or stacked when they do not fit on one line. */
@Composable
fun AdAwayAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmButton: DialogButton,
    dismissButton: DialogButton? = null,
    icon: Painter? = null,
) {
    val colors = LocalAdAwayColors.current
    AdAwayDialog(onDismissRequest, title, confirmButton, dismissButton, icon) {
        val scroll = rememberScrollState()
        val messageFocus = remember { FocusRequester() }
        Text(
            message,
            style = AdAwayType.body2,
            color = colors.textPrimary.copy(alpha = 0.6f),
            modifier = Modifier
                .focusRequester(messageFocus)
                .remoteScroll(scroll)
                .verticalScroll(scroll)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
        // On TV, a message longer than the dialog is read first, the arrows scrolling it down to the buttons.
        if (LocalTelevision.current && scroll.viewportSize > 0 && scroll.maxValue > 0) LaunchedEffect(Unit) { messageFocus.requestFocus() }
    }
}

/** The rounded dialog of the app around any [content], placed under the title. [neutralButton] stands apart, at the start. */
@Composable
fun AdAwayDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: DialogButton,
    dismissButton: DialogButton? = null,
    icon: Painter? = null,
    neutralButton: DialogButton? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAdAwayColors.current
    val isTelevision = LocalTelevision.current
    // On TV, a dialog whose content takes no focus starts on a button, so that OK answers it. The
    // dismissing one when there is one, so that OK pressed out of habit changes nothing.
    val initialButton = dismissButton ?: confirmButton
    val buttonFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var contentFocused by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier.padding(horizontal = 40.dp).widthIn(max = 560.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.dialogSurface,
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon?.let {
                        Icon(it, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(end = 8.dp).size(32.dp))
                    }
                    Text(title, style = AdAwayType.headline6, color = colors.textPrimary)
                }
                Box(modifier = Modifier.weight(1f, fill = false).onFocusChanged { contentFocused = it.hasFocus }) { content() }
                DialogButtons(neutralButton, listOfNotNull(dismissButton, confirmButton), Modifier.fillMaxWidth().padding(8.dp)) { button ->
                    if (button === initialButton) Modifier.focusRequester(buttonFocus) else Modifier
                }
            }
        }
        if (isTelevision) LaunchedEffect(Unit) { if (!contentFocused) buttonFocus.requestFocus() }
    }
}

/**
 * Buttons on one line, the confirming one last and the [neutral] one at the start; stacked under
 * each other, confirming first, when too wide.
 */
@Composable
private fun DialogButtons(neutral: DialogButton?, buttons: List<DialogButton>, modifier: Modifier, buttonModifier: (DialogButton) -> Modifier) {
    Layout(
        content = { (listOfNotNull(neutral) + buttons).forEach { DialogTextButton(it, buttonModifier(it)) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val all = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val start = if (neutral != null) all.first() else null
        val placeables = if (start != null) all.drop(1) else all
        val endWidth = placeables.sumOf { it.width } + gap * (placeables.size - 1)
        val width = constraints.maxWidth
        if (endWidth + (start?.let { it.width + gap } ?: 0) <= width) {
            layout(width, all.maxOf { it.height }) {
                start?.placeRelative(0, 0)
                var x = width - endWidth
                placeables.forEach {
                    it.placeRelative(x, 0)
                    x += it.width + gap
                }
            }
        } else {
            val stacked = placeables.reversed() + listOfNotNull(start)
            layout(width, stacked.sumOf { it.height }) {
                var y = 0
                stacked.forEach {
                    it.placeRelative(width - it.width, y)
                    y += it.height
                }
            }
        }
    }
}

@Composable
fun DialogTextButton(button: DialogButton, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = button.enabled, onClick = button.onClick)
            .alpha(if (button.enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(button.text.uppercase(), style = AdAwayType.button, color = LocalAdAwayColors.current.primary)
    }
}

/** Opacity of the controls not available yet. */
const val DISABLED_ALPHA = 0.38f
