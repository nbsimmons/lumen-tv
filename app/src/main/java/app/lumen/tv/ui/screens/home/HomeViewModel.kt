package app.lumen.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.StartupWork
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.HOME_ARRIVALS
import app.lumen.tv.data.model.HOME_COLLECTIONS
import app.lumen.tv.data.collections.CollectionIndex
import android.content.Context
import app.lumen.tv.R
import java.util.UUID
import app.lumen.tv.data.model.HOME_WATCHLIST
import app.lumen.tv.data.model.Availability
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.data.model.RequestScope
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.asMediaItem
import app.lumen.tv.data.seerr.SeerrRepository
import app.lumen.tv.data.ratings.MediaRatings
import app.lumen.tv.data.model.Shelf
import app.lumen.tv.data.model.ShelfKind
import app.lumen.tv.data.settings.SettingsStore
import app.lumen.tv.data.settings.inHomeOrder
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
    private val seerr: SeerrRepository,
    private val collections: CollectionIndex,
    private val context: Context,
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
        viewModelScope.launch { repository.changes.collect { refresh() } }
        viewModelScope.launch {
            settings.homeOrder.collect { saved ->
                order = saved
                (_state.value as? HomeState.Content)?.let { publish(it.shelves) }
            }
        }
        viewModelScope.launch { seerr.changes.collect { loadWatchlist(settings.hiddenSections.first()) } }
        viewModelScope.launch { settings.hiddenSections.drop(1).distinctUntilChanged().collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            val hidden = settings.hiddenSections.first()
            if (_state.value !is HomeState.Content) {
                val cached = runCatching { repository.cachedHome() }.getOrNull()?.filter { it.key !in hidden }
                if (cached.isNullOrEmpty()) _state.value = HomeState.Loading else publish(cached)
            }
            launch { loadWatchlist(hidden) }
            launch { loadArrivals(hidden) }
            runCatching { repository.homeShelves(hidden) }
                .onSuccess {
                    publish(it)
                    startup.afterHomeLoaded()
                    loadCollections(hidden)
                }
                .onFailure {
                    if (_state.value !is HomeState.Content) _state.value = HomeState.Error
                    startup.afterHomeLoaded()
                }
        }
    }

    private var watchlist: Shelf? = null
    private var order: List<String> = emptyList()
    private var arrivals: Shelf? = null
    private var inProgress: Shelf? = null

    private fun publish(shelves: List<Shelf>) {
        val base = shelves.filterNot { it.kind == ShelfKind.Watchlist || it.kind == ShelfKind.Arrivals || it.kind == ShelfKind.Collections }
        val insertAt = base.indexOfLast { it.kind == ShelfKind.ContinueWatching || it.kind == ShelfKind.NextUp } + 1
        val withCollections = inProgress?.let { shelf -> base.toMutableList().apply { add((insertAt).coerceAtMost(size), shelf) } } ?: base
        val merged = (watchlist?.let { withCollections.toMutableList().apply { add(insertAt, it) } } ?: withCollections)
            .let { list -> arrivals?.let { listOf(it) + list } ?: list }
            .inHomeOrder(order) { it.key }
        _state.value = HomeState.Content(merged)
        val current = _focused.value
        _focused.value = current?.let { focused -> shelves.firstNotNullOfOrNull { shelf -> shelf.items.firstOrNull { it.id == focused.id } } }
            ?: current
            ?: shelves.firstOrNull()?.items?.firstOrNull()
    }

    private suspend fun loadWatchlist(hidden: Set<String>) {
        if (HOME_WATCHLIST in hidden) {
            watchlist = null
            return
        }
        val favourites = runCatching { repository.favourites() }.getOrDefault(emptyList())
        seerr.restore()
        val wanted = if (seerr.connected.value == true) runCatching { seerr.watchlist() }.getOrDefault(emptyList()) else emptyList()
        val ownedIds = favourites.map { it.id }.toSet()
        val ownedTmdb = favourites.mapNotNull { it.tmdbId }.toSet()
        val extra = wanted.filterNot { it.tmdbId in ownedTmdb || it.jellyfinId in ownedIds }.map { it.asMediaItem() }
        watchlist = Shelf(HOME_WATCHLIST, ShelfKind.Watchlist, null, favourites + extra).takeIf { it.items.isNotEmpty() }
        (_state.value as? HomeState.Content)?.let { publish(it.shelves) }
    }

    private suspend fun loadCollections(hidden: Set<String>) {
        if (HOME_COLLECTIONS in hidden) {
            inProgress = null
            return
        }
        seerr.restore()
        if (seerr.connected.value != true) return
        val partway = runCatching { collections.libraryCollections() }.getOrNull()
            ?.filter { it.watched in 1 until it.owned }
            ?: return
        inProgress = Shelf(
            HOME_COLLECTIONS,
            ShelfKind.Collections,
            null,
            partway.map { collection ->
                MediaItem(
                    id = UUID.nameUUIDFromBytes("collection-${collection.ref.tmdbId}".toByteArray()),
                    kind = MediaKind.Other,
                    title = collection.ref.name,
                    name = collection.ref.name,
                    subtitle = context.getString(R.string.shelf_collection_progress, collection.watched, collection.owned),
                    overview = null,
                    year = null,
                    officialRating = null,
                    communityRating = null,
                    runtimeMinutes = null,
                    genres = emptyList(),
                    progress = collection.watched.toFloat() / collection.owned,
                    resumeTicks = 0L,
                    played = false,
                    favorite = false,
                    posterUrl = collection.ref.posterUrl,
                    thumbUrl = collection.ref.backdropUrl,
                    backdropUrl = collection.ref.backdropUrl,
                    logoUrl = null,
                    seriesId = null,
                    seasonId = null,
                    indexNumber = null,
                    parentIndexNumber = null,
                    unplayedCount = null,
                    collectionId = collection.ref.tmdbId,
                )
            },
        ).takeIf { partway.isNotEmpty() }
        (_state.value as? HomeState.Content)?.let { publish(it.shelves) }
    }

    private suspend fun loadArrivals(hidden: Set<String>) {
        if (HOME_ARRIVALS in hidden) {
            arrivals = null
            return
        }
        seerr.restore()
        if (seerr.connected.value != true) return
        val entries = runCatching { seerr.requests(RequestScope.Mine) }.getOrNull() ?: return
        val arrived = entries.map { it.item }
            .filter { it.jellyfinId != null && (it.availability == Availability.Available || it.availability == Availability.PartiallyAvailable) }
            .distinctBy { it.kind to it.tmdbId }
        val now = System.currentTimeMillis()
        val known = settings.arrivals()
        val updated = (known ?: emptyMap()).toMutableMap()
        arrived.forEach { item -> updated.putIfAbsent(arrivalKey(item.kind, item.tmdbId), if (known == null) SEEN else now) }
        updated.keys.retainAll(arrived.map { arrivalKey(it.kind, it.tmdbId) }.toSet())
        if (updated != known) settings.saveArrivals(updated)
        val fresh = arrived.filter { item -> (updated[arrivalKey(item.kind, item.tmdbId)] ?: SEEN) > now - ARRIVAL_WINDOW_MS }
        arrivals = Shelf(HOME_ARRIVALS, ShelfKind.Arrivals, null, fresh.map { it.asMediaItem() }).takeIf { fresh.isNotEmpty() }
        (_state.value as? HomeState.Content)?.let { publish(it.shelves) }
    }

    fun onOpened(item: MediaItem, shelf: Shelf) {
        if (shelf.kind != ShelfKind.Arrivals) return
        val tmdbId = item.tmdbId ?: return
        val kind = if (item.kind == MediaKind.Series) DiscoverKind.Series else DiscoverKind.Movie
        viewModelScope.launch {
            val current = settings.arrivals()?.toMutableMap() ?: return@launch
            current[arrivalKey(kind, tmdbId)] = SEEN
            settings.saveArrivals(current)
            arrivals = arrivals?.copy(items = arrivals!!.items.filterNot { it.id == item.id })?.takeIf { it.items.isNotEmpty() }
            (_state.value as? HomeState.Content)?.let { publish(it.shelves) }
        }
    }

    private fun arrivalKey(kind: DiscoverKind, tmdbId: Int) = "$kind-$tmdbId"

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
        const val SEEN = 0L
        const val ARRIVAL_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        const val RATINGS_WAIT_MS = 250L
    }
}
