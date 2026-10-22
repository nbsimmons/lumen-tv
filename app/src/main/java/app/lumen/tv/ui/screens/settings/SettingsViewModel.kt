package app.lumen.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.jellyfin.JellyfinClient
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.HomeSection
import app.lumen.tv.data.seerr.SeerrRepository
import app.lumen.tv.data.settings.LumenSettings
import app.lumen.tv.data.settings.SettingsStore
import app.lumen.tv.data.update.UpdateRepository
import app.lumen.tv.data.logos.TitleLogos
import app.lumen.tv.data.logos.TmdbKeyCheck
import app.lumen.tv.data.session.SessionStore
import app.lumen.tv.data.update.UpdateState
import app.lumen.tv.data.settings.StreamingQuality
import app.lumen.tv.data.settings.SearchKeyboard
import app.lumen.tv.data.settings.SubtitleMode
import app.lumen.tv.playback.DeviceCapabilities
import app.lumen.tv.playback.DeviceProfileFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverName: String = "",
    val serverUrl: String = "",
    val userName: String = "",
    val seerrConnected: Boolean = false,
    val seerrUrl: String? = null,
    val settings: LumenSettings = LumenSettings(),
    val hiddenSections: Set<String> = emptySet(),
)

class SettingsViewModel(
    private val jellyfin: JellyfinClient,
    private val seerr: SeerrRepository,
    private val store: SettingsStore,
    profiles: DeviceProfileFactory,
    private val library: JellyfinRepository,
    private val updates: UpdateRepository,
    private val sessions: SessionStore,
    private val logos: TitleLogos,
) : ViewModel() {
    val tmdbKey: StateFlow<String?> = sessions.tmdbKey.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _tmdbCheck = MutableStateFlow<TmdbKeyCheck?>(null)
    val tmdbCheck: StateFlow<TmdbKeyCheck?> = _tmdbCheck.asStateFlow()

    private val _tmdbSaving = MutableStateFlow(false)
    val tmdbSaving: StateFlow<Boolean> = _tmdbSaving.asStateFlow()

    fun saveTmdbKey(key: String, onSaved: () -> Unit) = viewModelScope.launch {
        if (key.isBlank() || _tmdbSaving.value) return@launch
        _tmdbSaving.value = true
        val result = logos.check(key)
        _tmdbCheck.value = result
        if (result == TmdbKeyCheck.Valid) {
            sessions.saveTmdbKey(key)
            logos.forget()
            onSaved()
        }
        _tmdbSaving.value = false
    }

    fun removeTmdbKey() = viewModelScope.launch {
        sessions.saveTmdbKey(null)
        logos.forget()
        _tmdbCheck.value = null
    }

    fun clearTmdbCheck() {
        _tmdbCheck.value = null
    }

    val updatesEnabled: Boolean = updates.enabled
    val updateState: StateFlow<UpdateState> = updates.state

    fun checkForUpdates() = updates.checkNow()

    private val _sections = MutableStateFlow<List<HomeSection>>(emptyList())
    val sections: StateFlow<List<HomeSection>> = _sections.asStateFlow()

    val capabilities: DeviceCapabilities = profiles.capabilities

    val state: StateFlow<SettingsUiState> = combine(
        jellyfin.session,
        seerr.connected,
        store.settings,
        store.hiddenSections,
    ) { session, connected, settings, hidden ->
        SettingsUiState(
            serverName = session?.serverName.orEmpty(),
            serverUrl = session?.serverUrl.orEmpty(),
            userName = session?.userName.orEmpty(),
            seerrConnected = connected == true,
            seerrUrl = seerr.url,
            settings = settings,
            hiddenSections = hidden,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { seerr.restore() }
        viewModelScope.launch { _sections.value = runCatching { library.homeSections() }.getOrDefault(emptyList()) }
    }

    fun cycleQuality() = viewModelScope.launch {
        val all = StreamingQuality.entries
        store.setQuality(all[(all.indexOf(state.value.settings.quality) + 1) % all.size])
    }

    fun toggleSearchKeyboard() = viewModelScope.launch {
        val next = if (state.value.settings.searchKeyboard == SearchKeyboard.System) SearchKeyboard.OneLine else SearchKeyboard.System
        store.setSearchKeyboard(next)
    }

    fun cycleSubtitles() = viewModelScope.launch {
        val all = SubtitleMode.entries
        store.setSubtitles(all[(all.indexOf(state.value.settings.subtitles) + 1) % all.size])
    }

    fun toggleSection(key: String) = viewModelScope.launch {
        store.setSectionHidden(key, key !in state.value.hiddenSections)
    }

    fun disconnectSeerr() = viewModelScope.launch { seerr.disconnect() }

    fun signOut() = viewModelScope.launch {
        seerr.disconnect()
        jellyfin.signOut()
    }
}
