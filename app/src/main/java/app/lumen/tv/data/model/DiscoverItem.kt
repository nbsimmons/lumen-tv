package app.lumen.tv.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class DiscoverKind { Movie, Series }

enum class Availability { None, Requested, PartiallyAvailable, Available }

data class DiscoverItem(
    val tmdbId: Int,
    val kind: DiscoverKind,
    val title: String,
    val year: Int?,
    val overview: String?,
    val rating: Double?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val availability: Availability,
    val jellyfinId: UUID?,
)

fun DiscoverItem.asMediaItem(): MediaItem = MediaItem(
    id = jellyfinId ?: UUID.nameUUIDFromBytes("tmdb-$kind-$tmdbId".toByteArray()),
    kind = if (kind == DiscoverKind.Series) MediaKind.Series else MediaKind.Movie,
    title = title,
    name = title,
    subtitle = null,
    overview = overview,
    year = year,
    officialRating = null,
    communityRating = rating?.toFloat(),
    runtimeMinutes = null,
    genres = emptyList(),
    progress = null,
    resumeTicks = 0L,
    played = false,
    favorite = false,
    posterUrl = posterUrl,
    thumbUrl = backdropUrl,
    backdropUrl = backdropUrl,
    logoUrl = null,
    seriesId = null,
    seasonId = null,
    indexNumber = null,
    parentIndexNumber = null,
    unplayedCount = null,
    tmdbId = tmdbId,
    remote = jellyfinId == null,
)

data class DiscoverSeason(
    val number: Int,
    val name: String,
    val episodes: Int?,
    val availability: Availability,
)

data class DiscoverDetail(
    val item: DiscoverItem,
    val tagline: String?,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val seasons: List<DiscoverSeason>,
    val tvdbId: Int?,
    val trailer: Trailer.YouTube?,
    val certification: String? = null,
    val studio: String? = null,
    val studioRef: StudioRef? = null,
    val cast: List<DiscoverCast> = emptyList(),
    val collection: CollectionRef? = null,
)

@Serializable
data class CollectionRef(val tmdbId: Int, val name: String, val posterUrl: String?, val backdropUrl: String?)

data class MovieCollection(
    val ref: CollectionRef,
    val overview: String?,
    val parts: List<DiscoverItem>,
) {
    val missing: List<DiscoverItem> get() = parts.filter { it.availability == Availability.None }
    val available: Int get() = parts.count { it.availability == Availability.Available || it.availability == Availability.PartiallyAvailable }
}

@kotlinx.serialization.Serializable
data class StudioRef(val tmdbId: Int, val name: String, val network: Boolean)

data class Option(val id: Int, val name: String)

enum class BrowseSort(val seerrValue: String) { Popular("popularity.desc"), TopRated("vote_average.desc"), Newest("primary_release_date.desc") }

data class BrowseQuery(
    val kind: DiscoverKind,
    val genre: Option? = null,
    val provider: Option? = null,
    val studio: StudioRef? = null,
    val sort: BrowseSort = BrowseSort.Popular,
)

enum class RequestStatus { Pending, Approved, Declined }

enum class RequestScope { Mine, Pending }

data class RequestEntry(
    val id: Int,
    val item: DiscoverItem,
    val status: RequestStatus,
    val requestedAt: Long?,
    val requestedBy: String?,
    val mine: Boolean,
    val seasons: List<Int>,
    val is4k: Boolean,
)

data class DiscoverCast(val tmdbId: Int, val name: String, val role: String?, val imageUrl: String?)

enum class DiscoverShelfKind { Trending, PopularMovies, PopularShows, UpcomingMovies, UpcomingShows, RecentlyRequested }

data class DiscoverShelf(val kind: DiscoverShelfKind, val items: List<DiscoverItem>)

data class QualityProfile(val id: Int, val name: String)

data class RequestServer(
    val id: Int,
    val name: String,
    val is4k: Boolean,
    val isDefault: Boolean,
    val defaultProfileId: Int?,
    val rootFolder: String?,
    val profiles: List<QualityProfile>,
)

data class RequestOptions(
    val canCustomise: Boolean,
    val servers: List<RequestServer>,
)

data class RequestChoice(
    val server: RequestServer?,
    val profileId: Int?,
)

data class ExternalRatings(
    val imdb: Double? = null,
    val tomatometer: Int? = null,
    val audience: Int? = null,
) {
    val isEmpty: Boolean get() = imdb == null && tomatometer == null && audience == null
}
