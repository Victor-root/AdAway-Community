package org.adaway.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.adaway.AdAwayApplication
import org.adaway.AppContainer

/** Returns the screen view model, created from the app services the first time. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as AdAwayApplication).container
    return viewModel { create(container) }
}
