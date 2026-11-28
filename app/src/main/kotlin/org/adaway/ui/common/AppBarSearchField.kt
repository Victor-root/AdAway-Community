package org.adaway.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/**
 * The search field that takes the place of the title in the app bar, filtering as it is typed. The
 * arrows of a remote or a keyboard leave it, which a text field keeps otherwise: down for the
 * results, right from the end of the text for the actions of the bar.
 */
@Composable
fun RowScope.AppBarSearchField(filter: String, hint: String, keyboardType: KeyboardType, onSearch: (String) -> Unit) {
    val colors = LocalAdAwayColors.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var value by remember { mutableStateOf(TextFieldValue(filter, TextRange(filter.length))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(modifier = Modifier.weight(1f).padding(start = 20.dp, end = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (filter.isEmpty()) {
            Text(hint, style = AdAwayType.subtitle1, color = colors.onPrimary.copy(alpha = 0.7f))
        }
        BasicTextField(
            value = value,
            onValueChange = { edited ->
                value = edited
                if (edited.text != filter) onSearch(edited.text)
            },
            singleLine = true,
            textStyle = AdAwayType.subtitle1.copy(color = colors.onPrimary),
            cursorBrush = SolidColor(colors.onPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    val atEnd = value.selection.collapsed && value.selection.end == value.text.length
                    event.type == KeyEventType.KeyDown && when (event.key) {
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionRight -> atEnd && focusManager.moveFocus(FocusDirection.Right)
                        else -> false
                    }
                },
        )
    }
}
