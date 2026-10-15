package org.adaway

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.adaway.backup.BackupFile
import org.adaway.blocking.BlockingController
import org.adaway.blocklist.BlockListCompiler
import org.adaway.blocklist.BlockListState
import org.adaway.blocklist.BlockListUpdater
import org.adaway.blocklist.StatusMessages
import org.adaway.blocklist.UserRuleRepository
import org.adaway.data.DataInitializer
import org.adaway.data.db.AppDatabase
import org.adaway.data.legacy.LegacyAppData
import org.adaway.data.legacy.LegacyConversion
import org.adaway.data.settings.SettingsRepository
import org.adaway.dnslog.DnsLog
import org.adaway.engine.Engine
import org.adaway.engine.EngineFiles
import org.adaway.root.RootController
import org.adaway.root.WebServer
import org.adaway.sources.SourceDownloader
import org.adaway.sources.SourceRepository
import org.adaway.sources.SourceSync
import org.adaway.system.DiagnosticLog
import org.adaway.update.AppUpdater
import org.adaway.system.RootAccess
import org.adaway.vpn.VpnController

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val Context.blockListDataStore by preferencesDataStore(name = "block_list")

/** Holds the app-wide services, created once by [AdAwayApplication]. */
class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    /** Scope of the work that must outlive the screens, such as an update the user started. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository = SettingsRepository(context.settingsDataStore, applicationScope)
    val database = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()
    val engineFiles = EngineFiles(context)
    val blockListState = BlockListState(context.blockListDataStore)
    private val legacyConversion = LegacyConversion(Engine)
    val dataInitializer = DataInitializer(
        database = database,
        settingsRepository = settingsRepository,
        legacyAppData = LegacyAppData(context),
        legacyConversion = legacyConversion,
    )
    val backupFile = BackupFile(database, legacyConversion, blockListState, applicationContext.resources)
    val rootAccess = RootAccess()
    val diagnosticLog = DiagnosticLog(context.filesDir, applicationScope, settingsRepository.settings)
    val vpnController = VpnController(applicationContext, settingsRepository, diagnosticLog)
    val statusMessages = StatusMessages()
    val rootController = RootController(applicationContext.resources, rootAccess, database, engineFiles, settingsRepository, statusMessages)
    val webServer = WebServer(applicationContext, rootAccess)
    val dnsLog = DnsLog(applicationContext, settingsRepository, rootAccess)
    val appUpdater = AppUpdater(applicationContext)
    val blockingController = BlockingController(settingsRepository, vpnController, rootController, statusMessages)
    val sourceRepository = SourceRepository(database, engineFiles, blockListState)
    val userRuleRepository = UserRuleRepository(database, Engine, blockListState)
    val blockListUpdater = BlockListUpdater(
        scope = applicationScope,
        sourceSync = SourceSync(database, SourceDownloader(context), engineFiles),
        compiler = BlockListCompiler(database, engineFiles, blockListState),
        statusMessages = statusMessages,
        diagnosticLog = diagnosticLog,
        onApplied = blockingController::onBlockListApplied,
    )
}
