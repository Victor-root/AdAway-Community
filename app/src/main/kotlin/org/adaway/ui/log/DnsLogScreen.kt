package org.adaway.ui.log

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.dnslog.DnsLogError
import org.adaway.engine.Engine
import org.adaway.system.copyToClipboard
import org.adaway.ui.common.AdAwayAlertDialog
import org.adaway.ui.common.AdAwayDialog
import org.adaway.ui.common.AdAwayEditText
import org.adaway.ui.common.AdAwayFab
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.AppBarAction
import org.adaway.ui.common.ApplyConfigurationSnackbar
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.appViewModel
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/**
 * The names asked for while recording, each with its three lists: a tap puts the name in a list, a
 * tap on the list of the user takes it out. A tap on the name opens it, a long press copies it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLogScreen(onBack: () -> Unit) {
    val viewModel = appViewModel(::DnsLogViewModel)
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val blockedRequestsIgnored by viewModel.blockedRequestsIgnored.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var redirecting by rememberSaveable { mutableStateOf<String?>(null) }
    val resources = LocalResources.current
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    AdAwayScreen(
        title = stringResource(R.string.shortcut_dns_requests),
        onBack = onBack,
        actions = {
            AppBarAction(painterResource(R.drawable.baseline_sort_by_alpha_24), stringResource(R.string.log_sort)) {
                Toast.makeText(context, resources.getString(viewModel.toggleSort().label), Toast.LENGTH_SHORT).show()
            }
            AppBarAction(painterResource(R.drawable.outline_delete_24), stringResource(R.string.log_clear), viewModel::clear)
        },
        floatingActionButton = {
            AdAwayFab(
                painterResource(if (recording) R.drawable.ic_pause_24dp else R.drawable.ic_record_24dp),
                stringResource(R.string.log_toggle_recording_description),
                viewModel::toggleRecording,
            )
        },
        snackbar = { ApplyConfigurationSnackbar(viewModel.applier) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val list = entries.orEmpty()
            if (list.isEmpty()) {
                EmptyLog(blockedRequestsIgnored)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp),
                ) {
                    items(list, key = DnsLogEntry::host) { entry ->
                        DnsLogRow(
                            entry,
                            onSelect = { type ->
                                when {
                                    entry.type == type -> if (entry.userType == type) viewModel.removeHost(entry.host)
                                    type == RuleType.REDIRECTED -> redirecting = entry.host
                                    else -> viewModel.putHost(entry.host, type)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    redirecting?.let { host ->
        RedirectDialog(onRedirect = { address -> viewModel.putHost(host, RuleType.REDIRECTED, address) }, onDismiss = { redirecting = null })
    }
    error?.let { DnsLogErrorDialog(it, onDismiss = viewModel::dismissError) }
}

/** Scrollable, so that the pull to refresh works on it too. */
@Composable
private fun EmptyLog(blockedRequestsIgnored: Boolean) {
    val text = stringResource(R.string.log_start_recording) + if (blockedRequestsIgnored) stringResource(R.string.log_blocked_requests_ignored) else ""
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text,
            style = AdAwayType.body2,
            color = LocalAdAwayColors.current.textPrimary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DnsLogRow(entry: DnsLogEntry, onSelect: (RuleType) -> Unit) {
    val context = LocalContext.current
    val copied = stringResource(R.string.host_copied)
    Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ListIcon(R.drawable.baseline_block_24, R.string.log_block, entry.type == RuleType.BLOCKED) { onSelect(RuleType.BLOCKED) }
        ListIcon(R.drawable.baseline_check_24, R.string.log_allow, entry.type == RuleType.ALLOWED) { onSelect(RuleType.ALLOWED) }
        ListIcon(R.drawable.baseline_compare_arrows_24, R.string.log_redirect, entry.type == RuleType.REDIRECTED) { onSelect(RuleType.REDIRECTED) }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .combinedClickable(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, "http://${entry.host}".toUri()))
                        } catch (_: ActivityNotFoundException) {
                            // No browser on the device.
                        }
                    },
                    onLongClick = {
                        context.copyToClipboard(entry.host)
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                )
                .padding(start = 8.dp, end = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(entry.host, style = AdAwayType.body2, color = LocalAdAwayColors.current.textPrimary, maxLines = 1)
        }
    }
}

/** Red when the host is in that list. */
@Composable
private fun ListIcon(@DrawableRes icon: Int, @StringRes description: Int, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAdAwayColors.current
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(description),
            tint = if (active) colors.primary else colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RedirectDialog(onRedirect: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAdAwayColors.current
    var address by rememberSaveable { mutableStateOf("") }
    AdAwayDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.log_redirect_dialog_title),
        confirmButton = DialogButton(
            stringResource(R.string.add),
            onClick = {
                onDismiss()
                onRedirect(address)
            },
            enabled = Engine.normalizeIpAddress(address) != null,
        ),
        dismissButton = DialogButton(stringResource(R.string.cancel), onClick = onDismiss),
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.rule_address), style = AdAwayType.medium, color = colors.textPrimary)
            AdAwayEditText(address, { address = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
        }
    }
}

@Composable
fun DnsLogErrorDialog(error: DnsLogError, onDismiss: () -> Unit) {
    AdAwayAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (error == DnsLogError.ROOT_DENIED) R.string.root_error_denied_title else R.string.shortcut_dns_requests),
        message = stringResource(if (error == DnsLogError.ROOT_DENIED) R.string.root_error_denied_text else R.string.log_capture_failed),
        confirmButton = DialogButton(stringResource(R.string.close), onClick = onDismiss),
    )
}
