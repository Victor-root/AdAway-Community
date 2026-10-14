package org.adaway.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.blocklist.RuleSaveResult
import org.adaway.data.db.RuleType
import org.adaway.data.db.UserRuleEntity
import org.adaway.ui.common.ruleTypeLabel

/** Adds a rule of [type], or edits [rule]. */
@Composable
fun RuleDialog(type: RuleType, rule: UserRuleEntity?, viewModel: ListsViewModel, onDismiss: () -> Unit) {
    var host by rememberSaveable { mutableStateOf(rule?.host.orEmpty()) }
    var redirection by rememberSaveable { mutableStateOf(rule?.redirection.orEmpty()) }
    var result by remember { mutableStateOf<RuleSaveResult?>(null) }
    val title = when (type) {
        RuleType.BLOCKED -> if (rule == null) R.string.rule_add_blocked else R.string.rule_edit_blocked
        RuleType.ALLOWED -> if (rule == null) R.string.rule_add_allowed else R.string.rule_edit_allowed
        RuleType.REDIRECTED -> if (rule == null) R.string.rule_add_redirected else R.string.rule_edit_redirected
    }
    val error = ruleSaveErrorText(result)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; result = null },
                    label = { Text(stringResource(R.string.rule_host)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
                if (type == RuleType.REDIRECTED) {
                    OutlinedTextField(
                        value = redirection,
                        onValueChange = { redirection = it; result = null },
                        label = { Text(stringResource(R.string.rule_address)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    )
                } else {
                    Text(
                        stringResource(R.string.rule_wildcards_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.save(rule, host, redirection) { saved ->
                        if (saved == RuleSaveResult.Saved) onDismiss() else result = saved
                    }
                },
                enabled = viewModel.isValid(host, redirection),
            ) {
                Text(stringResource(if (rule == null) R.string.add else R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Why a rule could not be saved, if it could not. */
@Composable
fun ruleSaveErrorText(result: RuleSaveResult?): String? = when (result) {
    is RuleSaveResult.Duplicate -> stringResource(R.string.rule_duplicate, ruleTypeLabel(result.type))
    RuleSaveResult.InvalidHost -> stringResource(R.string.rule_invalid_host)
    RuleSaveResult.InvalidAddress -> stringResource(R.string.rule_invalid_address)
    RuleSaveResult.Saved, null -> null
}
