package app.lumen.tv.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.model.Availability
import app.lumen.tv.data.model.DiscoverDetail
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.MovieCollection
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.RequestChoice
import app.lumen.tv.data.model.RequestOptions
import app.lumen.tv.data.model.RequestServer
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.seerr.SeerrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RequestOutcome { Sent, Failed }

data class DiscoverDetailState(
    val detail: DiscoverDetail? = null,
    val error: Boolean = false,
    val sheetOpen: Boolean = false,
    val loadingOptions: Boolean = false,
    val options: RequestOptions? = null,
    val want4k: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val selectedSeasons: Set<Int> = emptySet(),
    val requesting: Boolean = false,
    val outcome: RequestOutcome? = null,
    val ratings: ExternalRatings? = null,
    val similar: List<DiscoverItem> = emptyList(),
    val collection: MovieCollection? = null,
) {
    val server: RequestServer? get() = options?.servers?.firstOrNull { it.id == serverId }
    val serversForVersion: List<RequestServer> get() = options?.servers.orEmpty().filter { it.is4k == want4k }
    val has4kChoice: Boolean get() = options?.servers.orEmpty().let { all -> all.any { it.is4k } && all.any { !it.is4k } }
}

class DiscoverDetailViewModel(
    private val kind: DiscoverKind,
    private val tmdbId: Int,
    private val seerr: SeerrRepository,
    private val jellyfin: JellyfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverDetailState())
    val state: StateFlow<DiscoverDetailState> = _state.asStateFlow()

    fun logoUrl(item: DiscoverItem): String? = item.jellyfinId?.let(jellyfin::logoUrl)

    init {
        load()
        viewModelScope.launch {
            seerr.ratings(kind, tmdbId)?.let { ratings -> _state.update { it.copy(ratings = ratings) } }
        }
        viewModelScope.launch {
            runCatching { seerr.recommendations(kind, tmdbId) }.onSuccess { similar -> _state.update { it.copy(similar = similar) } }
        }
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { seerr.detail(kind, tmdbId) }
                .onSuccess { detail ->
                    _state.update { it.copy(detail = detail, error = false) }
                    detail.collection?.let { ref ->
                        runCatching { seerr.collection(ref.tmdbId) }.onSuccess { collection -> _state.update { it.copy(collection = collection) } }
                    }
                }
                .onFailure { _state.update { it.copy(error = true) } }
        }
    }

    fun onRequest() {
        val detail = _state.value.detail ?: return
        if (_state.value.loadingOptions || _state.value.requesting) return
        _state.update { it.copy(loadingOptions = true, outcome = null) }
        viewModelScope.launch {
            val options = _state.value.options
                ?: runCatching { seerr.requestOptions(kind) }.getOrElse { RequestOptions(canCustomise = false, servers = emptyList()) }
            _state.update { it.copy(loadingOptions = false, options = options) }
            if (detail.item.kind == DiscoverKind.Movie && !options.canCustomise) {
                submit(seasons = null, choice = null)
                return@launch
            }
            val requestable = detail.seasons.filter { it.availability == Availability.None }.map { it.number }.toSet()
            _state.update { it.copy(sheetOpen = true, selectedSeasons = requestable) }
            selectVersion(false)
        }
    }

    fun selectVersion(is4k: Boolean) {
        _state.update { it.copy(want4k = is4k) }
        val server = _state.value.serversForVersion.let { group -> group.firstOrNull { it.isDefault } ?: group.firstOrNull() }
        selectServer(server?.id)
    }

    fun selectServer(id: Int?) = _state.update { state ->
        val server = state.options?.servers?.firstOrNull { it.id == id }
        val profile = server?.profiles?.firstOrNull { it.id == server.defaultProfileId } ?: server?.profiles?.firstOrNull()
        state.copy(serverId = server?.id, profileId = profile?.id)
    }

    fun selectProfile(id: Int) = _state.update { it.copy(profileId = id) }

    fun toggleSeason(number: Int) = _state.update {
        it.copy(selectedSeasons = if (number in it.selectedSeasons) it.selectedSeasons - number else it.selectedSeasons + number)
    }

    fun toggleAllSeasons() = _state.update { state ->
        val requestable = state.detail?.seasons.orEmpty().filter { it.availability == Availability.None }.map { it.number }.toSet()
        state.copy(selectedSeasons = if (state.selectedSeasons.containsAll(requestable)) emptySet() else requestable)
    }

    fun dismissSheet() = _state.update { it.copy(sheetOpen = false) }

    fun confirm() {
        val state = _state.value
        val isSeries = state.detail?.item?.kind == DiscoverKind.Series
        if (isSeries && state.selectedSeasons.isEmpty()) return
        val choice = if (state.options?.canCustomise == true) RequestChoice(state.server, state.profileId) else null
        submit(seasons = if (isSeries) state.selectedSeasons.sorted() else null, choice = choice)
    }

    private fun submit(seasons: List<Int>?, choice: RequestChoice?) {
        val detail = _state.value.detail ?: return
        if (_state.value.requesting) return
        _state.update { it.copy(requesting = true, outcome = null) }
        viewModelScope.launch {
            runCatching { seerr.request(detail, seasons, choice) }
                .onSuccess {
                    _state.update { it.copy(requesting = false, sheetOpen = false, outcome = RequestOutcome.Sent) }
                    load()
                }
                .onFailure { _state.update { it.copy(requesting = false, outcome = RequestOutcome.Failed) } }
        }
    }
}
