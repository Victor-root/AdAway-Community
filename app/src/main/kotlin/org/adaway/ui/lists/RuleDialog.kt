package org.adaway.ui.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.blocklist.RuleSaveResult
import org.adaway.data.db.RuleType
import org.adaway.data.db.UserRuleEntity
import org.adaway.ui.common.AdAwayDialog
import org.adaway.ui.common.AdAwayEditText
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.ruleTypeLabel
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/** The 6.x add and edit dialog: the name, and the address of a redirection. */
@Composable
fun RuleDialog(type: RuleType, rule: UserRuleEntity?, viewModel: ListsViewModel, onDismiss: () -> Unit) {
    val colors = LocalAdAwayColors.current
    var host by rememberSaveable { mutableStateOf(rule?.host.orEmpty()) }
    var redirection by rememberSaveable { mutableStateOf(rule?.redirection.orEmpty()) }
    var result by remember { mutableStateOf<RuleSaveResult?>(null) }
    val title = when (type) {
        RuleType.BLOCKED -> if (rule == null) R.string.rule_add_blocked else R.string.rule_edit_blocked
        RuleType.ALLOWED -> if (rule == null) R.string.rule_add_allowed else R.string.rule_edit_allowed
        RuleType.REDIRECTED -> if (rule == null) R.string.rule_add_redirected else R.string.rule_edit_redirected
    }
    val error = ruleSaveErrorText(result)
    val fieldKeyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false)
    AdAwayDialog(
        onDismissRequest = onDismiss,
        title = stringResource(title),
        confirmButton = DialogButton(
            stringResource(if (rule == null) R.string.add else R.string.save),
            onClick = { viewModel.save(rule, host, redirection) { saved -> if (saved == RuleSaveResult.Saved) onDismiss() else result = saved } },
            enabled = viewModel.isValid(host, redirection),
        ),
        dismissButton = DialogButton(stringResource(R.string.cancel), onClick = onDismiss),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.rule_host), style = AdAwayType.medium, color = colors.textPrimary)
            AdAwayEditText(host, { host = it; result = null }, keyboardOptions = fieldKeyboard)
            if (type == RuleType.REDIRECTED) {
                Text(stringResource(R.string.rule_address), style = AdAwayType.medium, color = colors.textPrimary, modifier = Modifier.padding(top = 8.dp))
                AdAwayEditText(redirection, { redirection = it; result = null }, keyboardOptions = fieldKeyboard)
            } else {
                Text(stringResource(R.string.rule_wildcards_hint), style = AdAwayType.caption, color = colors.textSecondary)
            }
            error?.let { Text(it, style = AdAwayType.caption, color = colors.primary, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

/** Why a rule could not be saved, if it could not. */
@Composable
fun ruleSaveErrorText(result: RuleSaveResult?): String? = when (result) {
    is RuleSaveResult.Duplicate -> stringResource(R.string.rule_duplicate, ruleTypeLabel(result.type))
    RuleSaveResult.InvalidHost -> stringResource(R.string.rule_invalid_host)
    RuleSaveResult.InvalidAddress -> stringResource(R.string.rule_invalid_address)
    RuleSaveResult.Saved, null -> null
}
