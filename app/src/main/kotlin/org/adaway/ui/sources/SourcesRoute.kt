package org.adaway.ui.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.sourceLabel
import java.text.Collator

class SourcesNavigation(val back: () -> Unit, val add: () -> Unit, val edit: (Long) -> Unit)

@Composable
fun SourcesRoute(isTelevision: Boolean, navigation: SourcesNavigation) {
    val viewModel = appViewModel(::SourcesViewModel)
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val labelled = sources.orEmpty().map { it to sourceLabel(it) }
    val collator = remember { Collator.getInstance() }
    val sorted = labelled.sortedWith { first, second -> collator.compare(first.second, second.second) }
    if (isTelevision) {
        TvSourcesScreen(sorted, viewModel, navigation)
    } else {
        SourcesScreen(sorted, viewModel, navigation)
    }
}

