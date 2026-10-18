package app.lumen.tv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.LibrarySort
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryState(
    val sort: LibrarySort = LibrarySort.Title,
    val items: List<MediaItem> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
)

class LibraryViewModel(
    val kind: MediaKind,
    private val repository: JellyfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var pageJob: Job? = null

    init {
        reload()
    }

    fun setSort(sort: LibrarySort) {
        if (sort == _state.value.sort) return
        _state.value = LibraryState(sort = sort)
        reload()
    }

    fun reload() {
        pageJob?.cancel()
        _state.update { it.copy(items = emptyList(), total = null, loading = true, error = false) }
        loadPage()
    }

    fun onItemFocused(index: Int) {
        val current = _state.value
        val total = current.total ?: return
        if (index >= current.items.size - PREFETCH_DISTANCE && current.items.size < total && pageJob?.isActive != true) {
            loadPage()
        }
    }

    private fun loadPage() {
        val sort = _state.value.sort
        val start = _state.value.items.size
        pageJob = viewModelScope.launch {
            runCatching { repository.library(kind, sort, start, PAGE_SIZE) }
                .onSuccess { page ->
                    if (_state.value.sort != sort) return@onSuccess
                    _state.update { it.copy(items = it.items + page.items, total = page.total, loading = false) }
                }
                .onFailure { _state.update { it.copy(loading = false, error = it.items.isEmpty()) } }
        }
    }

    private companion object {
        const val PAGE_SIZE = 120
        const val PREFETCH_DISTANCE = 30
    }
}
