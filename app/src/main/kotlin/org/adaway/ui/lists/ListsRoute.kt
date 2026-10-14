package org.adaway.ui.lists

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.data.db.UserRuleEntity
import org.adaway.system.copyToClipboard
import org.adaway.ui.common.AppButton
import org.adaway.ui.common.ApplyBanner
import org.adaway.ui.common.ChoiceChip
import org.adaway.ui.common.MobileScreen
import org.adaway.ui.common.TvScreen
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.ruleTypeLabel
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.Text as TvText

/** What the add and edit dialog is showing. */
private sealed interface RuleEditing {
    data object New : RuleEditing

    data class Existing(val rule: UserRuleEntity) : RuleEditing
}

@Composable
fun ListsRoute(isTelevision: Boolean, initialType: RuleType, onBack: () -> Unit) {
    val viewModel = appViewModel { ListsViewModel(it, initialType) }
    val type by viewModel.type.collectAsStateWithLifecycle()
    val userRules by viewModel.userRules.collectAsStateWithLifecycle()
    val sourceRules = viewModel.sourceRules.collectAsLazyPagingItems()
    var editing by remember { mutableStateOf<RuleEditing?>(null) }
    if (isTelevision) {
        TvLists(type, userRules.orEmpty(), sourceRules, viewModel, onEdit = { editing = it })
    } else {
        MobileLists(type, userRules.orEmpty(), sourceRules, viewModel, onBack, onEdit = { editing = it })
    }
    editing?.let { current ->
        RuleDialog(
            type = type,
            rule = (current as? RuleEditing.Existing)?.rule,
            viewModel = viewModel,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MobileLists(
    type: RuleType,
    userRules: List<UserRuleEntity>,
    sourceRules: LazyPagingItems<String>,
    viewModel: ListsViewModel,
    onBack: () -> Unit,
    onEdit: (RuleEditing) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val showSourceRules by viewModel.showSourceRules.collectAsStateWithLifecycle()
    val configurationChanged by viewModel.configurationChanged.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val deletedMessage = stringResource(R.string.rule_deleted)
    val undoLabel = stringResource(R.string.undo)
    val copiedMessage = stringResource(R.string.host_copied)
    val copy = { host: String ->
        context.copyToClipboard(host)
        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
    }
    MobileScreen(
        title = stringResource(R.string.lists_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(RuleEditing.New) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.rule_add))
            }
        },
        bottomBar = { if (configurationChanged) ApplyBanner(applying = progress != null, onApply = viewModel::apply) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = type.ordinal) {
                RuleType.entries.forEach { tabType ->
                    Tab(
                        selected = tabType == type,
                        onClick = { viewModel.selectType(tabType) },
                        text = { Text(ruleTypeLabel(tabType)) },
                    )
                }
            }
            OutlinedTextField(
                value = filter,
                onValueChange = viewModel::search,
                placeholder = { Text(stringResource(R.string.lists_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FilterChip(
                selected = showSourceRules,
                onClick = { viewModel.setShowSourceRules(!showSourceRules) },
                label = { Text(stringResource(R.string.lists_show_source_rules)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            RuleList(
                userRules = userRules,
                sourceRules = sourceRules.takeIf { showSourceRules },
                userRule = { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(RuleEditing.Existing(rule)) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Switch(checked = rule.enabled, onCheckedChange = { viewModel.setEnabled(rule, it) })
                        RuleText(rule.host, rule.redirection, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                viewModel.delete(rule)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        deletedMessage,
                                        undoLabel,
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(rule)
                                }
                            },
                        ) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
                sourceRule = { line ->
                    val (host, redirection) = splitCatalogLine(line)
                    RuleText(
                        host,
                        redirection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { copy(host) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                },
                contentPadding = PaddingValues(bottom = 88.dp),
            )
        }
    }
}

@Composable
private fun TvLists(
    type: RuleType,
    userRules: List<UserRuleEntity>,
    sourceRules: LazyPagingItems<String>,
    viewModel: ListsViewModel,
    onEdit: (RuleEditing) -> Unit,
) {
    val context = LocalContext.current
    val showSourceRules by viewModel.showSourceRules.collectAsStateWithLifecycle()
    val configurationChanged by viewModel.configurationChanged.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<UserRuleEntity?>(null) }
    var confirmDeletion by rememberSaveable { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.host_copied)
    val copy = { host: String ->
        context.copyToClipboard(host)
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }
    TvScreen(
        title = stringResource(R.string.lists_title),
        actions = {
            AppButton(stringResource(R.string.rule_add), { onEdit(RuleEditing.New) }, isTelevision = true)
            AppButton(stringResource(R.string.lists_search), { searching = true }, isTelevision = true)
            ChoiceChip(
                stringResource(R.string.lists_show_source_rules),
                selected = showSourceRules,
                onClick = { viewModel.setShowSourceRules(!showSourceRules) },
                isTelevision = true,
            )
            if (configurationChanged) {
                AppButton(stringResource(R.string.apply), viewModel::apply, isTelevision = true, enabled = progress == null)
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleType.entries.forEach { tabType ->
                ChoiceChip(ruleTypeLabel(tabType), tabType == type, { viewModel.selectType(tabType) }, isTelevision = true)
            }
        }
        if (configurationChanged) {
            Text(
                stringResource(if (progress != null) R.string.apply_in_progress else R.string.apply_needed),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        RuleList(
            userRules = userRules,
            sourceRules = sourceRules.takeIf { showSourceRules },
            userRule = { rule ->
                TvListItem(
                    selected = false,
                    onClick = { selected = rule },
                    headlineContent = { TvText(rule.host) },
                    supportingContent = rule.redirection?.let { { TvText(it) } },
                    modifier = Modifier.alpha(if (rule.enabled) 1f else DISABLED_ALPHA),
                )
            },
            sourceRule = { line ->
                val (host, redirection) = splitCatalogLine(line)
                TvListItem(
                    selected = false,
                    onClick = { copy(host) },
                    headlineContent = { TvText(host) },
                    supportingContent = redirection?.let { { TvText(it) } },
                )
            },
            contentPadding = PaddingValues(vertical = 16.dp),
        )
    }
    if (searching) TvSearchDialog(viewModel, onDismiss = { searching = false })
    val rule = selected ?: return
    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            title = { Text(stringResource(R.string.rule_delete_confirm_title)) },
            text = { Text(rule.host) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(rule); confirmDeletion = false; selected = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text(stringResource(R.string.cancel)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(rule.host) },
            text = {
                Column {
                    TextButton(onClick = { viewModel.setEnabled(rule, !rule.enabled); selected = null }) {
                        Text(stringResource(if (rule.enabled) R.string.disable else R.string.enable))
                    }
                    TextButton(onClick = { selected = null; onEdit(RuleEditing.Existing(rule)) }) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(onClick = { confirmDeletion = true }) { Text(stringResource(R.string.delete)) }
                    TextButton(onClick = { copy(rule.host); selected = null }) { Text(stringResource(R.string.copy_host)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TvSearchDialog(viewModel: ListsViewModel, onDismiss: () -> Unit) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(filter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lists_search)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { viewModel.search(text); onDismiss() }) { Text(stringResource(R.string.lists_search)) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.search(""); onDismiss() }) { Text(stringResource(R.string.lists_search_clear)) }
        },
    )
}

/** The user rules, then the rules of the enabled sources when shown. */
@Composable
private fun RuleList(
    userRules: List<UserRuleEntity>,
    sourceRules: LazyPagingItems<String>?,
    userRule: @Composable (UserRuleEntity) -> Unit,
    sourceRule: @Composable (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        sectionTitle(R.string.lists_user_rules)
        if (userRules.isEmpty()) {
            item { EmptyText(R.string.lists_user_rules_empty) }
        }
        items(userRules, key = { "user-${it.id}" }) { userRule(it) }
        if (sourceRules != null) {
            sectionTitle(R.string.lists_source_rules)
            if (sourceRules.itemCount == 0) {
                item { EmptyText(R.string.lists_source_rules_empty) }
            }
            items(sourceRules.itemCount) { index -> sourceRules[index]?.let { sourceRule(it) } }
        }
    }
}

private fun LazyListScope.sectionTitle(@StringRes text: Int) {
    item {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun EmptyText(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RuleText(host: String, redirection: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(host, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        redirection?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A catalog line is `host` or `host address`. */
private fun splitCatalogLine(line: String): Pair<String, String?> {
    val separator = line.indexOf(' ')
    return if (separator < 0) line to null else line.substring(0, separator) to line.substring(separator + 1)
}

private const val DISABLED_ALPHA = 0.6f
