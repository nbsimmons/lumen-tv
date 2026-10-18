package app.lumen.tv.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.DiscoverShelf
import app.lumen.tv.data.model.DiscoverShelfKind
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.seerr.SeerrRepository
import app.lumen.tv.ui.components.ShelfFocusMemory
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

sealed interface DiscoverState {
    data object Loading : DiscoverState
    data object NotConnected : DiscoverState
    data object Error : DiscoverState
    data class Content(val shelves: List<DiscoverShelf>) : DiscoverState
}

class DiscoverViewModel(
    private val seerr: SeerrRepository,
    private val jellyfin: JellyfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DiscoverState>(DiscoverState.Loading)
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    private val _focused = MutableStateFlow<DiscoverItem?>(null)
    val focused: StateFlow<DiscoverItem?> = _focused.asStateFlow()

    val focusMemory = ShelfFocusMemory()

    fun logoUrl(item: DiscoverItem): String? = item.jellyfinId?.let(jellyfin::logoUrl)

    val autoConnecting: StateFlow<Boolean> = seerr.autoConnecting

    init {
        viewModelScope.launch { seerr.changes.collect { refresh() } }
        viewModelScope.launch {
            seerr.restore()
            seerr.connected.filter { it != null }.collect { connected ->
                if (connected == true) refresh() else _state.value = DiscoverState.NotConnected
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value !is DiscoverState.Content) _state.value = DiscoverState.Loading
            val requests = async { seerr.requestsShelf() }
            runCatching { seerr.browseShelves() }
                .onSuccess { browse ->
                    val existing = (_state.value as? DiscoverState.Content)?.shelves
                        ?.firstOrNull { it.kind == DiscoverShelfKind.RecentlyRequested }
                        ?: seerr.cachedRequests()
                    if (existing != null) publish(withRequests(browse, existing))
                    publish(withRequests(browse, requests.await()))
                }
                .onFailure {
                    requests.cancel()
                    if (seerr.connected.value == true && _state.value !is DiscoverState.Content) _state.value = DiscoverState.Error
                }
        }
    }

    private fun withRequests(browse: List<DiscoverShelf>, requests: DiscoverShelf?): List<DiscoverShelf> {
        if (requests == null || requests.items.isEmpty()) return browse
        return listOf(requests) + browse
    }

    private fun publish(shelves: List<DiscoverShelf>) {
        _state.value = DiscoverState.Content(shelves)
        if (_focused.value == null) _focused.value = shelves.firstOrNull()?.items?.firstOrNull()
    }

    fun onFocus(item: DiscoverItem) {
        _focused.value = item
    }
}
