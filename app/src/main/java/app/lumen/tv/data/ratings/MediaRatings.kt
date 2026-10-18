package app.lumen.tv.data.ratings

import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.data.seerr.SeerrRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MediaRatings(private val jellyfin: JellyfinRepository, private val seerr: SeerrRepository) {
    private val seriesTmdb = ConcurrentHashMap<UUID, Int>()

    suspend fun forItem(item: MediaItem): ExternalRatings? {
        val (kind, tmdbId) = when (item.kind) {
            MediaKind.Movie -> DiscoverKind.Movie to item.tmdbId
            MediaKind.Series -> DiscoverKind.Series to item.tmdbId
            MediaKind.Season, MediaKind.Episode -> DiscoverKind.Series to item.seriesId?.let { seriesTmdbId(it) }
            else -> return null
        }
        tmdbId ?: return null
        seerr.restore()
        return seerr.ratings(kind, tmdbId)
    }

    private suspend fun seriesTmdbId(id: UUID): Int? =
        seriesTmdb[id] ?: runCatching { jellyfin.tmdbId(id) }.getOrNull()?.also { seriesTmdb[id] = it }
}
