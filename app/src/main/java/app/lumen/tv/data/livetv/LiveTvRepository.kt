package app.lumen.tv.data.livetv

import android.content.Context
import app.lumen.tv.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream

class LiveTvRepository(context: Context, private val settings: SettingsStore) {
    private val cacheDir = File(context.cacheDir, "livetv").apply { mkdirs() }
    private val lock = Mutex()
    private var playlist: Pair<String, Playlist>? = null
    private val guides = HashMap<String, GuideParser.Result>()
    private val matches = HashMap<String, String?>()

    suspend fun playlistUrl(): String? = settings.liveTvPlaylist.first()

    suspend fun playlist(forceRefresh: Boolean = false): Playlist? = withContext(Dispatchers.IO) {
        val url = playlistUrl() ?: return@withContext null
        lock.withLock {
            playlist?.takeIf { it.first == url && !forceRefresh }?.let { return@withContext it.second }
            val text = download(url, PLAYLIST_TTL, forceRefresh).readText()
            PlaylistParser.parse(text).also { playlist = url to it }
        }
    }

    suspend fun channel(key: String): Channel? = playlist()?.channels?.firstOrNull { it.key == key }

    suspend fun guideFor(channels: List<Channel>): Guide = withContext(Dispatchers.IO) {
        val current = playlist() ?: return@withContext Guide(emptyMap(), emptyMap())
        val override = settings.liveTvGuide.first()
        val urls = (listOfNotNull(override) + guideUrlsFor(channels, current.guideUrls)).distinct()
        coroutineScope { urls.filter { it !in guides }.map { url -> async { loadGuide(url) } }.awaitAll() }
        lock.withLock {
            val loaded = urls.mapNotNull { guides[it] }
            val programmes = channels.associate { channel -> channel.key to programmesFor(channel, loaded) }
            val logos = channels.mapNotNull { channel ->
                val id = matches[channel.key] ?: return@mapNotNull null
                loaded.firstNotNullOfOrNull { it.icons[id] }?.let { channel.key to it }
            }.toMap()
            Guide(programmes, logos)
        }
    }

    private suspend fun loadGuide(url: String) {
        val now = Instant.now()
        val result = runCatching {
            val file = download(url, GUIDE_TTL, forceRefresh = false)
            file.inputStream().buffered().use { raw ->
                val stream = if (url.endsWith(".gz")) GZIPInputStream(raw) else raw
                GuideParser.parse(stream, now.minus(Duration.ofHours(3)), now.plus(Duration.ofHours(GUIDE_HOURS)))
            }
        }.getOrNull() ?: return
        lock.withLock {
            guides[url] = result
            matches.clear()
        }
    }

    private fun programmesFor(channel: Channel, loaded: List<GuideParser.Result>): List<Programme> {
        val id = matches.getOrPut(channel.key) { matchGuideId(channel, loaded) } ?: return emptyList()
        return loaded.firstNotNullOfOrNull { it.programmes[id] }.orEmpty()
    }

    private fun matchGuideId(channel: Channel, loaded: List<GuideParser.Result>): String? {
        val withProgrammes = loaded.flatMap { it.programmes.keys }.filterNot { TIMESHIFT.containsMatchIn(it) }
        if (withProgrammes.isEmpty()) return null
        channel.tvgId?.let { exact -> if (exact in withProgrammes) return exact }
        val names = loaded.fold(emptyMap<String, String>()) { acc, result -> acc + result.names }
        val targets = listOfNotNull(channel.tvgId, channel.name).map(::normalise).filter { it.length >= MIN_MATCH }.distinct()
        for (target in targets) {
            withProgrammes.firstOrNull { normalise(it) == target || names[it]?.let(::normalise) == target }?.let { return it }
        }
        for (target in targets) {
            withProgrammes
                .filter { normalise(it).startsWith(target) || names[it]?.let(::normalise)?.startsWith(target) == true }
                .minByOrNull { normalise(it).length }
                ?.let { return it }
        }
        return null
    }

    private fun guideUrlsFor(channels: List<Channel>, available: List<String>): List<String> {
        val codes = channels.mapNotNull { it.country?.uppercase() }.map { if (it == "GB") "UK" else it }.distinct()
        return codes.flatMap { code ->
            val pattern = Regex("[_/-]${code}\\d*\\.xml", RegexOption.IGNORE_CASE)
            available.filter { pattern.containsMatchIn(it) }
        }.take(MAX_GUIDES)
    }

    private fun download(url: String, ttl: Duration, forceRefresh: Boolean): File {
        val file = File(cacheDir, hash(url))
        val fresh = file.exists() && Instant.ofEpochMilli(file.lastModified()).plus(ttl).isAfter(Instant.now())
        if (fresh && !forceRefresh) return file
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val temp = File(cacheDir, "${file.name}.part")
            connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            temp.renameTo(file)
        } catch (e: Exception) {
            if (!file.exists()) throw e
        } finally {
            connection.disconnect()
        }
        return file
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun normalise(value: String): String = value.lowercase()
        .replace(COUNTRY_SUFFIX, "")
        .split(NON_ALPHANUMERIC)
        .filter { it.isNotEmpty() && it != "hd" }
        .joinToString("")

    private companion object {
        val PLAYLIST_TTL: Duration = Duration.ofHours(6)
        val GUIDE_TTL: Duration = Duration.ofHours(12)
        const val GUIDE_HOURS = 24L
        const val MAX_GUIDES = 3
        const val MIN_MATCH = 3
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 60_000
        val COUNTRY_SUFFIX = Regex("\\.[a-z]{2}$")
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        val TIMESHIFT = Regex("\\+\\d")
    }
}
