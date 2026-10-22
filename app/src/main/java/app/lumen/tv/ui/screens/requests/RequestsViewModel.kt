package app.lumen.tv.ui.screens.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.model.RequestEntry
import app.lumen.tv.data.model.RequestScope
import app.lumen.tv.data.seerr.SeerrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RequestsState(
    val scope: RequestScope = RequestScope.Mine,
    val entries: List<RequestEntry> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val canManage: Boolean = false,
    val busy: Set<Int> = emptySet(),
    val failed: Boolean = false,
)

class RequestsViewModel(private val seerr: SeerrRepository) : ViewModel() {
    private val _state = MutableStateFlow(RequestsState())
    val state: StateFlow<RequestsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            seerr.restore()
            _state.update { it.copy(canManage = seerr.canManageRequests()) }
            load()
        }
    }

    fun setScope(scope: RequestScope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope, entries = emptyList(), loading = true, error = false) }
        viewModelScope.launch { load() }
    }

    fun cancel(entry: RequestEntry) = act(entry) { seerr.cancelRequest(entry.id) }

    fun review(entry: RequestEntry, approve: Boolean) = act(entry) { seerr.reviewRequest(entry.id, approve) }

    private fun act(entry: RequestEntry, block: suspend () -> Unit) {
        if (entry.id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + entry.id, failed = false) }
        viewModelScope.launch {
            val ok = runCatching { block() }.isSuccess
            _state.update { it.copy(busy = it.busy - entry.id, failed = !ok) }
            if (ok) load()
        }
    }

    private suspend fun load() {
        val scope = _state.value.scope
        runCatching { seerr.requests(scope) }
            .onSuccess { entries -> _state.update { if (it.scope == scope) it.copy(entries = entries, loading = false, error = false) else it } }
            .onFailure { _state.update { if (it.scope == scope) it.copy(loading = false, error = it.entries.isEmpty()) else it } }
    }
}
