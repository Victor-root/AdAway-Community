package org.adaway.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.adaway.data.db.RuleType
import org.adaway.ui.common.appViewModel
import org.adaway.ui.help.HelpScreen
import org.adaway.ui.home.HomeNavigation
import org.adaway.ui.home.HomeRoute
import org.adaway.ui.lists.ListsRoute
import org.adaway.ui.log.DnsLogScreen
import org.adaway.ui.prefs.ExcludedAppsScreen
import org.adaway.ui.prefs.HostsFileScreen
import org.adaway.ui.prefs.PreferencesNavigation
import org.adaway.ui.prefs.PreferencesScreen
import org.adaway.ui.prefs.UpdatesScreen
import org.adaway.ui.sources.SourceEditRoute
import org.adaway.ui.sources.SourceEditViewModel
import org.adaway.ui.sources.SourcesNavigation
import org.adaway.ui.sources.SourcesRoute
import org.adaway.ui.theme.AdAwayTheme
import org.adaway.ui.welcome.WelcomeRoute

@Serializable
private data object Welcome

@Serializable
private data object Home

@Serializable
private data object Sources

@Serializable
private data class SourceEdit(val sourceId: Long = SourceEditViewModel.NEW_SOURCE)

@Serializable
private data class Lists(val type: RuleType)

@Serializable
private data object Preferences

@Serializable
private data object Help

@Serializable
private data object Updates

@Serializable
private data object ExcludedApps

@Serializable
private data object SystemHosts

@Serializable
private data object DnsLog

@Composable
fun AdAwayApp(isTelevision: Boolean) {
    val viewModel = appViewModel(::MainViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The window background shows the brand color until the app data is ready.
    val settings = (state as? MainState.Ready)?.settings ?: return
    AdAwayTheme(themeMode = settings.themeMode, isTelevision = isTelevision) {
        val navController = rememberNavController()
        val startDestination = remember { if (settings.welcomeCompleted) Home else Welcome }
        NavHost(navController = navController, startDestination = startDestination) {
            composable<Welcome> {
                WelcomeRoute(
                    isTelevision = isTelevision,
                    onFinished = {
                        navController.navigate(Home) { popUpTo<Welcome> { inclusive = true } }
                    },
                )
            }
            composable<Home> {
                HomeRoute(
                    isTelevision = isTelevision,
                    navigation = HomeNavigation(
                        openSources = { navController.navigate(Sources) },
                        openLists = { type -> navController.navigate(Lists(type)) },
                        openPreferences = { navController.navigate(Preferences) },
                        openHelp = { navController.navigate(Help) },
                        openLog = { navController.navigate(DnsLog) },
                    ),
                )
            }
            composable<Sources> {
                SourcesRoute(
                    isTelevision = isTelevision,
                    navigation = SourcesNavigation(
                        back = navController::popBackStack,
                        add = { navController.navigate(SourceEdit()) },
                        edit = { id -> navController.navigate(SourceEdit(id)) },
                    ),
                )
            }
            composable<SourceEdit> { entry ->
                SourceEditRoute(
                    isTelevision = isTelevision,
                    sourceId = entry.toRoute<SourceEdit>().sourceId,
                    onDone = navController::popBackStack,
                )
            }
            composable<Lists> { entry ->
                ListsRoute(
                    isTelevision = isTelevision,
                    initialType = entry.toRoute<Lists>().type,
                    onBack = navController::popBackStack,
                )
            }
            composable<Preferences> {
                PreferencesScreen(
                    PreferencesNavigation(
                        back = navController::popBackStack,
                        openUpdates = { navController.navigate(Updates) },
                        openExcludedApps = { navController.navigate(ExcludedApps) },
                        openHostsFile = { navController.navigate(SystemHosts) },
                    ),
                )
            }
            composable<Help> { HelpScreen(onBack = navController::popBackStack) }
            composable<Updates> { UpdatesScreen(onBack = navController::popBackStack) }
            composable<ExcludedApps> { ExcludedAppsScreen(onBack = navController::popBackStack) }
            composable<SystemHosts> { HostsFileScreen(onBack = navController::popBackStack) }
            composable<DnsLog> { DnsLogScreen(onBack = navController::popBackStack) }
        }
    }
}
