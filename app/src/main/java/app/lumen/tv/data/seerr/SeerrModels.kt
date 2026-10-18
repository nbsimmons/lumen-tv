package app.lumen.tv.data.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeerrUser(val id: Int, val displayName: String? = null, val permissions: Long = 0)

@Serializable
data class SeerrQuickConnect(val code: String, val secret: String)

@Serializable
data class SeerrQuickConnectState(val authenticated: Boolean = false)

@Serializable
data class SeerrSeasonStatus(val seasonNumber: Int, val status: Int = 1)

@Serializable
data class SeerrMediaInfo(
    val tmdbId: Int? = null,
    val mediaType: String? = null,
    val status: Int? = null,
    val jellyfinMediaId: String? = null,
    val seasons: List<SeerrSeasonStatus>? = null,
    val requests: List<SeerrMediaRequest>? = null,
)

@Serializable
data class SeerrMediaRequest(val status: Int = 1, val seasons: List<SeerrSeasonStatus>? = null)

@Serializable
data class SeerrResult(
    val id: Int,
    val popularity: Double? = null,
    val genreIds: List<Int>? = null,
    val episodeCount: Int? = null,
    val profilePath: String? = null,
    val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double? = null,
    val mediaInfo: SeerrMediaInfo? = null,
)

@Serializable
data class SeerrPage(val page: Int? = null, val totalPages: Int? = null, val results: List<SeerrResult> = emptyList())

@Serializable
data class SeerrGenre(val id: Int, val name: String)

@Serializable
data class SeerrSeason(val seasonNumber: Int, val name: String? = null, val episodeCount: Int? = null)

@Serializable
data class SeerrExternalIds(val tvdbId: Int? = null, val imdbId: String? = null)

@Serializable
data class SeerrDetails(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val runtime: Int? = null,
    val episodeRunTime: List<Int>? = null,
    val voteAverage: Double? = null,
    val genres: List<SeerrGenre>? = null,
    val seasons: List<SeerrSeason>? = null,
    val externalIds: SeerrExternalIds? = null,
    val mediaInfo: SeerrMediaInfo? = null,
    val relatedVideos: List<SeerrVideo>? = null,
    val imdbId: String? = null,
    val credits: SeerrDetailCredits? = null,
    val releases: SeerrReleases? = null,
    val contentRatings: SeerrContentRatings? = null,
    val networks: List<SeerrCompany>? = null,
    val productionCompanies: List<SeerrCompany>? = null,
)

@Serializable
data class SeerrDetailCredits(val cast: List<SeerrCastMember> = emptyList())

@Serializable
data class SeerrCastMember(val id: Int, val name: String? = null, val character: String? = null, val profilePath: String? = null)

@Serializable
data class SeerrCompany(val name: String? = null)

@Serializable
data class SeerrReleases(val results: List<SeerrReleaseCountry> = emptyList())

@Serializable
data class SeerrReleaseCountry(
    @SerialName("iso_3166_1") val country: String? = null,
    @SerialName("release_dates") val dates: List<SeerrReleaseDate> = emptyList(),
)

@Serializable
data class SeerrReleaseDate(val certification: String? = null)

@Serializable
data class SeerrContentRatings(val results: List<SeerrContentRating> = emptyList())

@Serializable
data class SeerrContentRating(@SerialName("iso_3166_1") val country: String? = null, val rating: String? = null)

@Serializable
data class SeerrVideo(val site: String? = null, val key: String? = null, val type: String? = null)

@Serializable
data class SeerrRequestMedia(val tmdbId: Int? = null, val mediaType: String? = null, val status: Int? = null)

@Serializable
data class SeerrRequest(val id: Int, val status: Int = 1, val type: String? = null, val media: SeerrRequestMedia? = null)

@Serializable
data class SeerrRequestPage(val results: List<SeerrRequest> = emptyList())

@Serializable
data class SeerrCreateRequest(
    val mediaType: String,
    val mediaId: Int,
    val tvdbId: Int? = null,
    val seasons: List<Int>? = null,
    val is4k: Boolean? = null,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
)

@Serializable
data class SeerrServiceServer(
    val id: Int,
    val name: String = "",
    val is4k: Boolean = false,
    val isDefault: Boolean = false,
    val activeProfileId: Int? = null,
    val activeDirectory: String? = null,
)

@Serializable
data class SeerrServiceProfile(val id: Int, val name: String)

@Serializable
data class SeerrServiceDetails(
    val server: SeerrServiceServer,
    val profiles: List<SeerrServiceProfile> = emptyList(),
)

@Serializable
data class SeerrRtRating(
    val criticsScore: Int? = null,
    val criticsRating: String? = null,
    val audienceScore: Int? = null,
)

@Serializable
data class SeerrImdbRating(val criticsScore: Double? = null)

@Serializable
data class SeerrCombinedRatings(val rt: SeerrRtRating? = null, val imdb: SeerrImdbRating? = null)

@Serializable
data class SeerrCredits(val cast: List<SeerrResult> = emptyList(), val crew: List<SeerrResult> = emptyList())

@Serializable
data class SeerrPersonDetails(val id: Int, val name: String? = null, val profilePath: String? = null)
