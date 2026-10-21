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
