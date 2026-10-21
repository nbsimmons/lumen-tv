package app.lumen.tv.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.collections.CollectionIndex
import app.lumen.tv.data.collections.LibraryCollection
import app.lumen.tv.data.seerr.SeerrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface CollectionsState {
    data object Loading : CollectionsState
    data object NotConnected : CollectionsState
    data object Error : CollectionsState
    data class Content(val collections: List<LibraryCollection>) : CollectionsState
}

class CollectionsViewModel(
    private val index: CollectionIndex,
    private val seerr: SeerrRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<CollectionsState>(CollectionsState.Loading)
    val state: StateFlow<CollectionsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            seerr.restore()
            val connected = seerr.connected.filter { it != null }.first()
            if (connected != true) {
                _state.value = CollectionsState.NotConnected
                return@launch
            }
            if (_state.value !is CollectionsState.Content) _state.value = CollectionsState.Loading
            runCatching { index.libraryCollections() }
                .onSuccess { _state.value = CollectionsState.Content(it) }
                .onFailure { if (_state.value !is CollectionsState.Content) _state.value = CollectionsState.Error }
        }
    }
}
