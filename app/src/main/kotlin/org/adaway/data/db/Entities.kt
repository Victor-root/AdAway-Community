package org.adaway.data.db

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SourceFormat { BLOCK, ALLOW }

/** Kept by name: the list screen route carries it, and release builds must still decode it. */
@Keep
enum class RuleType { BLOCKED, ALLOWED, REDIRECTED }

/** Why the last synchronization of a source failed. */
enum class SyncError {
    NO_NETWORK,
    NETWORK,
    HTTP_STATUS,
    TOO_LARGE,
    TOO_MANY_RULES,
    NO_RULES,
    FILE_UNREADABLE,
    STORAGE,
}

/** Sources shipped with the app. Their label follows the app language until the user renames them. */
enum class BuiltInSource(val url: String, val enabledByDefault: Boolean) {
    STEVEN_BLACK("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", enabledByDefault = true),
    PETER_LOWE(
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
        enabledByDefault = false,
    ),
}

@Entity(tableName = "sources", indices = [Index(value = ["url"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    /** Null for a built-in source the user did not rename. */
    val label: String?,
    val builtIn: BuiltInSource?,
    val format: SourceFormat,
    val redirectsEnabled: Boolean,
    val enabled: Boolean,
    /** HTTP validators of the last downloaded version, sent back to only download changes. */
    val entityTag: String? = null,
    val lastModifiedHeader: String? = null,
    /** When the server says the list last changed, in epoch seconds. */
    val onlineModifiedAt: Long? = null,
    /** Last successful synchronization, in epoch seconds. Null until the list is first read. */
    val syncedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val ruleCount: Int = 0,
    val syncError: SyncError? = null,
    /** HTTP status of a [SyncError.HTTP_STATUS] failure. */
    val syncErrorStatus: Int? = null,
    /** Set when a check found a newer version than the synchronized one. */
    @ColumnInfo(defaultValue = "0") val updateAvailable: Boolean = false,
)

@Entity(tableName = "user_rules", indices = [Index(value = ["host"], unique = true)])
data class UserRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val type: RuleType,
    /** Target address, only for [RuleType.REDIRECTED]. */
    val redirection: String?,
    val enabled: Boolean,
)
