package app.lumen.tv.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.BrowseQuery
import app.lumen.tv.data.model.BrowseSort
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.Option
import app.lumen.tv.data.seerr.SeerrRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseState(
    val query: BrowseQuery,
    val items: List<DiscoverItem> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val hasMore: Boolean = false,
    val genres: List<Option> = emptyList(),
    val providers: List<Option> = emptyList(),
    val library: List<MediaItem> = emptyList(),
)

class BrowseViewModel(
    initial: BrowseQuery,
    private val seerr: SeerrRepository,
    private val jellyfin: JellyfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseState(initial))
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private var page = 0
    private var pageJob: Job? = null

    init {
        viewModelScope.launch {
            seerr.restore()
            reload()
            loadOptions(initial.kind)
        }
        initial.studio?.let { studio ->
            viewModelScope.launch {
                runCatching { jellyfin.byStudio(studio.name) }.onSuccess { library -> _state.update { it.copy(library = library) } }
            }
        }
    }

    fun setKind(kind: DiscoverKind) {
        if (kind == _state.value.query.kind || _state.value.query.studio != null) return
        update(_state.value.query.copy(kind = kind, genre = null, provider = null))
        viewModelScope.launch { loadOptions(kind) }
    }

    fun setGenre(genre: Option?) = update(_state.value.query.copy(genre = genre))

    fun setProvider(provider: Option?) = update(_state.value.query.copy(provider = provider))

    fun setSort(sort: BrowseSort) = update(_state.value.query.copy(sort = sort))

    fun onItemFocused(index: Int) {
        val current = _state.value
        if (current.hasMore && index >= current.items.size - PREFETCH && pageJob?.isActive != true) loadPage()
    }

    private fun update(query: BrowseQuery) {
        if (query == _state.value.query) return
        _state.update { it.copy(query = query) }
        reload()
    }

    private fun reload() {
        pageJob?.cancel()
        page = 0
        _state.update { it.copy(items = emptyList(), loading = true, error = false, hasMore = false) }
        loadPage()
    }

    private fun loadPage() {
        val query = _state.value.query
        val next = page + 1
        pageJob = viewModelScope.launch {
            runCatching { seerr.browse(query, next) }
                .onSuccess { (items, more) ->
                    if (_state.value.query != query) return@onSuccess
                    page = next
                    _state.update { state ->
                        val known = state.items.map { it.kind to it.tmdbId }.toSet()
                        state.copy(items = state.items + items.filterNot { (it.kind to it.tmdbId) in known }, loading = false, hasMore = more)
                    }
                }
                .onFailure { _state.update { it.copy(loading = false, error = it.items.isEmpty()) } }
        }
    }

    private suspend fun loadOptions(kind: DiscoverKind) {
        val genres = runCatching { seerr.genres(kind) }.getOrDefault(emptyList())
        val providers = runCatching { seerr.providers(kind) }.getOrDefault(emptyList())
        _state.update { if (it.query.kind == kind) it.copy(genres = genres, providers = providers) else it }
    }

    private companion object {
        const val PREFETCH = 14
    }
}
