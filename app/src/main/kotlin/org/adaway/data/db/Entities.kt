package org.adaway.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SourceFormat { BLOCK, ALLOW }

enum class RuleType { BLOCKED, ALLOWED, REDIRECTED }

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
