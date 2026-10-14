package org.adaway.ui.prefs

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.adaway.R
import org.adaway.ui.common.AdAwayEditText
import org.adaway.ui.common.DISABLED_ALPHA
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.DialogTextButton
import org.adaway.ui.common.adAwaySwitchColors
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/** Room kept for the icons, so that the texts of the rows without one line up. */
private val IconSpace = 72.dp

/** A section title in red, with a divider above the ones that follow another section. */
@Composable
fun PreferenceCategory(title: String, divider: Boolean = true, enabled: Boolean = true) {
    val colors = LocalAdAwayColors.current
    if (divider) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.textPrimary.copy(alpha = 0.12f)))
    }
    Text(
        title,
        style = AdAwayType.body2,
        color = colors.primary,
        modifier = Modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(start = IconSpace, top = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/** A preference row: its icon, title and summary, and what is at its end such as a switch. */
@Composable
fun Preference(
    @DrawableRes icon: Int?,
    title: String?,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalAdAwayColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(IconSpace).padding(start = 16.dp)) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
            title?.let { Text(it, style = AdAwayType.subtitle1, color = colors.textPrimary) }
            summary?.let { Text(it, style = AdAwayType.body2, color = colors.textSecondary) }
        }
        trailing()
    }
}

@Composable
fun SwitchPreference(
    @DrawableRes icon: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val colors = LocalAdAwayColors.current
    Preference(icon, title, summary, enabled, onClick = { onCheckedChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = adAwaySwitchColors(),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** The rounded 6.x preference dialog listing the choices, each with its radio button. */
@Composable
fun ChoiceDialog(title: String, choices: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAdAwayColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.dialogSurface) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                Text(title, style = AdAwayType.headline6, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 24.dp))
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
                    choices.forEachIndexed { index, choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = index == selected, role = Role.RadioButton) { onSelect(index); onDismiss() }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = index == selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.textSecondary),
                                modifier = Modifier.padding(12.dp),
                            )
                            Text(choice, style = AdAwayType.subtitle1, color = colors.textPrimary)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                    DialogTextButton(DialogButton(stringResource(R.string.cancel), onClick = onDismiss))
                }
            }
        }
    }
}

/** The rounded 6.x preference dialog editing a text, with its OK button. */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAdAwayColors.current
    var value by rememberSaveable { mutableStateOf(initialValue) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.dialogSurface) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                Text(title, style = AdAwayType.headline6, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 24.dp))
                AdAwayEditText(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                    DialogTextButton(DialogButton(stringResource(R.string.cancel), onClick = onDismiss))
                    DialogTextButton(DialogButton(stringResource(android.R.string.ok), onClick = { onDismiss(); onConfirm(value) }))
                }
            }
        }
    }
}

