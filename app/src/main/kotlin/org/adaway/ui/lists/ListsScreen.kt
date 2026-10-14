package org.adaway.ui.lists

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.delay
import org.adaway.R
import org.adaway.blocklist.RuleSaveResult
import org.adaway.data.db.RuleType
import org.adaway.data.db.UserRuleEntity
import org.adaway.system.copyToClipboard
import org.adaway.ui.common.AdAwayAppBar
import org.adaway.ui.common.AdAwayDialog
import org.adaway.ui.common.AdAwayEditText
import org.adaway.ui.common.AdAwayFab
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.AdAwaySnackbar
import org.adaway.ui.common.AppBarAction
import org.adaway.ui.common.AppBarTitle
import org.adaway.ui.common.ApplyConfigurationSnackbar
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.adAwayCheckboxColors
import org.adaway.ui.common.ruleTypeLabel
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.RedNavigationBar

/** How long the undo action of a deletion stays offered. */
private const val UNDO_MILLIS = 10_000L
/** Background of the entry the actions apply to. */
private val SelectedBackground = Color(0x78B71C1C)

/**
 * The phone lists: the entries of a type, the user ones first then the sources' ones, the type
 * picked in the red bottom bar. A long press on an entry of the user offers to edit or delete it,
 * on an entry of a source copies it.
 */
