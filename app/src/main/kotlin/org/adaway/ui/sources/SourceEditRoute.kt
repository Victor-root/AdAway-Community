package org.adaway.ui.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.builtInSourceLabel

@Composable
fun SourceEditRoute(isTelevision: Boolean, sourceId: Long, onDone: () -> Unit) {
    val viewModel = appViewModel { SourceEditViewModel(it, sourceId) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onDone() }
    val current = form ?: return
    val translatedLabel = viewModel.builtIn?.let { builtInSourceLabel(it) }
    LaunchedEffect(Unit) {
        if (translatedLabel != null && current.label.isEmpty()) viewModel.edit { it.copy(label = translatedLabel) }
    }
    val title = stringResource(if (viewModel.isNew) R.string.source_add else R.string.source_edit)
    val save = { viewModel.save(translatedLabel) }
    if (isTelevision) {
        TvSourceEditScreen(title, current, viewModel, save)
    } else {
        SourceEditScreen(title, current, viewModel, save, onBack = onDone)
    }
}
