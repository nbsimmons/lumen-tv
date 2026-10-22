package app.lumen.tv.data.logos

import app.lumen.tv.BuildConfig
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

enum class TmdbKeyCheck { Valid, Invalid, Unreachable }

@Serializable
private data class TmdbImages(val logos: List<TmdbImage> = emptyList())

@Serializable
private data class TmdbImage(
    @SerialName("file_path") val path: String,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val votes: Double = 0.0,
)

class TitleLogos(private val jellyfin: JellyfinRepository, private val sessions: SessionStore) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, String>()
    private val misses: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun cached(item: DiscoverItem): String? = item.jellyfinId?.let(jellyfin::logoUrl) ?: cache[key(item.kind, item.tmdbId)]

    fun cachedCollection(tmdbId: Int): String? = cache["collection-$tmdbId"]

    suspend fun logo(item: DiscoverItem): String? {
        item.jellyfinId?.let { return jellyfin.logoUrl(it) }
        val path = if (item.kind == DiscoverKind.Movie) "movie/${item.tmdbId}" else "tv/${item.tmdbId}"
        return fetch(key(item.kind, item.tmdbId), path)
    }

    suspend fun collectionLogo(tmdbId: Int): String? = fetch("collection-$tmdbId", "collection/$tmdbId")

    suspend fun check(key: String): TmdbKeyCheck = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(request("configuration", key.trim())).execute().use { response ->
                when {
                    response.isSuccessful -> TmdbKeyCheck.Valid
                    response.code == 401 -> TmdbKeyCheck.Invalid
                    else -> TmdbKeyCheck.Unreachable
                }
            }
        }.getOrDefault(TmdbKeyCheck.Unreachable)
    }

    fun forget() {
        cache.clear()
        misses.clear()
    }

    private suspend fun fetch(cacheKey: String, path: String): String? {
        cache[cacheKey]?.let { return it }
        if (cacheKey in misses) return null
        val apiKey = sessions.tmdbKey.first() ?: return null
        val url = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(request("$path/images?include_image_language=en,null", apiKey)).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    json.decodeFromString<TmdbImages>(response.body.string()).logos
                        .filterNot { it.path.endsWith(".svg") }
                        .sortedWith(compareBy<TmdbImage> { if (it.language == "en") 0 else 1 }.thenByDescending { it.votes })
                        .firstOrNull()
                        ?.let { IMAGE_BASE + it.path }
                }
            }.getOrNull()
        }
        if (url != null) cache[cacheKey] = url else misses += cacheKey
        return url
    }

    private fun request(path: String, apiKey: String): Request {
        val bearer = apiKey.startsWith("eyJ")
        val separator = if ('?' in path) '&' else '?'
        val url = if (bearer) "$API_BASE$path" else "$API_BASE$path${separator}api_key=$apiKey"
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Lumen/${BuildConfig.VERSION_NAME}")
            .apply { if (bearer) header("Authorization", "Bearer $apiKey") }
            .build()
    }

    private fun key(kind: DiscoverKind, tmdbId: Int) = "$kind-$tmdbId"

    private companion object {
        const val API_BASE = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    }
}
