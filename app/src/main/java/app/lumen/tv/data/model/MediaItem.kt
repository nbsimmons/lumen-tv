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

enum class ShelfKind { ContinueWatching, NextUp, RecentlyAdded, NewReleases }

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

data class LibraryPage(val items: List<MediaItem>, val total: Int)

data class PersonResult(
    val jellyfinId: UUID?,
    val name: String,
    val imageUrl: String?,
    val tmdbId: Int?,
)