@Composable
fun ListsScreen(type: RuleType, userRules: List<UserRuleEntity>, sourceRules: LazyPagingItems<String>, viewModel: ListsViewModel, onBack: () -> Unit) {
    RedNavigationBar()
    val context = LocalContext.current
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val showSourceRules by viewModel.showSourceRules.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(filter.isNotEmpty()) }
    var selected by remember { mutableStateOf<UserRuleEntity?>(null) }
    var editing by remember { mutableStateOf<UserRuleEntity?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleted by remember { mutableStateOf<UserRuleEntity?>(null) }
    val copiedMessage = stringResource(R.string.host_copied)
    LaunchedEffect(deleted) {
        if (deleted == null) return@LaunchedEffect
        delay(UNDO_MILLIS)
        deleted = null
    }
    LaunchedEffect(type) { selected = null }
    AdAwayScreen(
        title = stringResource(R.string.lists_title),
        onBack = onBack,
        topBar = {
            val rule = selected
            when {
                rule != null -> AdAwayAppBar(
                    onNavigation = { selected = null },
                    navigationDescription = stringResource(R.string.cancel),
                    actions = {
                        AppBarAction(painterResource(R.drawable.outline_create_24), stringResource(R.string.edit)) {
                            editing = rule
                            selected = null
                        }
                        AppBarAction(painterResource(R.drawable.outline_delete_24), stringResource(R.string.delete)) {
                            viewModel.delete(rule)
                            deleted = rule
                            selected = null
                        }
                    },
                ) { AppBarTitle(stringResource(R.string.rule_actions_title)) }
                searching -> AdAwayAppBar(onNavigation = { searching = false; viewModel.search("") }) {
                    SearchField(filter, viewModel::search)
                }
                else -> AdAwayAppBar(
                    onNavigation = onBack,
                    actions = {
                        AppBarAction(painterResource(R.drawable.baseline_search_24), stringResource(R.string.lists_search)) { searching = true }
                        AppBarAction(painterResource(R.drawable.ic_collections_bookmark_24dp), stringResource(R.string.lists_show_source_rules)) {
                            viewModel.setShowSourceRules(!showSourceRules)
                        }
                    },
                ) { AppBarTitle(stringResource(R.string.lists_title)) }
            }
        },
        bottomBar = { TypeNavigation(type, viewModel::selectType) },
        floatingActionButton = {
            AdAwayFab(painterResource(R.drawable.ic_add_black_24px), stringResource(R.string.rule_add)) { adding = true }
        },
        snackbar = {
            val rule = deleted
            if (rule != null) {
                AdAwaySnackbar(stringResource(R.string.rule_deleted), actionLabel = stringResource(R.string.undo), onAction = {
                    viewModel.restore(rule)
                    deleted = null
                })
            } else {
                ApplyConfigurationSnackbar(viewModel.applier)
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp)) {
            items(userRules, key = { "user-${it.id}" }) { rule ->
                EntryRow(
                    host = rule.host,
                    redirection = rule.redirection.takeIf { type == RuleType.REDIRECTED },
                    twoRows = type == RuleType.REDIRECTED,
                    checked = rule.enabled,
                    editable = true,
                    selected = rule.id == selected?.id,
                    onToggle = { viewModel.setEnabled(rule, !rule.enabled) },
                    onLongClick = { if (selected == null) selected = rule },
                )
            }
            if (showSourceRules) {
                items(sourceRules.itemCount) { index ->
                    val line = sourceRules[index] ?: return@items
                    val (host, redirection) = splitCatalogLine(line)
                    EntryRow(
                        host = host,
                        redirection = redirection,
                        twoRows = type == RuleType.REDIRECTED,
                        checked = true,
                        editable = false,
                        selected = false,
                        onToggle = {},
                        onLongClick = {
                            context.copyToClipboard(host)
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
    if (adding) MobileRuleDialog(type, rule = null, viewModel, onDismiss = { adding = false })
    editing?.let { MobileRuleDialog(type, rule = it, viewModel, onDismiss = { editing = null }) }
}

@Composable
private fun RowScope.SearchField(filter: String, onSearch: (String) -> Unit) {
    val colors = LocalAdAwayColors.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(modifier = Modifier.weight(1f).padding(start = 20.dp, end = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (filter.isEmpty()) {
            Text(stringResource(R.string.lists_search_hint), style = AdAwayType.subtitle1, color = colors.onPrimary.copy(alpha = 0.7f))
        }
        BasicTextField(
            value = filter,
            onValueChange = onSearch,
            singleLine = true,
            textStyle = AdAwayType.subtitle1.copy(color = colors.onPrimary),
            cursorBrush = SolidColor(colors.onPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    host: String,
    redirection: String?,
    twoRows: Boolean,
    checked: Boolean,
    editable: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalAdAwayColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (twoRows) 72.dp else 48.dp)
            .background(if (selected) SelectedBackground else Color.Transparent)
            .combinedClickable(onClick = { if (editable) onToggle() }, onLongClick = onLongClick),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (editable) { _ -> onToggle() } else null,
            enabled = editable,
            colors = adAwayCheckboxColors(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
        )
        Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 72.dp, end = 16.dp)) {
            Text(host, style = AdAwayType.subtitle1, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (twoRows) {
                Text(redirection.orEmpty(), style = AdAwayType.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The red footer of the three types, a white bar sliding under the selected one. */
@Composable
private fun TypeNavigation(type: RuleType, onSelect: (RuleType) -> Unit) {
    val colors = LocalAdAwayColors.current
    Column(modifier = Modifier.fillMaxWidth().background(colors.primary)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            val itemWidth = maxWidth / RuleType.entries.size
            Row(modifier = Modifier.fillMaxSize()) {
                RuleType.entries.forEach { item ->
                    val checked = item == type
                    val icon = when (item) {
                        RuleType.BLOCKED -> R.drawable.baseline_block_24
                        RuleType.ALLOWED -> R.drawable.baseline_check_24
                        RuleType.REDIRECTED -> R.drawable.baseline_compare_arrows_24
                    }
                    val tint = if (checked) colors.onPrimary else colors.onPrimary.copy(alpha = 0.7f)
                    Column(
                        modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelect(item) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Text(ruleTypeLabel(item), style = if (checked) AdAwayType.small else AdAwayType.caption, color = tint)
                    }
                }
            }
            val indicatorX by animateDpAsState(itemWidth * type.ordinal + (itemWidth - 40.dp) / 2, tween(200), label = "indicator")
            Spacer(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(indicatorX.roundToPx(), 0) }
                    .size(width = 40.dp, height = 4.dp)
                    .background(colors.onPrimary, RoundedCornerShape(2.dp)),
            )
        }
        Spacer(modifier = Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** The 6.x add and edit dialog: the name, and the address of a redirection. */
@Composable
private fun MobileRuleDialog(type: RuleType, rule: UserRuleEntity?, viewModel: ListsViewModel, onDismiss: () -> Unit) {
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
