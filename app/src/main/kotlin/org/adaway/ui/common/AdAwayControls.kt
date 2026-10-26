package org.adaway.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.LocalTelevision

/** The filled text field of the 6.x screens: gray box, underline, floating label, error under it. */
@Composable
fun AdAwayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    TextField(
        state = rememberTextFieldState(value, onValueChange),
        label = { Text(label) },
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = keyboardOptions.forDevice(),
        textStyle = AdAwayType.subtitle1,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        colors = adAwayTextFieldColors(),
        modifier = modifier.fillMaxWidth().keyboardOnRemoteOk(),
    )
}

/** The OK button of a TV remote, and the Enter keys acting as it. */
val RemoteConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * On TV, the OK button of the remote opens the keyboard, which moving onto the field leaves closed.
 * Some TVs, as TCL ones, do not open it by themselves.
 */
@Composable
private fun Modifier.keyboardOnRemoteOk(): Modifier {
    if (!LocalTelevision.current) return this
    val keyboard = LocalSoftwareKeyboardController.current
    return onPreviewKeyEvent { event ->
        if (event.key !in RemoteConfirmKeys) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp) keyboard?.show()
        true
    }
}

/**
 * The text a field edits, showing [value]: the edits of the user go to [onValueChange], and a
 * [value] changed elsewhere replaces the text, the cursor at its end. The values coming back from
 * the edits are recognized as such, so that one arriving late never undoes a later keystroke.
 */
@Composable
private fun rememberTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(initialText = value)
    val sent = remember { ArrayDeque<String>() }
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (text != currentValue) {
                sent.addLast(text)
                currentOnValueChange(text)
            }
        }
    }
    LaunchedEffect(value) {
        val echo = sent.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { sent.removeFirst() }
        } else if (value != state.text.toString()) {
            sent.clear()
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    return state
}

/**
 * On TV, moving onto a field leaves the keyboard closed, until [keyboardOnRemoteOk] opens it. Only
 * the fields editing a [TextFieldState] honor this.
 */
@Composable
private fun KeyboardOptions.forDevice() = if (LocalTelevision.current) copy(showKeyboardOnFocus = false) else this

@Composable
private fun adAwayTextFieldColors(): TextFieldColors {
    val colors = LocalAdAwayColors.current
    val box = colors.textPrimary.copy(alpha = 0.12f)
    return TextFieldDefaults.colors(
        focusedContainerColor = box,
        unfocusedContainerColor = box,
        errorContainerColor = box,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        errorTextColor = colors.textPrimary,
        cursorColor = colors.primary,
        focusedIndicatorColor = colors.primary,
        unfocusedIndicatorColor = colors.textPrimary.copy(alpha = 0.42f),
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.textPrimary.copy(alpha = 0.6f),
        errorIndicatorColor = ErrorRed,
        errorLabelColor = ErrorRed,
        errorSupportingTextColor = ErrorRed,
        errorCursorColor = ErrorRed,
    )
}

/** Error color of the 6.x text fields. */
private val ErrorRed = Color(0xFFB00020)

/** Outlined buttons side by side, one of them selected, as the 6.x toggle groups. */
@Composable
fun <T> AdAwayToggleGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAdAwayColors.current
    Row(modifier = modifier.padding(vertical = 6.dp)) {
        options.forEachIndexed { index, (value, label) ->
            val checked = value == selected
            val shape = RoundedCornerShape(
                topStart = if (index == 0) 4.dp else 0.dp,
                bottomStart = if (index == 0) 4.dp else 0.dp,
                topEnd = if (index == options.lastIndex) 4.dp else 0.dp,
                bottomEnd = if (index == options.lastIndex) 4.dp else 0.dp,
            )
            Box(
                modifier = Modifier
                    // Neighbors share their border.
                    .offset(x = (-1).dp * index)
                    .height(36.dp)
                    .defaultMinSize(minWidth = 64.dp)
                    .clip(shape)
                    .background(if (checked) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .border(BorderStroke(1.dp, if (checked) colors.primary else colors.textPrimary.copy(alpha = 0.12f)), shape)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = AdAwayType.button,
                    color = if (checked) colors.primary else colors.textPrimary.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** A checkbox with its text, the whole row toggling it. */
@Composable
fun AdAwayCheckboxRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAdAwayColors.current
    Row(
        modifier = modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, colors = adAwayCheckboxColors(), modifier = Modifier.padding(12.dp))
        Text(text, style = AdAwayType.small, color = colors.textPrimary)
    }
}

@Composable
fun adAwayCheckboxColors() = LocalAdAwayColors.current.let { colors ->
    CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.textSecondary, checkmarkColor = colors.onPrimary)
}

/**
 * The plain 6.x edit text: the text over an underline that thickens and turns red with the focus,
 * and the [hint] while it is empty.
 */
@Composable
fun AdAwayEditText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = LocalAdAwayColors.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val state = rememberTextFieldState(value, onValueChange)
    BasicTextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = keyboardOptions.forDevice(),
        textStyle = AdAwayType.subtitle1.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interactions,
        decorator = { field ->
            Box {
                if (state.text.isEmpty() && hint != null) Text(hint, style = AdAwayType.subtitle1, color = colors.textSecondary)
                field()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .drawBehind {
                val stroke = (if (focused) 2.dp else 1.dp).toPx()
                drawRect(
                    color = if (focused) colors.primary else colors.textSecondary,
                    topLeft = Offset(0f, size.height + 8.dp.toPx() - stroke),
                    size = Size(size.width, stroke),
                )
            }
            .keyboardOnRemoteOk(),
    )
}

@Composable
fun adAwaySwitchColors() = LocalAdAwayColors.current.let { colors ->
    SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary, checkedBorderColor = colors.primary)
}
