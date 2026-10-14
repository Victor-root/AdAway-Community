package org.adaway.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.data.db.UserRuleEntity
import org.adaway.system.copyHost
import org.adaway.ui.common.AdAwayDialog
import org.adaway.ui.common.AdAwayEditText
import org.adaway.ui.common.ApplyProgressSnackbar
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.ruleTypeIcon
import org.adaway.ui.common.ruleTypeLabel
import org.adaway.ui.lists.ListsViewModel
import org.adaway.ui.lists.RuleDialog
import org.adaway.ui.lists.splitCatalogLine
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/** What the add and edit dialog is showing. */
private sealed interface RuleEditing {
    data object New : RuleEditing

    data class Existing(val rule: UserRuleEntity) : RuleEditing
}

/**
 * The TV list of a type of rules, the one of the home card picked: the user rules, then the rules
 * of the sources. The actions sit in the header; a rule of the user opens its choices, a rule of a
 * source is copied.
 */
@Composable
fun TvListsScreen(type: RuleType) {
    val viewModel = appViewModel { ListsViewModel(it, type) }
    val userRules by viewModel.userRules.collectAsStateWithLifecycle()
    val sourceRules = viewModel.sourceRules.collectAsLazyPagingItems()
    val showSourceRules by viewModel.showSourceRules.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<RuleEditing?>(null) }
    var selected by remember { mutableStateOf<UserRuleEntity?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val addFocus = remember { FocusRequester() }
    val pendingApply = isApplyPending(viewModel.applier)
    TvScreen(
        header = {
            TvHeader(
                icon = painterResource(ruleTypeIcon(type)),
                title = ruleTypeLabel(type),
                hint = if (pendingApply) stringResource(R.string.configuration_changed) else null,
            ) {
                TvHeaderButton(
                    stringResource(R.string.add),
                    painterResource(R.drawable.ic_add_black_24px),
                    onClick = { editing = RuleEditing.New },
                    modifier = Modifier.focusRequester(addFocus).padding(end = 8.dp),
                )
                TvHeaderButton(
                    stringResource(R.string.tv_dialog_search),
                    painterResource(R.drawable.baseline_search_24),
                    onClick = { searching = true },
                    modifier = Modifier.padding(end = 8.dp),
                )
                TvHeaderButton(
                    stringResource(R.string.lists_show_source_rules),
                    painterResource(R.drawable.ic_collections_bookmark_24dp),
                    onClick = { viewModel.setShowSourceRules(!showSourceRules) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                if (pendingApply) TvApplyButton(viewModel.applier, afterApply = addFocus)
            }
        },
        snackbar = { ApplyProgressSnackbar(viewModel.applier) },
    ) {
        val user = userRules.orEmpty()
        val sourceCount = if (showSourceRules) sourceRules.itemCount else 0
        if (user.isEmpty() && sourceCount == 0) {
            TvEmptyText(stringResource(R.string.tv_lists_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(user, key = { "user-${it.id}" }) { rule ->
                    RuleRow(rule.host, rule.redirection, onClick = { selected = rule }, modifier = Modifier.alpha(if (rule.enabled) 1f else 0.5f))
                }
                items(sourceCount) { index ->
                    val line = sourceRules[index] ?: return@items
                    val (host, redirection) = splitCatalogLine(line)
                    RuleRow(host, redirection, onClick = { context.copyHost(host) })
                }
            }
        }
    }
    selected?.let { rule ->
        TvListDialog(
            title = rule.host,
            items = listOf(
                DialogButton(stringResource(if (rule.enabled) R.string.disable else R.string.enable), onClick = { viewModel.setEnabled(rule, !rule.enabled) }),
                DialogButton(stringResource(R.string.edit), onClick = { editing = RuleEditing.Existing(rule) }),
                DialogButton(stringResource(R.string.delete), onClick = {
                    // The focused row goes away with the rule.
                    addFocus.requestFocus()
                    viewModel.delete(rule)
                }),
                DialogButton(stringResource(R.string.tv_dialog_copy_hostname), onClick = { context.copyHost(rule.host) }),
            ),
            onDismiss = { selected = null },
        )
    }
    editing?.let { current ->
        RuleDialog(type, rule = (current as? RuleEditing.Existing)?.rule, viewModel = viewModel, onDismiss = { editing = null })
    }
    if (searching) SearchDialog(viewModel, onDismiss = { searching = false })
    LaunchedEffect(Unit) { addFocus.requestFocus() }
}

@Composable
private fun RuleRow(host: String, redirection: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAdAwayColors.current
    TvListRow(onClick = onClick, modifier = modifier) {
        Text(host, style = AdAwayType.subtitle1, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        redirection?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = AdAwayType.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Filters the list on the hosts containing the text, with the * and ? wildcards. */
@Composable
private fun SearchDialog(viewModel: ListsViewModel, onDismiss: () -> Unit) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(filter) }
    AdAwayDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lists_search),
        confirmButton = DialogButton(stringResource(R.string.tv_dialog_search), onClick = {
            onDismiss()
            viewModel.search(text)
        }),
        dismissButton = DialogButton(stringResource(R.string.cancel), onClick = onDismiss),
        neutralButton = DialogButton(stringResource(R.string.lists_search_clear), onClick = {
            onDismiss()
            viewModel.search("")
        }),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            AdAwayEditText(
                text,
                { text = it },
                hint = stringResource(R.string.lists_search_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            )
        }
    }
}
