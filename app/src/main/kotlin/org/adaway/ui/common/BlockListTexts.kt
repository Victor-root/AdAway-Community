package org.adaway.ui.common

import android.content.res.Resources
import android.icu.text.CompactDecimalFormat
import android.icu.text.NumberFormat
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.math.pow
import kotlin.math.roundToLong
import org.adaway.R
import org.adaway.blocklist.UpdateError
import org.adaway.data.db.BuiltInSource
import org.adaway.data.db.RuleType
import org.adaway.data.db.SourceEntity
import org.adaway.data.db.SyncError
import org.adaway.root.RootError
import org.adaway.sources.UpdateProgress

@StringRes
private fun builtInSourceLabelRes(source: BuiltInSource): Int = when (source) {
    BuiltInSource.STEVEN_BLACK -> R.string.source_steven_black
    BuiltInSource.PETER_LOWE -> R.string.source_peter_lowe
}

@Composable
fun builtInSourceLabel(source: BuiltInSource): String = stringResource(builtInSourceLabelRes(source))

/** The user's name of the source, else the built-in one, else its address. */
fun sourceLabel(source: SourceEntity, resources: Resources): String =
    source.label ?: source.builtIn?.let { resources.getString(builtInSourceLabelRes(it)) } ?: source.url

@Composable
fun sourceLabel(source: SourceEntity): String = sourceLabel(source, LocalResources.current)

@Composable
fun sourceStatus(source: SourceEntity): String {
    val error = source.syncError
    val syncedAt = source.syncedAt
    return when {
        !source.enabled -> stringResource(R.string.source_status_disabled)
        error != null -> stringResource(R.string.source_status_failed, syncErrorText(error, source.syncErrorStatus))
        syncedAt == null -> stringResource(R.string.source_status_never_synced)
        source.updateAvailable -> stringResource(R.string.source_status_update_available)
        else -> {
            val elapsed = System.currentTimeMillis() - syncedAt * 1000
            val checkedAt = if (elapsed < DateUtils.MINUTE_IN_MILLIS) {
                stringResource(R.string.just_now)
            } else {
                DateUtils.getRelativeTimeSpanString(syncedAt * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                    .toString()
                    .replaceFirstChar { it.lowercase(LocalConfiguration.current.locales[0]) }
            }
            stringResource(R.string.source_status_up_to_date, checkedAt)
        }
    }
}

/**
 * The update status of a source, as the 6.x source list tells it: how long the online version has
 * been there, or since when the list is installed when the server gives no date.
 */
@Composable
fun sourceUpdateText(source: SourceEntity): String {
    val error = source.syncError
    val onlineModifiedAt = source.onlineModifiedAt
    val syncedAt = source.syncedAt
    return when {
        !source.enabled -> stringResource(R.string.hosts_source_disabled)
        error != null -> stringResource(R.string.source_status_failed, syncErrorText(error, source.syncErrorStatus))
        onlineModifiedAt != null -> stringResource(
            when {
                syncedAt == null -> R.string.hosts_source_last_update
                source.updateAvailable -> R.string.hosts_source_need_update
                else -> R.string.hosts_source_up_to_date
            },
            approximateDelay(onlineModifiedAt),
        )
        syncedAt != null -> stringResource(R.string.hosts_source_installed, approximateDelay(syncedAt))
        else -> stringResource(R.string.hosts_source_unknown_status)
    }
}

/** A few minutes, then hours, days and 30-day months. */
@Composable
private fun approximateDelay(epochSeconds: Long): String {
    val minutes = (System.currentTimeMillis() / 1000 - epochSeconds).coerceAtLeast(0) / 60
    val hours = (minutes / 60).toInt()
    val days = hours / 24
    return when {
        minutes < 60 -> stringResource(R.string.hosts_source_few_minutes)
        hours < 24 -> pluralStringResource(R.plurals.hosts_source_hours, hours, hours)
        days < 30 -> pluralStringResource(R.plurals.hosts_source_days, days, days)
        else -> pluralStringResource(R.plurals.hosts_source_months, days / 30, days / 30)
    }
}

/** The entry count rounded to its thousands with a k, M or G suffix, as "73k". */
@Composable
fun hostCountText(count: Int): String {
    val digits = count.toString().length
    val prefix = ((digits - 1) / 3 - 1).coerceAtMost(QUANTITY_PREFIXES.lastIndex)
    val text = if (prefix < 0) {
        count.toString()
    } else {
        (count / 10.0.pow((prefix + 1) * 3)).roundToLong().toString() + QUANTITY_PREFIXES[prefix]
    }
    return stringResource(R.string.hosts_count, text)
}

private val QUANTITY_PREFIXES = listOf("k", "M", "G")

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
fun rootErrorTitle(error: RootError): String = stringResource(
    when (error) {
        RootError.ROOT_DENIED -> R.string.root_error_denied_title
        RootError.PRIVATE_FILE -> R.string.update_error_apply_title
        RootError.NOT_ENOUGH_SPACE -> R.string.root_error_space_title
        RootError.COPY_FAILED -> R.string.root_error_copy_title
        RootError.REVERT_FAILED -> R.string.root_error_revert_title
    },
)

@Composable
fun rootErrorText(error: RootError): String = stringResource(
    when (error) {
        RootError.ROOT_DENIED -> R.string.root_error_denied_text
        RootError.PRIVATE_FILE -> R.string.update_error_apply_text
        RootError.NOT_ENOUGH_SPACE -> R.string.root_error_space_text
        RootError.COPY_FAILED -> R.string.root_error_copy_text
        RootError.REVERT_FAILED -> R.string.root_error_revert_text
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
