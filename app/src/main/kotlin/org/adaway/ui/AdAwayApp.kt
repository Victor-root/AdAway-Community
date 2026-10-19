package org.adaway.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import org.adaway.data.db.RuleType
import org.adaway.ui.common.ScreenTransitions
import org.adaway.ui.common.appViewModel
import org.adaway.ui.help.HelpScreen
import org.adaway.ui.help.NoticesScreen
import org.adaway.ui.home.HomeNavigation
import org.adaway.ui.home.HomeRoute
import org.adaway.ui.home.HomeScreen
import org.adaway.ui.lists.ListsRoute
import org.adaway.ui.log.DnsLogScreen
import org.adaway.ui.prefs.BackupRestoreScreen
import org.adaway.ui.prefs.ExcludedAppsScreen
import org.adaway.ui.prefs.HostsFileScreen
import org.adaway.ui.prefs.PreferencesNavigation
import org.adaway.ui.prefs.PreferencesScreen
import org.adaway.ui.prefs.UpdatesScreen
import org.adaway.ui.sources.SourceEditRoute
import org.adaway.ui.sources.SourceEditScreen
import org.adaway.ui.sources.SourcesNavigation
import org.adaway.ui.sources.SourcesRoute
import org.adaway.ui.theme.AdAwayTheme
import org.adaway.ui.tv.tvDestinations
import org.adaway.ui.update.UpdateScreen
import org.adaway.ui.welcome.WelcomeRoute
import org.adaway.ui.welcome.WelcomeScreen

/** [destination] holds the screen a shortcut or a notification asks for, emptied once shown. */
@Composable
fun AdAwayApp(isTelevision: Boolean, destination: MutableStateFlow<Destination?>) {
    val viewModel = appViewModel(::MainViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The window background shows the brand color until the app data is ready.
    val settings = (state as? MainState.Ready)?.settings ?: return
    AdAwayTheme(themeMode = settings.themeMode, isTelevision = isTelevision) {
        val navController = rememberNavController()
        val startDestination = remember { if (settings.welcomeCompleted) Home else Welcome }
        val requested by destination.collectAsStateWithLifecycle()
        LaunchedEffect(requested, settings.welcomeCompleted) {
            val target = requested ?: return@LaunchedEffect
            destination.value = null
            if (!settings.welcomeCompleted) return@LaunchedEffect
            navController.navigate(
                when (target) {
                    Destination.PREFERENCES -> Preferences
                    Destination.DNS_LOG -> DnsLog
                    Destination.LISTS -> Lists(RuleType.BLOCKED)
                    Destination.UPDATE -> AppUpdate
                },
            ) { popUpTo<Home>() }
        }
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { ScreenTransitions.enter },
            exitTransition = { ScreenTransitions.exit },
            popEnterTransition = { ScreenTransitions.popEnter },
            popExitTransition = { ScreenTransitions.popExit },
            predictivePopEnterTransition = { ScreenTransitions.popEnter },
            predictivePopExitTransition = { ScreenTransitions.popExit },
        ) {
            if (isTelevision) tvDestinations(navController) else phoneDestinations(navController)
            sharedDestinations(navController)
        }
    }
}

/** The screens of a phone or a tablet that the TV has its own of. */
private fun NavGraphBuilder.phoneDestinations(navController: NavController) {
    composable<Welcome> {
        WelcomeRoute(onFinished = navController::leaveWelcome) { state, viewModel, actions ->
            WelcomeScreen(state, viewModel, actions.chooseVpn, actions.finish)
        }
    }
    composable<Home> {
        val navigation = remember { homeNavigation(navController) }
        HomeRoute(navigation) { state, viewModel, blocking -> HomeScreen(state, viewModel, navigation, blocking) }
    }
    composable<Sources> { SourcesRoute(sourcesNavigation(navController)) }
    composable<SourceEdit> { entry ->
        SourceEditRoute(entry.toRoute<SourceEdit>().sourceId, onDone = navController::popBackStack) { title, form, viewModel, save ->
            SourceEditScreen(title, form, viewModel, save, onBack = navController::popBackStack)
        }
    }
    composable<Lists> { entry -> ListsRoute(entry.toRoute<Lists>().type, onBack = navController::popBackStack) }
    composable<DnsLog> { DnsLogScreen(onBack = navController::popBackStack) }
}

/** The screens the TV shows as the phone does, as the 6.x versions did. */
private fun NavGraphBuilder.sharedDestinations(navController: NavController) {
    composable<Preferences> {
        PreferencesScreen(
            PreferencesNavigation(
                back = navController::popBackStack,
                openUpdates = { navController.navigate(Updates) },
                openExcludedApps = { navController.navigate(ExcludedApps) },
                openHostsFile = { navController.navigate(SystemHosts) },
                openBackupRestore = { navController.navigate(BackupRestore) },
            ),
        )
    }
    composable<Help> { HelpScreen(onBack = navController::popBackStack, openNotices = { navController.navigate(ThirdPartyNotices) }) }
    composable<ThirdPartyNotices> { NoticesScreen(onBack = navController::popBackStack) }
    composable<Updates> { UpdatesScreen(onBack = navController::popBackStack, openUpdate = { navController.navigate(AppUpdate) }) }
    composable<AppUpdate> { UpdateScreen(onBack = navController::popBackStack) }
    composable<ExcludedApps> { ExcludedAppsScreen(onBack = navController::popBackStack) }
    composable<SystemHosts> { HostsFileScreen(onBack = navController::popBackStack) }
    composable<BackupRestore> { BackupRestoreScreen(onBack = navController::popBackStack) }
}

/** Where the home screens lead, on the phone and the TV. */
internal fun homeNavigation(navController: NavController) = HomeNavigation(
    openSources = { navController.navigate(Sources) },
    openLists = { type -> navController.navigate(Lists(type)) },
    openPreferences = { navController.navigate(Preferences) },
    openHelp = { navController.navigate(Help) },
    openLog = { navController.navigate(DnsLog) },
    openUpdate = { navController.navigate(AppUpdate) },
)

internal fun sourcesNavigation(navController: NavController) = SourcesNavigation(
    back = navController::popBackStack,
    add = { navController.navigate(SourceEdit()) },
    edit = { id -> navController.navigate(SourceEdit(id)) },
)

/** The wizard done, the home takes its place. */
internal fun NavController.leaveWelcome() = navigate(Home) {
    popUpTo<Welcome> { inclusive = true }
    // A second tap on Finish finds the home already there.
    launchSingleTop = true
}
