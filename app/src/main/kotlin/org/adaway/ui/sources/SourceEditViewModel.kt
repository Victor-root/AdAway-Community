package org.adaway.ui.sources

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.data.db.BuiltInSource
import org.adaway.data.db.SourceEntity
import org.adaway.data.db.SourceFormat
import org.adaway.sources.SourceDraft
import org.adaway.sources.SourceFieldError
import org.adaway.sources.SourceSaveResult
import org.adaway.system.DocumentAccess

data class SourceForm(
    val label: String,
    val url: String,
    val format: SourceFormat,
    val redirectsEnabled: Boolean,
    val errors: Set<SourceFieldError> = emptySet(),
) {
    val isFile get() = url.startsWith(CONTENT_PREFIX)

    companion object {
        const val HTTPS_PREFIX = "https://"
        const val CONTENT_PREFIX = "content://"
    }
}

class SourceEditViewModel(private val container: AppContainer, private val sourceId: Long) : ViewModel() {
    private val repository = container.sourceRepository
    private val mutableForm = MutableStateFlow<SourceForm?>(null)
    private val mutableDone = MutableStateFlow(false)
    private var original: SourceEntity? = null

    val isNew = sourceId == NEW_SOURCE
    val form: StateFlow<SourceForm?> = mutableForm.asStateFlow()

    /** Set once the source is saved or deleted, to leave the screen. */
    val done: StateFlow<Boolean> = mutableDone.asStateFlow()

    /** The built-in source being edited, whose label shows translated until renamed. */
    var builtIn: BuiltInSource? = null
        private set

    init {
        viewModelScope.launch {
            val source = if (isNew) null else repository.get(sourceId)
            original = source
            builtIn = source?.builtIn
            mutableForm.value = SourceForm(
                label = source?.label.orEmpty(),
                url = source?.url ?: SourceForm.HTTPS_PREFIX,
                format = source?.format ?: SourceFormat.BLOCK,
                redirectsEnabled = source?.redirectsEnabled ?: false,
            )
        }
    }

    fun edit(transform: (SourceForm) -> SourceForm) {
        mutableForm.update { form -> form?.let(transform) }
    }

    fun useUrl() = edit { it.copy(url = SourceForm.HTTPS_PREFIX) }

    fun usePickedFile(uri: Uri) {
        DocumentAccess.keep(container.applicationContext, uri)
        edit { it.copy(url = uri.toString()) }
    }

    /** [translatedLabel] is the shown name of a built-in source: keeping it keeps the translation. */
    fun save(translatedLabel: String?) {
        val form = mutableForm.value ?: return
        val keepsBuiltInName = builtIn != null && form.label.trim() == translatedLabel
        viewModelScope.launch {
            val draft = SourceDraft(
                label = if (keepsBuiltInName) null else form.label,
                url = form.url,
                format = form.format,
                redirectsEnabled = form.redirectsEnabled,
            )
            when (val result = repository.save(original?.id, draft)) {
                SourceSaveResult.Saved -> {
                    original?.url?.takeIf { it != form.url.trim() }?.let { previous ->
                        DocumentAccess.release(container.applicationContext, previous)
                    }
                    mutableDone.value = true
                }
                is SourceSaveResult.Invalid -> edit { it.copy(errors = result.errors) }
            }
        }
    }

    /** Deletes the source. With [undoable], the list screen then offers to undo it. */
    fun delete(undoable: Boolean) {
        val source = original ?: return
        viewModelScope.launch {
            repository.delete(source)
            if (!undoable) {
                repository.forgetRecentlyDeleted()
                DocumentAccess.release(container.applicationContext, source.url)
            }
            mutableDone.value = true
        }
    }

    companion object {
        const val NEW_SOURCE = 0L
    }
}
