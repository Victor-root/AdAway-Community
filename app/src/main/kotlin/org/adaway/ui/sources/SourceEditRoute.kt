package org.adaway.ui.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.builtInSourceLabel

/** The source form logic shared by the phone and TV screens. [screen] draws the form of the device. */
@Composable
fun SourceEditRoute(sourceId: Long, onDone: () -> Unit, screen: @Composable (title: String, form: SourceForm, viewModel: SourceEditViewModel, save: () -> Unit) -> Unit) {
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
    screen(title, current, viewModel, save)
}
