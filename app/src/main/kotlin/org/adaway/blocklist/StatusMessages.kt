package org.adaway.blocklist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.adaway.root.RootStep
import org.adaway.sources.UpdateProgress

/** The line under the sources card: the step in progress, then how the last operation ended. */
sealed interface StatusMessage {
    data class Update(val progress: UpdateProgress) : StatusMessage

    data class Outcome(val outcome: UpdateOutcome) : StatusMessage

    data class Root(val step: RootStep) : StatusMessage
}

class StatusMessages {
    private val mutableCurrent = MutableStateFlow<StatusMessage?>(null)

    val current: StateFlow<StatusMessage?> = mutableCurrent.asStateFlow()

    /** Null when an error is told instead. */
    fun post(message: StatusMessage?) {
        mutableCurrent.value = message
    }
}
