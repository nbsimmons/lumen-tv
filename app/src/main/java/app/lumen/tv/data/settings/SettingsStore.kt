package app.lumen.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.lumen.tv.data.model.RecentView
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class StreamingQuality(val bitrate: Int) {
    Auto(120_000_000),
    High(40_000_000),
    Medium(20_000_000),
    Low(8_000_000),
    Minimum(4_000_000),
}

enum class SubtitleMode { Off, ForcedOnly, Always }

enum class SearchKeyboard { System, OneLine }

data class LumenSettings(
    val quality: StreamingQuality = StreamingQuality.Auto,
    val subtitles: SubtitleMode = SubtitleMode.ForcedOnly,
    val searchKeyboard: SearchKeyboard = SearchKeyboard.System,
    val autoSkipIntros: Boolean = false,
)

@Serializable
data class RequestDefaults(val want4k: Boolean = false, val serverId: Int? = null, val profileId: Int? = null)

@Serializable
data class TrackChoice(val audioLanguage: String? = null, val subtitleLanguage: String? = null, val subtitlesOff: Boolean = false)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.settingsDataStore

    val settings: Flow<LumenSettings> = store.data.map { prefs ->
        LumenSettings(
            quality = prefs[QUALITY]?.let { saved -> StreamingQuality.entries.firstOrNull { it.bitrate == saved } } ?: StreamingQuality.Auto,
            subtitles = prefs[SUBTITLES]?.let { saved -> SubtitleMode.entries.firstOrNull { it.name == saved } } ?: SubtitleMode.ForcedOnly,
            searchKeyboard = prefs[SEARCH_KEYBOARD]?.let { saved -> SearchKeyboard.entries.firstOrNull { it.name == saved } } ?: SearchKeyboard.System,
            autoSkipIntros = prefs[AUTO_SKIP_INTROS] ?: false,
        )
    }

    val recentSearches: Flow<List<String>> = store.data.map { prefs ->
        prefs[RECENT]?.split(RECENT_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun addRecentSearch(query: String) {
        val clean = query.trim().replace(RECENT_SEPARATOR, " ")
        if (clean.length < 2) return
        store.edit { prefs ->
            val existing = prefs[RECENT]?.split(RECENT_SEPARATOR).orEmpty().filter { it.isNotBlank() }
            val updated = (listOf(clean) + existing.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX_RECENT)
            prefs[RECENT] = updated.joinToString(RECENT_SEPARATOR)
        }
    }

    val recentlyViewed: Flow<List<RecentView>> = store.data.map { prefs -> decodeViews(prefs[RECENT_VIEWS]) }

    suspend fun addRecentlyViewed(view: RecentView) {
        store.edit { prefs ->
            val existing = decodeViews(prefs[RECENT_VIEWS]).filterNot { it.sameTitle(view) }
            prefs[RECENT_VIEWS] = Json.encodeToString(VIEWS_SERIALIZER, (listOf(view) + existing).take(MAX_VIEWS))
        }
    }

    private fun decodeViews(raw: String?): List<RecentView> =
        raw?.let { runCatching { json.decodeFromString(VIEWS_SERIALIZER, it) }.getOrNull() }.orEmpty()

    suspend fun clearRecentSearches() {
        store.edit { it.remove(RECENT) }
    }

    val liveTvPlaylist: Flow<String?> = store.data.map { it[LIVE_PLAYLIST]?.ifBlank { null } }
    val liveTvGuide: Flow<String?> = store.data.map { it[LIVE_GUIDE]?.ifBlank { null } }

    suspend fun setLiveTvSources(playlist: String?, guide: String?) {
        store.edit { prefs ->
            if (playlist.isNullOrBlank()) prefs.remove(LIVE_PLAYLIST) else prefs[LIVE_PLAYLIST] = playlist.trim()
            if (guide.isNullOrBlank()) prefs.remove(LIVE_GUIDE) else prefs[LIVE_GUIDE] = guide.trim()
        }
    }

    val hiddenSections: Flow<Set<String>> = store.data.map { it[HIDDEN_SECTIONS].orEmpty() }

    suspend fun setSectionHidden(key: String, hidden: Boolean) {
        store.edit { prefs ->
            val current = prefs[HIDDEN_SECTIONS].orEmpty()
            prefs[HIDDEN_SECTIONS] = if (hidden) current + key else current - key
        }
    }

    suspend fun current(): LumenSettings = settings.first()

    suspend fun setQuality(quality: StreamingQuality) {
        store.edit { it[QUALITY] = quality.bitrate }
    }

    suspend fun setAutoSkipIntros(enabled: Boolean) {
        store.edit { it[AUTO_SKIP_INTROS] = enabled }
    }

    suspend fun trackChoice(seriesId: String): TrackChoice? = decodeChoices(store.data.first()[TRACK_CHOICES])[seriesId]

    suspend fun saveTrackChoice(seriesId: String, transform: (TrackChoice) -> TrackChoice) {
        store.edit { prefs ->
            val choices = LinkedHashMap(decodeChoices(prefs[TRACK_CHOICES]))
            val next = transform(choices.remove(seriesId) ?: TrackChoice())
            choices[seriesId] = next
            val trimmed = choices.entries.toList().takeLast(MAX_TRACK_CHOICES).associate { it.key to it.value }
            prefs[TRACK_CHOICES] = Json.encodeToString(CHOICES_SERIALIZER, trimmed)
        }
    }

    private fun decodeChoices(raw: String?): Map<String, TrackChoice> =
        raw?.let { runCatching { json.decodeFromString(CHOICES_SERIALIZER, it) }.getOrNull() }.orEmpty()

    suspend fun requestDefaults(series: Boolean): RequestDefaults? =
        store.data.first()[if (series) TV_DEFAULTS else MOVIE_DEFAULTS]?.let { runCatching { json.decodeFromString(RequestDefaults.serializer(), it) }.getOrNull() }

    suspend fun saveRequestDefaults(series: Boolean, defaults: RequestDefaults) {
        store.edit { it[if (series) TV_DEFAULTS else MOVIE_DEFAULTS] = Json.encodeToString(RequestDefaults.serializer(), defaults) }
    }

    suspend fun arrivals(): Map<String, Long>? =
        store.data.first()[ARRIVALS]?.let { runCatching { json.decodeFromString(ARRIVALS_SERIALIZER, it) }.getOrNull() }

    suspend fun saveArrivals(arrivals: Map<String, Long>) {
        store.edit { it[ARRIVALS] = Json.encodeToString(ARRIVALS_SERIALIZER, arrivals) }
    }

    suspend fun setSearchKeyboard(keyboard: SearchKeyboard) {
        store.edit { it[SEARCH_KEYBOARD] = keyboard.name }
    }

    suspend fun setSubtitles(mode: SubtitleMode) {
        store.edit { it[SUBTITLES] = mode.name }
    }

    private companion object {
        val QUALITY = intPreferencesKey("max_bitrate")
        val SUBTITLES = stringPreferencesKey("subtitle_mode")
        val SEARCH_KEYBOARD = stringPreferencesKey("search_keyboard")
        val AUTO_SKIP_INTROS = booleanPreferencesKey("auto_skip_intros")
        val TRACK_CHOICES = stringPreferencesKey("series_track_choices")
        val CHOICES_SERIALIZER = MapSerializer(String.serializer(), TrackChoice.serializer())
        const val MAX_TRACK_CHOICES = 200
        val ARRIVALS = stringPreferencesKey("request_arrivals")
        val MOVIE_DEFAULTS = stringPreferencesKey("request_defaults_movie")
        val TV_DEFAULTS = stringPreferencesKey("request_defaults_tv")
        val ARRIVALS_SERIALIZER = MapSerializer(String.serializer(), Long.serializer())
        val RECENT = stringPreferencesKey("recent_searches")
        val RECENT_VIEWS = stringPreferencesKey("recently_viewed")
        val VIEWS_SERIALIZER = ListSerializer(RecentView.serializer())
        val json = Json { ignoreUnknownKeys = true }
        val HIDDEN_SECTIONS = stringSetPreferencesKey("hidden_home_sections")
        val LIVE_PLAYLIST = stringPreferencesKey("live_playlist")
        val LIVE_GUIDE = stringPreferencesKey("live_guide")
        const val RECENT_SEPARATOR = "\n"
        const val MAX_RECENT = 8
        const val MAX_VIEWS = 15
    }
}
