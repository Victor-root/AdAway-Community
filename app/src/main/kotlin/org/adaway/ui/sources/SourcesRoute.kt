package org.adaway.ui.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.data.db.SourceEntity
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.sourceLabel
import java.text.Collator

class SourcesNavigation(val back: () -> Unit, val add: () -> Unit, val edit: (Long) -> Unit)

@Composable
fun SourcesRoute(navigation: SourcesNavigation) {
    val viewModel = appViewModel(::SourcesViewModel)
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    SourcesScreen(sortedByLabel(sources.orEmpty()), viewModel, navigation)
}

/** Each source with its shown name, in the alphabetical order of the names. */
@Composable
fun sortedByLabel(sources: List<SourceEntity>): List<Pair<SourceEntity, String>> {
    val collator = remember { Collator.getInstance() }
    return sources.map { it to sourceLabel(it) }.sortedWith { first, second -> collator.compare(first.second, second.second) }
}
