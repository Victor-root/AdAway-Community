package org.adaway.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import org.adaway.ui.common.appViewModel
import org.adaway.ui.home.HomeRoute
import org.adaway.ui.theme.AdAwayTheme
import org.adaway.ui.welcome.WelcomeRoute

@Serializable
private data object Welcome

@Serializable
private data object Home

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
            composable<Home> { HomeRoute(isTelevision = isTelevision) }
        }
    }
}
