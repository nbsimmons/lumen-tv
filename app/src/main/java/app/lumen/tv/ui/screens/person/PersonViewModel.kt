package app.lumen.tv.ui.screens.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.Availability
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.PersonResult
import app.lumen.tv.data.seerr.SeerrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class PersonRef(val jellyfinId: UUID?, val tmdbId: Int?)

data class PersonState(
    val person: PersonResult? = null,
    val library: List<MediaItem> = emptyList(),
    val requestable: List<DiscoverItem> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
)

class PersonViewModel(
    private val ref: PersonRef,
    private val jellyfin: JellyfinRepository,
    private val seerr: SeerrRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PersonState())
    val state: StateFlow<PersonState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            seerr.restore()
            val fromLibrary = ref.jellyfinId?.let { id -> runCatching { jellyfin.person(id) }.getOrNull() }
            val fromTmdb = if (fromLibrary == null && ref.tmdbId != null && seerr.connected.value == true) {
                runCatching { seerr.person(ref.tmdbId) }.getOrNull()
            } else {
                null
            }
            val person = fromLibrary ?: fromTmdb
            if (person == null) {
                _state.update { it.copy(loading = false, error = true) }
                return@launch
            }
            val libraryId = person.jellyfinId ?: jellyfin.findPerson(person.name)?.jellyfinId
            val library = libraryId?.let { id -> runCatching { jellyfin.itemsWithPerson(id) }.getOrNull() }.orEmpty()
            _state.update { it.copy(person = person, library = library, loading = false) }
            loadCredits()
        }
        viewModelScope.launch { seerr.changes.collect { loadCredits() } }
    }

    private suspend fun loadCredits() {
        val tmdbId = _state.value.person?.tmdbId ?: ref.tmdbId ?: return
        if (seerr.connected.value != true) return
        val credits = runCatching { seerr.personCredits(tmdbId) }.getOrNull() ?: return
        _state.update { state -> state.copy(requestable = credits.filter { it.availability != Availability.Available }) }
    }
}
