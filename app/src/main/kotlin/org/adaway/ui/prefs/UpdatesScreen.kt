package org.adaway.ui.prefs

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.appViewModel

/** The automatic updates. The app updates do not exist yet: their settings are grayed out. */
@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val viewModel = appViewModel(::PreferencesViewModel)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    AdAwayScreen(title = stringResource(R.string.pref_update_title), onBack = onBack) { padding ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding)) {
            if (!notificationsEnabled) {
                Preference(
                    R.drawable.notifications_off_24,
                    stringResource(R.string.pref_update_enable_notifications),
                    stringResource(R.string.pref_update_enable_notifications_summary),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
            }
            PreferenceCategory(stringResource(R.string.pref_update_app_category), divider = !notificationsEnabled, enabled = false)
            SwitchPreference(
                R.drawable.ic_power_settings_new_24dp,
                stringResource(R.string.pref_update_check_app_startup),
                checked = current.appUpdateCheckOnStartup,
                onCheckedChange = {},
                enabled = false,
            )
            SwitchPreference(
                R.drawable.ic_schedule_24dp,
                stringResource(R.string.pref_update_check_app_daily),
                checked = current.appUpdatePeriodicCheck,
                onCheckedChange = {},
                enabled = false,
            )
            Preference(R.drawable.ic_refresh_24dp, stringResource(R.string.pref_update_check_now), enabled = false)
            Preference(R.drawable.ic_get_app_24dp, stringResource(R.string.pref_update_install), enabled = false)

            PreferenceCategory(stringResource(R.string.pref_update_hosts_category))
            SwitchPreference(
                R.drawable.ic_power_settings_new_24dp,
                stringResource(R.string.pref_update_check),
                checked = current.sourcesCheckOnStartup,
                onCheckedChange = { checked -> viewModel.update { it.copy(sourcesCheckOnStartup = checked) } },
            )
            SwitchPreference(
                R.drawable.ic_schedule_24dp,
                stringResource(R.string.pref_update_check_hosts_daily),
                checked = current.sourcesPeriodicCheck,
                onCheckedChange = { checked -> viewModel.update { it.copy(sourcesPeriodicCheck = checked) } },
            )
            // Both depend on the periodic check.
            SwitchPreference(
                R.drawable.ic_sync_24dp,
                stringResource(R.string.pref_update_sync_on_update),
                checked = current.sourcesAutoSync,
                onCheckedChange = { checked -> viewModel.update { it.copy(sourcesAutoSync = checked) } },
                enabled = current.sourcesPeriodicCheck,
            )
            SwitchPreference(
                R.drawable.ic_wifi_24dp,
                stringResource(R.string.pref_update_sync_unmetered_only),
                checked = current.sourcesUnmeteredOnly,
                onCheckedChange = { checked -> viewModel.update { it.copy(sourcesUnmeteredOnly = checked) } },
                enabled = current.sourcesPeriodicCheck,
            )
        }
    }
}
