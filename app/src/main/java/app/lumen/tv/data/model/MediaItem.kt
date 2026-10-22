@file:UseSerializers(UuidSerializer::class)

package app.lumen.tv.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

enum class MediaKind { Movie, Series, Season, Episode, Other }

@Serializable
data class MediaItem(
    val id: UUID,
    val kind: MediaKind,
    val title: String,
    val name: String,
    val subtitle: String?,
    val overview: String?,
    val year: Int?,
    val officialRating: String?,
    val communityRating: Float?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val progress: Float?,
    val resumeTicks: Long,
    val played: Boolean,
    val favorite: Boolean,
    val posterUrl: String?,
    val thumbUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val seriesId: UUID?,
    val seasonId: UUID?,
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val unplayedCount: Int?,
    val tmdbId: Int? = null,
    val lastPlayed: Long? = null,
    val remote: Boolean = false,
)

data class Person(
    val id: UUID,
    val name: String,
    val role: String?,
    val imageUrl: String?,
)

data class Season(
    val id: UUID,
    val name: String,
    val number: Int?,
)

data class ItemDetail(
    val item: MediaItem,
    val people: List<Person>,
    val studio: String?,
    val tmdbId: Int?,
    val criticRating: Int?,
    val trailer: Trailer?,
)

const val HOME_WATCHLIST = "watchlist"

enum class ShelfKind { ContinueWatching, NextUp, Watchlist, RecentlyAdded, NewReleases }

data class HomeSection(val key: String, val kind: ShelfKind, val library: String?)

@Serializable
data class Shelf(
    val key: String,
    val kind: ShelfKind,
    val title: String?,
    val items: List<MediaItem>,
)

@Serializable
data class LibraryView(val id: UUID, val name: String?, val movies: Boolean)

enum class LibrarySort { Title, DateAdded, Year, Rating }

enum class WatchFilter { All, Unwatched, Watched }

data class LibraryFilter(
    val genre: String? = null,
    val watched: WatchFilter = WatchFilter.All,
    val decade: Int? = null,
    val minRating: Int? = null,
    val uhdOnly: Boolean = false,
) {
    val activeCount: Int get() = listOf(genre != null, watched != WatchFilter.All, decade != null, minRating != null, uhdOnly).count { it }
}

data class LibraryPage(val items: List<MediaItem>, val total: Int)

data class PersonResult(
    val jellyfinId: UUID?,
    val name: String,
    val imageUrl: String?,
    val tmdbId: Int?,
)
