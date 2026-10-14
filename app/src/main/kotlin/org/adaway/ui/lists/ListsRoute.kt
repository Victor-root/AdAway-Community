package org.adaway.ui.lists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import org.adaway.data.db.RuleType
import org.adaway.ui.common.appViewModel

@Composable
fun ListsRoute(initialType: RuleType, onBack: () -> Unit) {
    val viewModel = appViewModel { ListsViewModel(it, initialType) }
    val type by viewModel.type.collectAsStateWithLifecycle()
    val userRules by viewModel.userRules.collectAsStateWithLifecycle()
    val sourceRules = viewModel.sourceRules.collectAsLazyPagingItems()
    ListsScreen(type, userRules.orEmpty(), sourceRules, viewModel, onBack)
}

/** A catalog line is `host` or `host address`. */
fun splitCatalogLine(line: String): Pair<String, String?> {
    val separator = line.indexOf(' ')
    return if (separator < 0) line to null else line.substring(0, separator) to line.substring(separator + 1)
}

