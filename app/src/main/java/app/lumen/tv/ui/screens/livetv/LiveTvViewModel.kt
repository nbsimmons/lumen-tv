package app.lumen.tv.ui.screens.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.livetv.Channel
import app.lumen.tv.data.livetv.LiveTvRepository
import app.lumen.tv.data.livetv.Playlist
import app.lumen.tv.data.livetv.Programme
import app.lumen.tv.data.settings.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

enum class LiveTvMode { Channels, Guide }

data class LiveTvSources(val playlist: String, val guide: String)

sealed interface LiveTvState {
    data object Loading : LiveTvState
    data class Setup(val current: LiveTvSources, val canCancel: Boolean) : LiveTvState
    data object Error : LiveTvState
    data class Content(
        val groups: List<String>,
        val group: String,
        val channels: List<Channel>,
        val guide: Map<String, List<Programme>>,
        val guideLoading: Boolean,
    ) : LiveTvState
}

class LiveTvViewModel(
    private val repository: LiveTvRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow<LiveTvState>(LiveTvState.Loading)
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    private val _now = MutableStateFlow(Instant.now())
    val now: StateFlow<Instant> = _now.asStateFlow()

    private val _mode = MutableStateFlow(LiveTvMode.Channels)
    val mode: StateFlow<LiveTvMode> = _mode.asStateFlow()

    private val _focused = MutableStateFlow<Channel?>(null)
    val focused: StateFlow<Channel?> = _focused.asStateFlow()

    private var playlist: Playlist? = null
    private var beforeSetup: LiveTvState? = null
    private var guideJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            while (isActive) {
                delay(CLOCK_TICK_MS)
                _now.value = Instant.now()
            }
        }
    }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = LiveTvState.Loading
            if (repository.playlistUrl() == null) {
                _state.value = LiveTvState.Setup(currentSources(), canCancel = false)
                return@launch
            }
            val loaded = runCatching { repository.playlist(forceRefresh) }.getOrNull()
            if (loaded == null || loaded.channels.isEmpty()) {
                _state.value = LiveTvState.Error
                return@launch
            }
            playlist = loaded
            val groups = loaded.channels.map { it.group }.distinct()
            val uk = groups.firstOrNull { it.equals(DEFAULT_GROUP, ignoreCase = true) }
            selectGroup(uk ?: groups.first())
        }
    }

    fun selectGroup(group: String) {
        val current = playlist ?: return
        val groups = current.channels.map { it.group }.distinct()
        val channels = current.channels.filter { it.group == group }.sortedBy { it.number }
        _state.value = LiveTvState.Content(groups, group, channels, emptyMap(), guideLoading = true)
        _focused.value = channels.firstOrNull()
        guideJob?.cancel()
        guideJob = viewModelScope.launch {
            val guide = runCatching { repository.guideFor(channels) }.getOrNull()
            val withLogos = channels.map { channel ->
                guide?.logos?.get(channel.key)?.takeIf { channel.logo == null }?.let { channel.copy(logo = it) } ?: channel
            }
            _state.update { state ->
                if (state is LiveTvState.Content && state.group == group) {
                    state.copy(channels = withLogos, guide = guide?.programmes.orEmpty(), guideLoading = false)
                } else {
                    state
                }
            }
            _focused.update { focused -> withLogos.firstOrNull { it.key == focused?.key } ?: focused }
        }
    }

    fun saveSources(playlistUrl: String, guideUrl: String) {
        viewModelScope.launch {
            settings.setLiveTvSources(playlistUrl, guideUrl)
            beforeSetup = null
            load(forceRefresh = true)
        }
    }

    fun editSources() {
        viewModelScope.launch {
            beforeSetup = _state.value.takeIf { it is LiveTvState.Content || it is LiveTvState.Error }
            _state.value = LiveTvState.Setup(currentSources(), canCancel = beforeSetup != null)
        }
    }

    fun cancelEdit() {
        beforeSetup?.let { _state.value = it }
        beforeSetup = null
    }

    fun toggleMode() = _mode.update { if (it == LiveTvMode.Channels) LiveTvMode.Guide else LiveTvMode.Channels }

    fun onFocus(channel: Channel) {
        _focused.value = channel
    }

    private suspend fun currentSources() = LiveTvSources(
        playlist = settings.liveTvPlaylist.first().orEmpty(),
        guide = settings.liveTvGuide.first().orEmpty(),
    )

    private companion object {
        const val CLOCK_TICK_MS = 30_000L
        const val DEFAULT_GROUP = "UK"
    }
}

fun List<Programme>.onNow(now: Instant): Programme? = firstOrNull { it.isOn(now) }

fun List<Programme>.upNext(now: Instant): Programme? = firstOrNull { it.start.isAfter(now) }
