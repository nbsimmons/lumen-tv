package app.lumen.tv.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.Availability
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.data.model.PersonResult
import app.lumen.tv.data.model.RecentView
import app.lumen.tv.data.seerr.SeerrRepository
import app.lumen.tv.data.settings.SearchKeyboard
import app.lumen.tv.data.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchFocus(val row: String, val key: String)

sealed interface SearchHighlight {
    val backdropUrl: String?

    data class Library(val item: MediaItem) : SearchHighlight {
        override val backdropUrl: String? get() = item.backdropUrl
    }

    data class Discover(val item: DiscoverItem) : SearchHighlight {
        override val backdropUrl: String? get() = item.backdropUrl
    }

    data class Viewed(val view: RecentView) : SearchHighlight {
        override val backdropUrl: String? get() = view.backdropUrl
    }
}

sealed interface TopResult {
    data class Library(val item: MediaItem) : TopResult
    data class Discover(val item: DiscoverItem) : TopResult
    data class Person(val person: PersonResult) : TopResult
}

data class SearchState(
    val query: String = "",
    val searching: Boolean = false,
    val library: List<MediaItem> = emptyList(),
    val requestable: List<DiscoverItem> = emptyList(),
    val top: TopResult? = null,
    val topRatings: ExternalRatings? = null,
    val searched: Boolean = false,
    val resultsFor: String = "",
    val recent: List<String> = emptyList(),
    val viewed: List<RecentView> = emptyList(),
    val viewedStatus: Map<String, Availability> = emptyMap(),
    val trending: List<DiscoverItem> = emptyList(),
    val highlight: SearchHighlight? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val jellyfin: JellyfinRepository,
    private val seerr: SeerrRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val queries = MutableStateFlow("")

    var lastFocus: SearchFocus? = null

    init {
        viewModelScope.launch { settings.recentSearches.collect { recent -> _state.update { it.copy(recent = recent) } } }
        viewModelScope.launch {
            settings.recentlyViewed.collect { viewed ->
                _state.update { it.copy(viewed = viewed) }
                refreshViewedStatus(viewed, onlyMissing = true)
            }
        }
        viewModelScope.launch {
            seerr.changes.collect {
                loadTrending()
                refreshViewedStatus(_state.value.viewed, onlyMissing = false)
                search(_state.value.query.trim())
            }
        }
        viewModelScope.launch {
            seerr.restore()
            loadTrending()
            refreshViewedStatus(_state.value.viewed, onlyMissing = true)
        }
        viewModelScope.launch {
            queries
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { query -> search(query.trim()) }
        }
    }

    val keyboard: StateFlow<SearchKeyboard> = settings.settings.map { it.searchKeyboard }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchKeyboard.System)

    fun type(text: String) = setQuery((_state.value.query + text).take(MAX_QUERY))

    fun replaceQuery(value: String) = setQuery(value.take(MAX_QUERY))

    fun backspace() = setQuery(_state.value.query.dropLast(1))

    fun clear() = setQuery("")

    fun useRecent(query: String) = setQuery(query)

    fun clearRecent() {
        viewModelScope.launch { settings.clearRecentSearches() }
    }

    fun highlight(item: SearchHighlight) = _state.update { it.copy(highlight = item) }

    fun rememberQuery() {
        val query = _state.value.query
        viewModelScope.launch { settings.addRecentSearch(query) }
    }

    fun rememberView(view: RecentView?) {
        view ?: return
        viewModelScope.launch { settings.addRecentlyViewed(view) }
    }

    private suspend fun refreshViewedStatus(views: List<RecentView>, onlyMissing: Boolean) {
        if (seerr.connected.value != true) return
        val known = _state.value.viewedStatus
        val targets = views.take(VIEWED_STATUS_LIMIT).filter { it.tmdbId != null && (!onlyMissing || it.key !in known) }
        if (targets.isEmpty()) return
        val resolved = coroutineScope {
            targets.map { view -> async { view.tmdbId?.let { seerr.status(view.kind, it) }?.let { view.key to it } } }.awaitAll()
        }.filterNotNull()
        _state.update { it.copy(viewedStatus = it.viewedStatus + resolved) }
    }

    private suspend fun loadTrending() {
        if (seerr.connected.value != true) return
        val trending = runCatching { seerr.trending() }.getOrNull() ?: return
        _state.update { it.copy(trending = trending) }
    }

    private fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        queries.value = value
    }

    private suspend fun search(query: String) {
        if (query.length < MIN_QUERY) {
            _state.update {
                it.copy(
                    library = emptyList(), requestable = emptyList(), top = null,
                    searching = false, searched = false, resultsFor = "",
                )
            }
            return
        }
        _state.update { it.copy(searching = true) }
        coroutineScope {
            val discover = async {
                if (seerr.connected.value == true) runCatching { seerr.search(query) }.getOrNull() else null
            }
            val library = runCatching { jellyfin.search(query) }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    searched = true,
                    resultsFor = query,
                    library = library,
                    requestable = if (it.resultsFor == query) it.requestable else emptyList(),
                    top = topResult(query, library, emptyList()),
                    topRatings = null,
                )
            }
            val found = discover.await()
            val requestable = found?.items.orEmpty().filter { item ->
                item.availability != Availability.Available && library.none { it.matches(item) }
            }
            val person = matchPerson(query, found?.people.orEmpty()) ?: if (found == null && query.contains(' ')) jellyfin.findPerson(query) else null
            val top = topResult(query, library, requestable, person)
            _state.update { it.copy(searching = false, requestable = requestable, top = top) }
            val ratings = top?.let { ratingsFor(it) }
            _state.update { if (it.top == top) it.copy(topRatings = ratings) else it }
        }
    }

    private suspend fun ratingsFor(top: TopResult): ExternalRatings? = when (top) {
        is TopResult.Library -> top.item.tmdbId?.let { id ->
            seerr.ratings(if (top.item.kind == MediaKind.Series) DiscoverKind.Series else DiscoverKind.Movie, id)
        }
        is TopResult.Discover -> seerr.ratings(top.item.kind, top.item.tmdbId)
        is TopResult.Person -> null
    }

    private fun MediaItem.matches(item: DiscoverItem): Boolean {
        if (item.jellyfinId != null && item.jellyfinId == id) return true
        val sameKind = (kind == MediaKind.Series) == (item.kind == DiscoverKind.Series)
        return sameKind && tmdbId != null && tmdbId == item.tmdbId
    }

    private fun matchPerson(query: String, people: List<PersonResult>): PersonResult? {
        val q = query.trim().lowercase()
        people.firstOrNull { it.name.lowercase() == q }?.let { return it }
        if (q.length < PERSON_PREFIX_MIN || !q.contains(' ')) return null
        return people.firstOrNull { it.name.lowercase().startsWith(q) }
    }

    private fun topResult(query: String, library: List<MediaItem>, requestable: List<DiscoverItem>, person: PersonResult? = null): TopResult? {
        val q = query.lowercase()
        library.firstOrNull { it.title.lowercase() == q }?.let { return TopResult.Library(it) }
        requestable.firstOrNull { it.title.lowercase() == q }?.let { return TopResult.Discover(it) }
        person?.let { return TopResult.Person(it) }
        library.firstOrNull { it.title.lowercase().startsWith(q) }?.let { return TopResult.Library(it) }
        return library.firstOrNull()?.let(TopResult::Library) ?: requestable.firstOrNull()?.let(TopResult::Discover)
    }

    private companion object {
        const val DEBOUNCE_MS = 350L
        const val MIN_QUERY = 1
        const val MAX_QUERY = 60
        const val PERSON_PREFIX_MIN = 5
        const val VIEWED_STATUS_LIMIT = 10
    }
}
