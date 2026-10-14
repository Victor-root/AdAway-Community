package org.adaway.ui

import kotlinx.serialization.Serializable
import org.adaway.data.db.RuleType
import org.adaway.ui.sources.SourceEditViewModel

@Serializable
internal data object Welcome

@Serializable
internal data object Home

@Serializable
internal data object Sources

@Serializable
internal data class SourceEdit(val sourceId: Long = SourceEditViewModel.NEW_SOURCE)

@Serializable
internal data class Lists(val type: RuleType)

@Serializable
internal data object Preferences

@Serializable
internal data object Help

@Serializable
internal data object Updates

@Serializable
internal data object ExcludedApps

@Serializable
internal data object SystemHosts

@Serializable
internal data object DnsLog

@Serializable
internal data object BackupRestore

@Serializable
internal data object AppUpdate
