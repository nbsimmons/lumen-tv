package app.lumen.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.StartupWork
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.ratings.MediaRatings
import app.lumen.tv.data.model.Shelf
import app.lumen.tv.data.model.ShelfKind
import app.lumen.tv.data.settings.SettingsStore
import app.lumen.tv.playback.PlaybackEvents
import app.lumen.tv.ui.components.ShelfFocusMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface HomeState {
    data object Loading : HomeState
    data object Error : HomeState
    data class Content(val shelves: List<Shelf>) : HomeState
}

data class HeroDetails(val item: MediaItem, val ratings: ExternalRatings?)

class HomeViewModel(
    private val repository: JellyfinRepository,
    events: PlaybackEvents,
    private val settings: SettingsStore,
    private val ratings: MediaRatings,
    private val startup: StartupWork,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    val focusMemory = ShelfFocusMemory()

    private val _focused = MutableStateFlow<MediaItem?>(null)
    val focused: StateFlow<MediaItem?> = _focused.asStateFlow()

    private val _hero = MutableStateFlow<HeroDetails?>(null)
    val hero: StateFlow<HeroDetails?> = _hero.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            _focused.filterNotNull().collectLatest { item ->
                coroutineScope {
                    val lookup = async { ratings.forItem(item) }
                    val quick = withTimeoutOrNull(RATINGS_WAIT_MS) { lookup.await() }
                    _hero.value = HeroDetails(item, quick)
                    if (quick == null) lookup.await()?.let { _hero.value = HeroDetails(item, it) }
                }
            }
        }
        viewModelScope.launch { events.stopped.collect { refresh() } }
        viewModelScope.launch { settings.hiddenSections.drop(1).distinctUntilChanged().collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            val hidden = settings.hiddenSections.first()
            if (_state.value !is HomeState.Content) {
                val cached = runCatching { repository.cachedHome() }.getOrNull()?.filter { it.key !in hidden }
                if (cached.isNullOrEmpty()) _state.value = HomeState.Loading else publish(cached)
            }
            runCatching { repository.homeShelves(hidden) }
                .onSuccess {
                    publish(it)
                    startup.afterHomeLoaded()
                }
                .onFailure {
                    if (_state.value !is HomeState.Content) _state.value = HomeState.Error
                    startup.afterHomeLoaded()
                }
        }
    }

    private fun publish(shelves: List<Shelf>) {
        _state.value = HomeState.Content(shelves)
        val current = _focused.value
        _focused.value = current?.let { focused -> shelves.firstNotNullOfOrNull { shelf -> shelf.items.firstOrNull { it.id == focused.id } } }
            ?: current
            ?: shelves.firstOrNull()?.items?.firstOrNull()
    }

    fun removeFromContinueWatching(item: MediaItem) = mutate(item) { repository.clearResume(item.id) }

    fun markWatched(item: MediaItem) = mutate(item) { repository.setPlayed(item.id, true) }

    private fun mutate(item: MediaItem, action: suspend () -> Unit) {
        val before = _state.value
        if (before is HomeState.Content) {
            _state.value = HomeState.Content(
                before.shelves.map { shelf ->
                    if (shelf.kind == ShelfKind.ContinueWatching) shelf.copy(items = shelf.items.filterNot { it.id == item.id }) else shelf
                }.filter { it.items.isNotEmpty() },
            )
        }
        viewModelScope.launch {
            runCatching { action() }.onFailure { _state.value = before }
            refresh()
        }
    }

    fun resetShow(item: MediaItem) {
        val seriesId = item.seriesId ?: return
        val before = _state.value
        if (before is HomeState.Content) {
            _state.value = HomeState.Content(
                before.shelves.map { shelf ->
                    if (shelf.kind == ShelfKind.NextUp || shelf.kind == ShelfKind.ContinueWatching) {
                        shelf.copy(items = shelf.items.filterNot { it.seriesId == seriesId })
                    } else {
                        shelf
                    }
                }.filter { it.items.isNotEmpty() },
            )
        }
        viewModelScope.launch {
            runCatching { repository.setPlayed(seriesId, false) }.onFailure { _state.value = before }
            refresh()
        }
    }

    fun hideSection(key: String) {
        val current = _state.value
        if (current is HomeState.Content) _state.value = HomeState.Content(current.shelves.filterNot { it.key == key })
        viewModelScope.launch { settings.setSectionHidden(key, true) }
    }

    fun onFocus(item: MediaItem) = _focused.update { item }

    private companion object {
        const val RATINGS_WAIT_MS = 250L
    }
}
