package org.adaway.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.adaway.AppContainer
import org.adaway.data.settings.Settings

sealed interface MainState {
    data object Loading : MainState

    data class Ready(val settings: Settings) : MainState
}

class MainViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<MainState> = flow {
        container.dataInitializer.ensureInitialized()
        emitAll(container.settingsRepository.settings.map(MainState::Ready))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainState.Loading)
}
