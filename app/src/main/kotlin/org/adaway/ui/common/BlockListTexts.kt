package org.adaway.ui.common

import android.icu.text.CompactDecimalFormat
import android.icu.text.NumberFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.adaway.R
import org.adaway.blocklist.UpdateError
import org.adaway.data.db.BuiltInSource
import org.adaway.data.db.RuleType
import org.adaway.data.db.SourceEntity
import org.adaway.data.db.SyncError
import org.adaway.sources.UpdateProgress

@Composable
fun builtInSourceLabel(source: BuiltInSource): String = stringResource(
    when (source) {
        BuiltInSource.STEVEN_BLACK -> R.string.source_steven_black
        BuiltInSource.PETER_LOWE -> R.string.source_peter_lowe
    },
)

@Composable
fun sourceLabel(source: SourceEntity): String =
    source.label ?: source.builtIn?.let { builtInSourceLabel(it) } ?: source.url

@Composable
fun sourceStatus(source: SourceEntity): String {
    val error = source.syncError
    val syncedAt = source.syncedAt
    return when {
        !source.enabled -> stringResource(R.string.source_status_disabled)
        error != null -> stringResource(R.string.source_status_failed, syncErrorText(error, source.syncErrorStatus))
        syncedAt == null -> stringResource(R.string.source_status_never_synced)
        source.updateAvailable -> stringResource(R.string.source_status_update_available)
        else -> stringResource(
            R.string.source_status_up_to_date,
            DateUtils.getRelativeTimeSpanString(
                syncedAt * 1000,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString(),
        )
    }
}

@Composable
fun syncErrorText(error: SyncError, httpStatus: Int?): String = when (error) {
    SyncError.NO_NETWORK -> stringResource(R.string.sync_error_no_network)
    SyncError.NETWORK -> stringResource(R.string.sync_error_network)
    SyncError.HTTP_STATUS -> stringResource(R.string.sync_error_http_status, httpStatus ?: 0)
    SyncError.TOO_LARGE -> stringResource(R.string.sync_error_too_large)
    SyncError.TOO_MANY_RULES -> stringResource(R.string.sync_error_too_many_rules)
    SyncError.NO_RULES -> stringResource(R.string.sync_error_no_rules)
    SyncError.FILE_UNREADABLE -> stringResource(R.string.sync_error_file_unreadable)
    SyncError.STORAGE -> stringResource(R.string.sync_error_storage)
}

@Composable
fun ruleCountText(count: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val compact = CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(count)
    return pluralStringResource(R.plurals.source_rule_count, count, compact)
}

@Composable
fun formatCount(count: Long): String = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(count)

@Composable
fun updateProgressText(progress: UpdateProgress): String = when (progress) {
    UpdateProgress.Checking -> stringResource(R.string.progress_checking)
    is UpdateProgress.Downloading -> stringResource(R.string.progress_downloading, sourceLabel(progress.source))
    is UpdateProgress.Reading -> stringResource(R.string.progress_reading, sourceLabel(progress.source))
    UpdateProgress.Applying -> stringResource(R.string.progress_applying)
}

@Composable
fun updateErrorTitle(error: UpdateError): String = stringResource(
    when (error) {
        UpdateError.NO_NETWORK -> R.string.update_error_no_network_title
        UpdateError.DOWNLOAD_FAILED -> R.string.update_error_download_title
        UpdateError.APPLY_FAILED -> R.string.update_error_apply_title
    },
)

@Composable
fun updateErrorText(error: UpdateError): String = stringResource(
    when (error) {
        UpdateError.NO_NETWORK -> R.string.update_error_no_network_text
        UpdateError.DOWNLOAD_FAILED -> R.string.update_error_download_text
        UpdateError.APPLY_FAILED -> R.string.update_error_apply_text
    },
)

@Composable
fun ruleTypeLabel(type: RuleType): String = stringResource(
    when (type) {
        RuleType.BLOCKED -> R.string.rules_blocked
        RuleType.ALLOWED -> R.string.rules_allowed
        RuleType.REDIRECTED -> R.string.rules_redirected
    },
)
