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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

class DialogButton(val text: String, val onClick: () -> Unit, val enabled: Boolean = true)

/**
 * The alert dialog of the 6.x interface: small corners, uppercase red buttons side by side, or
 * stacked when they do not fit on one line.
 */
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
        Text(
            message,
            style = AdAwayType.body2,
            color = colors.textPrimary.copy(alpha = 0.6f),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
    }
}

/** The 6.x dialog around any [content], placed under the title. */
@Composable
fun AdAwayDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: DialogButton,
    dismissButton: DialogButton? = null,
    icon: Painter? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAdAwayColors.current
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 40.dp).widthIn(max = 560.dp),
            shape = RoundedCornerShape(4.dp),
            color = colors.dialogSurface,
            shadowElevation = 24.dp,
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
                Box(modifier = Modifier.weight(1f, fill = false)) { content() }
                DialogButtons(listOfNotNull(dismissButton, confirmButton), Modifier.fillMaxWidth().padding(8.dp))
            }
        }
    }
}

/** Buttons on one line, the confirming one last; stacked under each other, confirming first, when too wide. */
@Composable
private fun DialogButtons(buttons: List<DialogButton>, modifier: Modifier) {
    Layout(
        content = { buttons.forEach { DialogTextButton(it) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val rowWidth = placeables.sumOf { it.width } + gap * (placeables.size - 1)
        val width = constraints.maxWidth
        if (rowWidth <= width) {
            layout(width, placeables.maxOf { it.height }) {
                var x = width - rowWidth
                placeables.forEach {
                    it.placeRelative(x, 0)
                    x += it.width + gap
                }
            }
        } else {
            val stacked = placeables.reversed()
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
