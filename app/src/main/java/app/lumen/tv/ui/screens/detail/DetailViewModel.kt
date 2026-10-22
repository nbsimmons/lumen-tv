package app.lumen.tv.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.ItemDetail
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.data.model.MovieCollection
import app.lumen.tv.data.model.StudioRef
import app.lumen.tv.data.model.Season
import app.lumen.tv.data.model.Trailer
import app.lumen.tv.data.seerr.SeerrRepository
import app.lumen.tv.playback.DeviceProfileFactory
import app.lumen.tv.playback.PlaybackEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

data class DetailState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val detail: ItemDetail? = null,
    val nextUp: MediaItem? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeason: UUID? = null,
    val episodes: List<MediaItem> = emptyList(),
    val focusEpisode: UUID? = null,
    val similar: List<MediaItem> = emptyList(),
    val ratings: ExternalRatings? = null,
    val trailer: Trailer? = null,
    val collection: MovieCollection? = null,
    val studio: StudioRef? = null,
)

class DetailViewModel(
    private val itemId: UUID,
    private val focusEpisodeId: UUID?,
    private val repository: JellyfinRepository,
    events: PlaybackEvents,
    private val seerr: SeerrRepository,
    private val profiles: DeviceProfileFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailState(focusEpisode = focusEpisodeId))
    val state: StateFlow<DetailState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch(Dispatchers.Default) { profiles.capabilities }
        viewModelScope.launch { events.stopped.collect { refresh() } }
    }

    fun load() {
        val preview = repository.peek(itemId)
        if (preview != null) {
            _state.update { it.copy(detail = it.detail ?: ItemDetail(preview, emptyList(), null, preview.tmdbId, null, null)) }
            loadRatings(preview.kind, preview.tmdbId)
        }
        viewModelScope.launch {
            launch {
                val similar = repository.similar(itemId)
                _state.update { it.copy(similar = similar) }
            }
            val seriesLoaded = preview?.kind == MediaKind.Series
            if (seriesLoaded) launch { loadSeries() }
            runCatching { repository.detail(itemId) }
                .onSuccess { detail ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = false,
                            detail = detail,
                            ratings = it.ratings ?: fallbackRatings(detail),
                            trailer = detail.trailer ?: it.trailer,
                        )
                    }
                    if (preview?.tmdbId == null || preview.tmdbId != detail.tmdbId) loadRatings(detail.item.kind, detail.tmdbId)
                    if (detail.item.kind == MediaKind.Movie) loadCollection(detail.tmdbId)
                    loadStudio(detail.item.kind, detail.tmdbId)
                    if (detail.trailer == null) loadSeerrTrailer(detail)
                    if (!seriesLoaded && detail.item.kind == MediaKind.Series) launch { loadSeries() }
                }
                .onFailure { _state.update { it.copy(loading = false, error = it.detail == null) } }
        }
    }

    private fun fallbackRatings(detail: ItemDetail): ExternalRatings? =
        detail.criticRating?.let { ExternalRatings(tomatometer = it) }

    private fun discoverKind(kind: MediaKind) = if (kind == MediaKind.Series) DiscoverKind.Series else DiscoverKind.Movie

    private fun loadRatings(kind: MediaKind, tmdbId: Int?) {
        tmdbId ?: return
        viewModelScope.launch {
            seerr.restore()
            seerr.ratings(discoverKind(kind), tmdbId)?.let { ratings -> _state.update { it.copy(ratings = ratings) } }
        }
    }

    private fun loadStudio(kind: MediaKind, tmdbId: Int?) {
        tmdbId ?: return
        viewModelScope.launch {
            seerr.restore()
            if (seerr.connected.value != true) return@launch
            runCatching { seerr.studioOf(discoverKind(kind), tmdbId) }.getOrNull()?.let { studio -> _state.update { it.copy(studio = studio) } }
        }
    }

    private fun loadCollection(tmdbId: Int?) {
        tmdbId ?: return
        viewModelScope.launch {
            seerr.restore()
            if (seerr.connected.value != true) return@launch
            val ref = runCatching { seerr.collectionOf(tmdbId) }.getOrNull() ?: return@launch
            runCatching { seerr.collection(ref.tmdbId) }.onSuccess { collection -> _state.update { it.copy(collection = collection) } }
        }
    }

    private fun loadSeerrTrailer(detail: ItemDetail) {
        val tmdbId = detail.tmdbId ?: return
        viewModelScope.launch {
            seerr.restore()
            if (seerr.connected.value != true) return@launch
            seerr.trailer(discoverKind(detail.item.kind), tmdbId)?.let { trailer -> _state.update { it.copy(trailer = trailer) } }
        }
    }

    private suspend fun loadSeries() = coroutineScope {
        val nextUpCall = async { repository.nextUp(itemId) }
        val seasonsCall = async { runCatching { repository.seasons(itemId) }.getOrDefault(emptyList()) }
        val hinted = focusEpisodeId?.let { repository.peek(it)?.seasonId }
        hinted?.let(::selectSeason)
        val nextUp = nextUpCall.await()
        val seasons = seasonsCall.await()
        val focusSeason = hinted ?: focusEpisodeId?.let { id ->
            if (id == nextUp?.id) nextUp.seasonId else runCatching { repository.detail(id).item.seasonId }.getOrNull()
        }
        val initial = focusSeason ?: nextUp?.seasonId ?: seasons.firstOrNull()?.id
        _state.update {
            it.copy(
                nextUp = nextUp,
                seasons = seasons,
                focusEpisode = focusEpisodeId ?: nextUp?.id,
            )
        }
        initial?.let { selectSeason(it) }
    }

    private fun refresh() {
        val detail = _state.value.detail ?: return
        viewModelScope.launch {
            runCatching { repository.detail(itemId) }.onSuccess { fresh -> _state.update { it.copy(detail = fresh) } }
            if (detail.item.kind == MediaKind.Series) {
                val nextUp = repository.nextUp(itemId)
                _state.update { it.copy(nextUp = nextUp) }
                _state.value.selectedSeason?.let { season ->
                    val episodes = runCatching { repository.episodes(itemId, season) }.getOrNull()
                    if (episodes != null) _state.update { it.copy(episodes = episodes) }
                }
            }
        }
    }

    fun selectSeason(seasonId: UUID) {
        if (_state.value.selectedSeason == seasonId && _state.value.episodes.isNotEmpty()) return
        _state.update { it.copy(selectedSeason = seasonId) }
        viewModelScope.launch {
            val episodes = runCatching { repository.episodes(itemId, seasonId) }.getOrDefault(emptyList())
            if (_state.value.selectedSeason == seasonId) _state.update { it.copy(episodes = episodes) }
        }
    }

    fun togglePlayed() {
        val item = _state.value.detail?.item ?: return
        val played = !item.played
        updateItem { it.copy(played = played) }
        viewModelScope.launch {
            runCatching { repository.setPlayed(item.id, played) }.onFailure { updateItem { it.copy(played = !played) } }
        }
    }

    fun toggleFavorite() {
        val item = _state.value.detail?.item ?: return
        val favorite = !item.favorite
        updateItem { it.copy(favorite = favorite) }
        viewModelScope.launch {
            runCatching { repository.setFavorite(item.id, favorite) }.onFailure { updateItem { it.copy(favorite = !favorite) } }
        }
    }

    private fun updateItem(transform: (MediaItem) -> MediaItem) {
        _state.update { state -> state.copy(detail = state.detail?.let { it.copy(item = transform(it.item)) }) }
    }
}
