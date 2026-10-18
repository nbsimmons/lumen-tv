package app.lumen.tv.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.discovery.DiscoveredServer
import app.lumen.tv.data.discovery.LocalDiscovery
import app.lumen.tv.data.jellyfin.JellyfinClient
import app.lumen.tv.data.jellyfin.ServerInfo
import app.lumen.tv.data.session.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class OnboardingStep { Server, Method, Password, QuickConnect }

enum class OnboardingError { ServerUnreachable, InvalidCredentials, QuickConnectFailed }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Server,
    val serverUrl: String = "",
    val server: ServerInfo? = null,
    val quickConnectAvailable: Boolean = false,
    val username: String = "",
    val password: String = "",
    val quickConnectCode: String? = null,
    val busy: Boolean = false,
    val error: OnboardingError? = null,
    val discovered: List<DiscoveredServer> = emptyList(),
    val discovering: Boolean = false,
)

class OnboardingViewModel(
    private val jellyfin: JellyfinClient,
    private val sessions: SessionStore,
    private val discovery: LocalDiscovery,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var quickConnectJob: Job? = null

    init {
        viewModelScope.launch {
            sessions.lastServerUrl()?.let { url -> _state.update { it.copy(serverUrl = url) } }
        }
        discover()
    }

    fun discover() {
        if (_state.value.discovering) return
        _state.update { it.copy(discovering = true) }
        viewModelScope.launch {
            discovery.jellyfinServers().collect { server ->
                _state.update { it.copy(discovered = it.discovered + server) }
            }
            _state.update { it.copy(discovering = false) }
        }
    }

    fun pick(server: DiscoveredServer) {
        _state.update { it.copy(serverUrl = server.url, error = null) }
        submitServer()
    }

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submitServer() {
        val url = _state.value.serverUrl
        if (url.isBlank() || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { jellyfin.connect(url) }
                .onSuccess { server ->
                    val quickConnect = jellyfin.isQuickConnectEnabled()
                    _state.update {
                        it.copy(
                            busy = false,
                            server = server,
                            quickConnectAvailable = quickConnect,
                            step = if (quickConnect) OnboardingStep.Method else OnboardingStep.Password,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(busy = false, error = OnboardingError.ServerUnreachable) } }
        }
    }

    fun choosePassword() {
        quickConnectJob?.cancel()
        _state.update { it.copy(step = OnboardingStep.Password, error = null) }
    }

    fun chooseQuickConnect() {
        _state.update { it.copy(step = OnboardingStep.QuickConnect, quickConnectCode = null, error = null) }
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            runCatching {
                val code = jellyfin.startQuickConnect()
                _state.update { it.copy(quickConnectCode = code.code) }
                while (isActive && !jellyfin.isQuickConnectApproved(code.secret)) delay(POLL_MS)
                jellyfin.finishQuickConnect(code.secret)
            }.onFailure { _state.update { it.copy(error = OnboardingError.QuickConnectFailed) } }
        }
    }

    fun submitPassword() {
        val current = _state.value
        if (current.username.isBlank() || current.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { jellyfin.login(current.username.trim(), current.password) }
                .onFailure { _state.update { it.copy(busy = false, error = OnboardingError.InvalidCredentials) } }
        }
    }

    fun back(): Boolean {
        quickConnectJob?.cancel()
        val step = _state.value.step
        val previous = when (step) {
            OnboardingStep.Server -> return false
            OnboardingStep.Method -> OnboardingStep.Server
            OnboardingStep.Password, OnboardingStep.QuickConnect ->
                if (_state.value.quickConnectAvailable) OnboardingStep.Method else OnboardingStep.Server
        }
        _state.update { it.copy(step = previous, error = null, busy = false) }
        return true
    }

    private companion object {
        const val POLL_MS = 3_000L
    }
}
