package app.lumen.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.discovery.LocalDiscovery
import app.lumen.tv.data.jellyfin.JellyfinClient
import app.lumen.tv.data.seerr.SeerrRepository
import app.lumen.tv.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

enum class RootState { Loading, SignedOut, SignedIn }

class RootViewModel(
    private val jellyfin: JellyfinClient,
    private val seerr: SeerrRepository,
    private val sessions: SessionStore,
    private val discovery: LocalDiscovery,
) : ViewModel() {
    private val restored = MutableStateFlow(false)

    val state: StateFlow<RootState> = combine(restored, jellyfin.session) { done, session ->
        when {
            !done -> RootState.Loading
            session == null -> RootState.SignedOut
            else -> RootState.SignedIn
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    init {
        viewModelScope.launch {
            jellyfin.restore()
            restored.value = true
        }
        viewModelScope.launch {
            jellyfin.session.filterNotNull().distinctUntilChangedBy { it.userId }.collect { session -> connectSeerr(session.serverUrl) }
        }
    }

    private suspend fun connectSeerr(jellyfinUrl: String) {
        seerr.restore()
        if (seerr.connected.value == true) return
        val host = runCatching { URI(jellyfinUrl).host }.getOrNull()
        val known = listOfNotNull(sessions.lastSeerrUrl())
        if (known.isNotEmpty() && seerr.autoConnect(known)) return
        val found = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
            discovery.seerrServers(listOfNotNull(host)).firstOrNull()
        } ?: return
        seerr.autoConnect(listOf(found.url))
    }

    private companion object {
        const val DISCOVERY_TIMEOUT_MS = 8_000L
    }
}
