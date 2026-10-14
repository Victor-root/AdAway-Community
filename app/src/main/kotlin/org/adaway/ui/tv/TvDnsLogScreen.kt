package org.adaway.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.system.copyHost
import org.adaway.ui.common.ApplyProgressSnackbar
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.ruleTypeIcon
import org.adaway.ui.log.DnsLogEntry
import org.adaway.ui.log.DnsLogErrorDialog
import org.adaway.ui.log.DnsLogViewModel
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/** How often the names show up while recording. */
private const val REFRESH_MILLIS = 1_000L
private const val FADE_MILLIS = 300

/**
 * The TV DNS log: the names asked for while recording, each marked with the list it is in. A name
 * opens the choices to put it in or take it out of the allowed and blocked lists, or to copy it.
 */
@Composable
fun TvDnsLogScreen() {
    val viewModel = appViewModel(::DnsLogViewModel)
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val vpnInactive by viewModel.vpnInactive.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var selected by remember { mutableStateOf<DnsLogEntry?>(null) }
    val refreshFocus = remember { FocusRequester() }
    val pendingApply = isApplyPending(viewModel.applier)
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(recording) {
        if (recording) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(REFRESH_MILLIS)
                    viewModel.refresh()
                }
            }
        }
    }
    TvScreen(
        header = {
            TvHeader(
                icon = painterResource(R.drawable.ic_record_24dp),
                title = stringResource(R.string.shortcut_dns_requests),
                horizontalPadding = 24.dp,
                titlePadding = 0.dp,
                actionsCentered = false,
                hint = if (pendingApply) stringResource(R.string.configuration_changed) else null,
            ) {
                TvHeaderButton(
                    stringResource(R.string.menu_refresh),
                    painterResource(R.drawable.ic_sync_24dp),
                    onClick = viewModel::refresh,
                    modifier = Modifier.focusRequester(refreshFocus).padding(end = 8.dp),
                )
                TvHeaderButton(
                    stringResource(
                        when {
                            vpnInactive -> R.string.log_start_recording_vpn_inactive
                            recording -> R.string.log_stop_recording
                            else -> R.string.log_start_recording_button
                        },
                    ),
                    painterResource(R.drawable.ic_record_24dp),
                    onClick = viewModel::toggleRecording,
                    enabled = !vpnInactive,
                    modifier = Modifier.padding(end = 8.dp),
                )
                TvHeaderButton(
                    stringResource(R.string.log_clear),
                    painterResource(R.drawable.baseline_clear_all_24),
                    onClick = viewModel::clear,
                    modifier = Modifier.padding(end = 8.dp),
                )
                if (pendingApply) TvApplyButton(viewModel.applier, afterApply = refreshFocus)
            }
        },
        snackbar = { ApplyProgressSnackbar(viewModel.applier) },
    ) {
        val list = entries.orEmpty()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(list, key = DnsLogEntry::host) { entry -> LogRow(entry, onClick = { selected = entry }) }
        }
        AnimatedVisibility(visible = entries != null && list.isEmpty(), enter = fadeIn(tween(FADE_MILLIS)), exit = fadeOut(tween(FADE_MILLIS))) {
            TvEmptyText(stringResource(R.string.log_start_recording))
        }
    }
    selected?.let { entry ->
        val allowed = entry.userType == RuleType.ALLOWED
        val blocked = entry.userType == RuleType.BLOCKED
        TvListDialog(
            title = entry.host,
            items = listOf(
                DialogButton(stringResource(if (allowed) R.string.tv_dialog_remove_from_allowed else R.string.tv_dialog_add_to_allowed), onClick = {
                    if (allowed) viewModel.removeHost(entry.host) else viewModel.putHost(entry.host, RuleType.ALLOWED)
                }),
                DialogButton(stringResource(if (blocked) R.string.tv_dialog_remove_from_blocked else R.string.tv_dialog_add_to_blocked), onClick = {
                    if (blocked) viewModel.removeHost(entry.host) else viewModel.putHost(entry.host, RuleType.BLOCKED)
                }),
                DialogButton(stringResource(R.string.tv_dialog_copy_hostname), onClick = {
                    context.copyHost(entry.host)
                }),
            ),
            onDismiss = { selected = null },
        )
    }
    error?.let { DnsLogErrorDialog(it, onDismiss = viewModel::dismissError) }
    LaunchedEffect(Unit) { refreshFocus.requestFocus() }
}

@Composable
private fun LogRow(entry: DnsLogEntry, onClick: () -> Unit) {
    val colors = LocalAdAwayColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListMark(entry.type)
        Text(
            entry.host,
            style = AdAwayType.subtitle1,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
    }
}

/** A red block mark, a green check mark, or a ring for the other names. */
@Composable
private fun ListMark(type: RuleType?) {
    val colors = LocalAdAwayColors.current
    val description = stringResource(R.string.tv_log_entry_status_description)
    when (type) {
        RuleType.BLOCKED -> Icon(painterResource(ruleTypeIcon(type)), description, tint = colors.logBlocked, modifier = Modifier.size(32.dp))
        RuleType.ALLOWED -> Icon(painterResource(ruleTypeIcon(type)), description, tint = colors.logAllowed, modifier = Modifier.size(32.dp))
        RuleType.REDIRECTED, null -> Box(modifier = Modifier.size(32.dp).border(2.dp, colors.textPrimary, CircleShape))
    }
}
