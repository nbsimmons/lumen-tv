package app.lumen.tv.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.discovery.DiscoveredServer
import app.lumen.tv.data.discovery.LocalDiscovery
import app.lumen.tv.data.jellyfin.JellyfinClient
import app.lumen.tv.data.seerr.SeerrRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URI

enum class ConnectStep { Server, QuickConnect, Password }

enum class ConnectError { Unreachable, InvalidCredentials, QuickConnectFailed }

data class SeerrConnectState(
    val step: ConnectStep = ConnectStep.Server,
    val url: String = "",
    val verifiedUrl: String? = null,
    val username: String = "",
    val password: String = "",
    val code: String? = null,
    val busy: Boolean = false,
    val error: ConnectError? = null,
    val discovered: List<DiscoveredServer> = emptyList(),
    val discovering: Boolean = false,
)

class SeerrConnectViewModel(
    private val seerr: SeerrRepository,
    private val jellyfin: JellyfinClient,
    discovery: LocalDiscovery,
) : ViewModel() {
    private val _state = MutableStateFlow(SeerrConnectState(username = jellyfin.session.value?.userName.orEmpty()))
    val state: StateFlow<SeerrConnectState> = _state.asStateFlow()

    private var quickConnectJob: Job? = null

    init {
        viewModelScope.launch {
            val suggested = seerr.suggestedUrl()
            _state.update { if (it.url.isEmpty()) it.copy(url = suggested) else it }
        }
        viewModelScope.launch {
            _state.update { it.copy(discovering = true) }
            val hint = jellyfin.session.value?.serverUrl?.let { runCatching { URI(it).host }.getOrNull() }
            discovery.seerrServers(listOfNotNull(hint)).collect { server ->
                _state.update { it.copy(discovered = it.discovered + server) }
            }
            _state.update { it.copy(discovering = false) }
        }
    }

    fun pick(server: DiscoveredServer) = _state.update { it.copy(url = server.url, error = null) }

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun connectWithQuickConnect() = verifyThen { url -> startQuickConnect(url) }

    fun connectWithPassword() = verifyThen { _state.update { it.copy(step = ConnectStep.Password, busy = false) } }

    fun submitPassword() {
        val current = _state.value
        val url = current.verifiedUrl ?: return
        if (current.username.isBlank() || current.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { seerr.loginWithPassword(url, current.username.trim(), current.password) }
                .onFailure { _state.update { it.copy(busy = false, error = ConnectError.InvalidCredentials) } }
        }
    }

    fun back(): Boolean {
        if (_state.value.step == ConnectStep.Server) return false
        quickConnectJob?.cancel()
        _state.update { it.copy(step = ConnectStep.Server, code = null, error = null, busy = false) }
        return true
    }

    private fun verifyThen(next: (String) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { seerr.checkServer(_state.value.url) }
                .onSuccess { url ->
                    _state.update { it.copy(verifiedUrl = url, url = url) }
                    next(url)
                }
                .onFailure { _state.update { it.copy(busy = false, error = ConnectError.Unreachable) } }
        }
    }

    private fun startQuickConnect(url: String) {
        quickConnectJob?.cancel()
        _state.update { it.copy(step = ConnectStep.QuickConnect, busy = false, code = null) }
        quickConnectJob = viewModelScope.launch {
            runCatching {
                val code = seerr.startQuickConnect(url)
                _state.update { it.copy(code = code.code) }
                jellyfin.authorizeQuickConnect(code.code)
                while (isActive && !seerr.isQuickConnectApproved(url, code.secret)) delay(POLL_MS)
                seerr.finishQuickConnect(url, code.secret)
            }.onFailure { _state.update { it.copy(error = ConnectError.QuickConnectFailed) } }
        }
    }

    private companion object {
        const val POLL_MS = 3_000L
    }
}
