package app.lumen.tv.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RecentView(
    val title: String,
    val kind: DiscoverKind,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val jellyfinId: String? = null,
    val tmdbId: Int? = null,
) {
    val key: String get() = jellyfinId ?: "$kind-$tmdbId"

    fun sameTitle(other: RecentView): Boolean =
        (jellyfinId != null && jellyfinId == other.jellyfinId) || (tmdbId != null && tmdbId == other.tmdbId && kind == other.kind)

    companion object {
        fun of(item: MediaItem): RecentView? {
            val kind = when (item.kind) {
                MediaKind.Movie -> DiscoverKind.Movie
                MediaKind.Series -> DiscoverKind.Series
                else -> return null
            }
            return RecentView(item.title, kind, item.posterUrl, item.backdropUrl, item.id.toString(), item.tmdbId)
        }

        fun of(item: DiscoverItem): RecentView =
            RecentView(item.title, item.kind, item.posterUrl, item.backdropUrl, item.jellyfinId?.toString(), item.tmdbId)
    }
}
