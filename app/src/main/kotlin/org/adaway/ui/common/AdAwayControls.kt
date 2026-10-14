package org.adaway.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

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
    val colors = LocalAdAwayColors.current
    val box = colors.textPrimary.copy(alpha = 0.12f)
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        textStyle = AdAwayType.subtitle1,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        colors = TextFieldDefaults.colors(
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
        ),
        modifier = modifier.fillMaxWidth(),
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
fun AdAwayCheckboxRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LocalAdAwayColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, colors = adAwayCheckboxColors(), modifier = Modifier.padding(12.dp))
        Text(text, style = AdAwayType.small, color = colors.textPrimary)
    }
}

@Composable
fun adAwayCheckboxColors() = LocalAdAwayColors.current.let { colors ->
    CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.textSecondary, checkmarkColor = colors.onPrimary)
}
