package org.adaway.ui.tv

import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.adaway.ui.DnsLog
import org.adaway.ui.Home
import org.adaway.ui.Lists
import org.adaway.ui.SourceEdit
import org.adaway.ui.Sources
import org.adaway.ui.leaveWelcome
import org.adaway.ui.sourcesNavigation
import org.adaway.ui.Welcome
import org.adaway.ui.home.HomeRoute
import org.adaway.ui.homeNavigation
import org.adaway.ui.sources.SourceEditRoute
import org.adaway.ui.welcome.WelcomeRoute

/** The screens the TV has its own of, made for the remote. */
internal fun NavGraphBuilder.tvDestinations(navController: NavController) {
    composable<Welcome> {
        WelcomeRoute(onFinished = navController::leaveWelcome) { state, viewModel, actions ->
            TvWelcomeScreen(state, viewModel, actions)
        }
    }
    composable<Home> {
        val navigation = remember { homeNavigation(navController) }
        HomeRoute(navigation) { state, viewModel, blocking -> TvHomeScreen(state, viewModel, navigation, blocking) }
    }
    composable<Sources> { TvSourcesScreen(sourcesNavigation(navController)) }
    composable<SourceEdit> { entry ->
        SourceEditRoute(entry.toRoute<SourceEdit>().sourceId, onDone = navController::popBackStack) { title, form, viewModel, save ->
            TvSourceEditScreen(title, form, viewModel, save)
        }
    }
    composable<Lists> { entry -> TvListsScreen(entry.toRoute<Lists>().type) }
    composable<DnsLog> { TvDnsLogScreen() }
}
