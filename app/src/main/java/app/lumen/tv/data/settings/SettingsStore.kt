package app.lumen.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.lumen.tv.data.model.RecentView
import kotlinx.serialization.builtins.ListSerializer
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
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.settingsDataStore

    val settings: Flow<LumenSettings> = store.data.map { prefs ->
        LumenSettings(
            quality = prefs[QUALITY]?.let { saved -> StreamingQuality.entries.firstOrNull { it.bitrate == saved } } ?: StreamingQuality.Auto,
            subtitles = prefs[SUBTITLES]?.let { saved -> SubtitleMode.entries.firstOrNull { it.name == saved } } ?: SubtitleMode.ForcedOnly,
            searchKeyboard = prefs[SEARCH_KEYBOARD]?.let { saved -> SearchKeyboard.entries.firstOrNull { it.name == saved } } ?: SearchKeyboard.System,
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

    suspend fun updateCheckDue(interval: java.time.Duration): Boolean {
        val last = store.data.first()[LAST_UPDATE_CHECK] ?: return true
        return java.time.Instant.ofEpochMilli(last).plus(interval).isBefore(java.time.Instant.now())
    }

    suspend fun markUpdateChecked() {
        store.edit { it[LAST_UPDATE_CHECK] = System.currentTimeMillis() }
    }

    suspend fun dismissedUpdate(): String? = store.data.first()[DISMISSED_UPDATE]

    suspend fun setDismissedUpdate(version: String) {
        store.edit { it[DISMISSED_UPDATE] = version }
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
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val DISMISSED_UPDATE = stringPreferencesKey("dismissed_update")
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
