package org.adaway.ui.prefs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.R
import org.adaway.data.settings.SystemAppExclusion
import org.adaway.ui.common.AdAwayAppBar
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.AdAwayTabRow
import org.adaway.ui.common.AppBarAction
import org.adaway.ui.common.AppBarSearchField
import org.adaway.ui.common.adAwaySwitchColors
import org.adaway.ui.common.appViewModel
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.LocalTelevision

/** The order of the ways to exclude the system apps, as the choices name them. */
private val SystemAppExclusions = listOf(SystemAppExclusion.NONE, SystemAppExclusion.ALL_EXCEPT_BROWSERS, SystemAppExclusion.ALL, SystemAppExclusion.CHOSEN)

/**
 * The apps kept out of the VPN, the user ones and the system ones in a tab each, a switch per app.
 * The system tab first tells how its apps are excluded, and lists them when they are chosen one by
 * one. The search keeps the apps whose name contains what is typed, and the menu selects all the
 * apps shown.
 */
@Composable
fun ExcludedAppsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel(::ExcludedAppsViewModel)
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val excluded by viewModel.excluded.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val systemTab by viewModel.systemTab.collectAsStateWithLifecycle()
    val systemExclusion by viewModel.systemExclusion.collectAsStateWithLifecycle()
    val isTelevision = LocalTelevision.current
    var searching by rememberSaveable { mutableStateOf(filter.isNotEmpty()) }
    var choosingSystemExclusion by rememberSaveable { mutableStateOf(false) }
    val systemTabFocus = remember { FocusRequester() }
    // The system apps are listed only when chosen one by one, and the search and menu only go with a list.
    val listShown = !systemTab || systemExclusion == SystemAppExclusion.CHOSEN
    val closeSearch = {
        searching = false
        viewModel.search("")
    }
    // The back key ends the search before leaving the screen, as the arrow of the bar does.
    BackHandler(enabled = searching, onBack = closeSearch)
    AdAwayScreen(
        title = stringResource(R.string.pref_vpn_exclude_user_apps_activity),
        onBack = onBack,
        topBar = if (searching) {
            {
                AdAwayAppBar(onNavigation = closeSearch, actions = { SelectAllMenu(viewModel) }) {
                    AppBarSearchField(filter, stringResource(R.string.excluded_apps_search_hint), KeyboardType.Text, viewModel::search)
                }
            }
        } else {
            null
        },
        actions = {
            if (listShown) {
                AppBarAction(painterResource(R.drawable.baseline_search_24), stringResource(R.string.excluded_apps_search)) { searching = true }
                SelectAllMenu(viewModel)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            AdAwayTabRow(
                titles = listOf(stringResource(R.string.excluded_apps_tab_user), stringResource(R.string.excluded_apps_tab_system)),
                selected = if (systemTab) 1 else 0,
                onSelect = { viewModel.showSystemTab(it == 1) },
                // On TV, moving on a tab opens it, as in the help.
                tabModifier = { index ->
                    if (isTelevision) {
                        Modifier
                            .then(if (index == 1) Modifier.focusRequester(systemTabFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) viewModel.showSystemTab(index == 1) }
                    } else {
                        Modifier
                    }
                },
            )
            if (systemTab) {
                val choices = stringArrayResource(R.array.pref_vpn_excluded_system_apps_entries).toList()
                // Under both tabs, the remote would go up to the nearest one, the user tab: it goes back to its own.
                Box(
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        isTelevision && event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown && systemTabFocus.requestFocus()
                    },
                ) {
                    Preference(
                        R.drawable.ic_android_24dp,
                        stringResource(R.string.pref_vpn_exclude_system_apps),
                        summary = systemExclusion?.let { choices[SystemAppExclusions.indexOf(it)] },
                        onClick = { choosingSystemExclusion = true },
                    )
                }
            }
            val list = apps
            when {
                !listShown -> Text(
                    stringResource(R.string.excluded_apps_system_hint),
                    style = AdAwayType.body2,
                    color = LocalAdAwayColors.current.textSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
                list == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalAdAwayColors.current.primary)
                }
                list.isEmpty() -> Text(
                    stringResource(R.string.excluded_apps_none_found),
                    style = AdAwayType.body2,
                    color = LocalAdAwayColors.current.textPrimary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
                    items(list, key = { it.packageName }) { app ->
                        AppRow(app, app.packageName in excluded) { viewModel.setExcluded(app.packageName, it) }
                    }
                }
            }
        }
    }
    if (choosingSystemExclusion) {
        ChoiceDialog(
            title = stringResource(R.string.pref_vpn_exclude_system_apps),
            choices = stringArrayResource(R.array.pref_vpn_excluded_system_apps_entries).toList(),
            selected = SystemAppExclusions.indexOf(systemExclusion),
            onSelect = { index -> viewModel.setSystemExclusion(SystemAppExclusions[index]) },
            onDismiss = { choosingSystemExclusion = false },
        )
    }
}

/** The menu that excludes or includes all the apps shown, the matching ones only while searching. */
@Composable
private fun SelectAllMenu(viewModel: ExcludedAppsViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        AppBarAction(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.pref_vpn_excluded_apps)) { menuOpen = true }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pref_vpn_exclude_user_apps_select_all)) },
                onClick = { viewModel.setAllExcluded(true); menuOpen = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pref_vpn_exclude_user_apps_deselect_all)) },
                onClick = { viewModel.setAllExcluded(false); menuOpen = false },
            )
        }
    }
}

@Composable
private fun AppRow(app: ExcludableApp, excluded: Boolean, onExcludedChange: (Boolean) -> Unit) {
    val colors = LocalAdAwayColors.current
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { app.info.loadIcon(context.packageManager).toBitmap().asImageBitmap() }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).clickable { onExcludedChange(!excluded) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(16.dp).size(40.dp)) {
            icon?.let { Image(it, contentDescription = stringResource(R.string.pref_vpn_exclude_user_apps_icon), modifier = Modifier.fillMaxSize()) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = AdAwayType.medium, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = AdAwayType.small, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Switch(
            checked = excluded,
            onCheckedChange = onExcludedChange,
            colors = adAwaySwitchColors(),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
