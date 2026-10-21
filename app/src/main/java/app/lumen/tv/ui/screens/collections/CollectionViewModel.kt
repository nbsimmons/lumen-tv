package app.lumen.tv.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.MovieCollection
import app.lumen.tv.data.model.RequestChoice
import app.lumen.tv.data.model.RequestOptions
import app.lumen.tv.data.model.RequestServer
import app.lumen.tv.data.seerr.SeerrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CollectionOutcome {
    data class Sent(val count: Int) : CollectionOutcome
    data object Failed : CollectionOutcome
}

data class CollectionState(
    val collection: MovieCollection? = null,
    val error: Boolean = false,
    val sheetOpen: Boolean = false,
    val loadingOptions: Boolean = false,
    val options: RequestOptions? = null,
    val want4k: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val selected: Set<Int> = emptySet(),
    val requesting: Boolean = false,
    val outcome: CollectionOutcome? = null,
) {
    val server: RequestServer? get() = options?.servers?.firstOrNull { it.id == serverId }
    val serversForVersion: List<RequestServer> get() = options?.servers.orEmpty().filter { it.is4k == want4k }
    val has4kChoice: Boolean get() = options?.servers.orEmpty().let { all -> all.any { it.is4k } && all.any { !it.is4k } }
}

class CollectionViewModel(
    private val tmdbId: Int,
    private val openRequest: Boolean,
    private val seerr: SeerrRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CollectionState())
    val state: StateFlow<CollectionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            load()
            if (openRequest && _state.value.collection?.missing?.isNotEmpty() == true) openSheet()
        }
        viewModelScope.launch { seerr.changes.collect { load() } }
    }

    private suspend fun load() {
        seerr.restore()
        runCatching { seerr.collection(tmdbId) }
            .onSuccess { collection -> _state.update { it.copy(collection = collection, error = false) } }
            .onFailure { _state.update { it.copy(error = it.collection == null) } }
    }

    fun openSheet() {
        val missing = _state.value.collection?.missing.orEmpty()
        if (missing.isEmpty() || _state.value.loadingOptions) return
        _state.update { it.copy(loadingOptions = true, outcome = null) }
        viewModelScope.launch {
            val options = _state.value.options
                ?: runCatching { seerr.requestOptions(DiscoverKind.Movie) }
                    .getOrElse { RequestOptions(canCustomise = false, servers = emptyList()) }
            _state.update { it.copy(loadingOptions = false, options = options, sheetOpen = true, selected = missing.map { m -> m.tmdbId }.toSet()) }
            selectVersion(false)
        }
    }

    fun dismissSheet() = _state.update { it.copy(sheetOpen = false) }

    fun toggle(tmdbId: Int) = _state.update {
        it.copy(selected = if (tmdbId in it.selected) it.selected - tmdbId else it.selected + tmdbId)
    }

    fun toggleAll() = _state.update { state ->
        val all = state.collection?.missing.orEmpty().map { it.tmdbId }.toSet()
        state.copy(selected = if (state.selected.containsAll(all)) emptySet() else all)
    }

    fun selectVersion(is4k: Boolean) {
        _state.update { it.copy(want4k = is4k) }
        val server = _state.value.serversForVersion.let { group -> group.firstOrNull { it.isDefault } ?: group.firstOrNull() }
        selectServer(server?.id)
    }

    fun selectServer(id: Int?) = _state.update { state ->
        val server = state.options?.servers?.firstOrNull { it.id == id }
        val profile = server?.profiles?.firstOrNull { it.id == server.defaultProfileId } ?: server?.profiles?.firstOrNull()
        state.copy(serverId = server?.id, profileId = profile?.id)
    }

    fun selectProfile(id: Int) = _state.update { it.copy(profileId = id) }

    fun confirm() {
        val state = _state.value
        if (state.requesting || state.selected.isEmpty()) return
        val order = state.collection?.missing.orEmpty().map { it.tmdbId }.filter { it in state.selected }
        val choice = if (state.options?.canCustomise == true) RequestChoice(state.server, state.profileId) else null
        _state.update { it.copy(requesting = true, outcome = null) }
        viewModelScope.launch {
            val sent = runCatching { seerr.requestMovies(order, choice) }.getOrDefault(0)
            _state.update {
                it.copy(
                    requesting = false,
                    sheetOpen = sent == 0 && it.sheetOpen,
                    outcome = if (sent > 0) CollectionOutcome.Sent(sent) else CollectionOutcome.Failed,
                )
            }
        }
    }
}
