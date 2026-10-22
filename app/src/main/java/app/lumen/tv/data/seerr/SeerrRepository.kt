package app.lumen.tv.data.seerr

import app.lumen.tv.data.jellyfin.JellyfinClient
import app.lumen.tv.data.model.Availability
import app.lumen.tv.data.model.BrowseQuery
import app.lumen.tv.data.model.BrowseSort
import app.lumen.tv.data.model.CollectionRef
import app.lumen.tv.data.model.Option
import app.lumen.tv.data.model.RequestEntry
import app.lumen.tv.data.model.RequestScope
import app.lumen.tv.data.model.RequestStatus
import app.lumen.tv.data.model.StudioRef
import app.lumen.tv.data.model.MovieCollection
import app.lumen.tv.data.model.DiscoverCast
import app.lumen.tv.data.model.DiscoverDetail
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.DiscoverSeason
import app.lumen.tv.data.model.DiscoverShelf
import app.lumen.tv.data.model.DiscoverShelfKind
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.QualityProfile
import app.lumen.tv.data.model.RequestChoice
import app.lumen.tv.data.model.RequestOptions
import app.lumen.tv.data.model.RequestServer
import app.lumen.tv.data.model.PersonResult
import app.lumen.tv.data.model.Trailer
import app.lumen.tv.data.ratings.ImdbRatings
import app.lumen.tv.data.session.SeerrSession
import app.lumen.tv.data.session.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

class SeerrRepository(
    private val sessions: SessionStore,
    private val jellyfin: JellyfinClient,
    private val imdb: ImdbRatings,
) {
    private val http = HttpClient(OkHttp) {
        engine {
            config { dispatcher(Dispatcher().apply { maxRequestsPerHost = REQUESTS_PER_HOST }) }
        }
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 8_000
        }
    }

    private var session: SeerrSession? = null
    private var userId: Int? = null
    private var permissions: Long? = null
    private val ratingsCache = ConcurrentHashMap<Pair<DiscoverKind, Int>, ExternalRatings>()
    private val posterCache = ConcurrentHashMap<Pair<DiscoverKind, Int>, SeerrDetails>()
    private val requested = ConcurrentHashMap.newKeySet<Pair<DiscoverKind, Int>>()
    private val inflight = ConcurrentHashMap<Pair<DiscoverKind, Int>, Deferred<SeerrDetails>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val url: String? get() = session?.url

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val _connected = MutableStateFlow<Boolean?>(null)
    val connected: StateFlow<Boolean?> = _connected.asStateFlow()

    suspend fun restore() {
        if (_connected.value != null) return
        session = sessions.seerr()
        _connected.value = session != null
    }

    suspend fun suggestedUrl(): String {
        sessions.lastSeerrUrl()?.let { return it }
        val host = jellyfin.session.value?.serverUrl?.let { runCatching { URI(it).host }.getOrNull() } ?: return ""
        return "http://$host:5055"
    }

    suspend fun checkServer(rawUrl: String): String {
        val url = normalise(rawUrl)
        http.get("$url/api/v1/status")
        return url
    }

    suspend fun loginWithPassword(url: String, username: String, password: String) {
        val response = http.post("$url/api/v1/auth/jellyfin") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("username" to username, "password" to password))
        }
        complete(url, response)
    }

    suspend fun startQuickConnect(url: String): SeerrQuickConnect =
        http.post("$url/api/v1/auth/jellyfin/quickconnect/initiate").body()

    suspend fun isQuickConnectApproved(url: String, secret: String): Boolean =
        http.get("$url/api/v1/auth/jellyfin/quickconnect/check") { parameter("secret", secret) }
            .body<SeerrQuickConnectState>().authenticated

    suspend fun finishQuickConnect(url: String, secret: String) {
        val response = http.post("$url/api/v1/auth/jellyfin/quickconnect/authenticate") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("secret" to secret))
        }
        complete(url, response)
    }

    private val _autoConnecting = MutableStateFlow(false)
    val autoConnecting: StateFlow<Boolean> = _autoConnecting.asStateFlow()

    suspend fun autoConnect(candidates: List<String>): Boolean {
        _autoConnecting.value = true
        try {
            for (candidate in candidates.distinct()) {
                val url = runCatching { checkServer(candidate) }.getOrNull() ?: continue
                if (connectWithJellyfinSession(url)) return true
            }
            return false
        } finally {
            _autoConnecting.value = false
        }
    }

    suspend fun connectWithJellyfinSession(url: String): Boolean = runCatching {
        val code = startQuickConnect(url)
        if (!jellyfin.authorizeQuickConnect(code.code)) return@runCatching false
        repeat(APPROVAL_POLLS) {
            if (isQuickConnectApproved(url, code.secret)) {
                finishQuickConnect(url, code.secret)
                return@runCatching true
            }
            delay(APPROVAL_POLL_MS)
        }
        false
    }.getOrDefault(false)

    suspend fun disconnect() {
        sessions.clearSeerr()
        session = null
        userId = null
        permissions = null
        _connected.value = false
    }

    suspend fun studioOf(kind: DiscoverKind, tmdbId: Int): StudioRef? = studioRef(detailsFor(kind, tmdbId))

    private fun studioRef(details: SeerrDetails): StudioRef? {
        details.networks.orEmpty().firstOrNull { it.id != null && !it.name.isNullOrBlank() }?.let { return StudioRef(it.id!!, it.name!!, network = true) }
        return details.productionCompanies.orEmpty().firstOrNull { it.id != null && !it.name.isNullOrBlank() }?.let { StudioRef(it.id!!, it.name!!, network = false) }
    }

    private val genreCache = ConcurrentHashMap<DiscoverKind, List<Option>>()
    private val providerCache = ConcurrentHashMap<DiscoverKind, List<Option>>()

    suspend fun genres(kind: DiscoverKind): List<Option> = genreCache[kind] ?: authed<List<SeerrNamedId>>(if (kind == DiscoverKind.Movie) "genres/movie" else "genres/tv")
        .mapNotNull { genre -> genre.name?.let { Option(genre.id, it) } }
        .also { genreCache[kind] = it }

    suspend fun providers(kind: DiscoverKind): List<Option> = providerCache[kind] ?: authed<List<SeerrWatchProvider>>(if (kind == DiscoverKind.Movie) "watchproviders/movies" else "watchproviders/tv") {
        parameter("watchRegion", WATCH_REGION)
    }.sortedBy { it.displayPriority ?: Int.MAX_VALUE }
        .mapNotNull { provider -> provider.name?.let { Option(provider.id, it) } }
        .take(MAX_PROVIDERS)
        .also { providerCache[kind] = it }

    suspend fun browse(query: BrowseQuery, page: Int): Pair<List<DiscoverItem>, Boolean> {
        val movie = query.kind == DiscoverKind.Movie
        val body = authed<SeerrPage>(if (movie) "discover/movies" else "discover/tv") {
            parameter("page", page)
            parameter("sortBy", if (!movie && query.sort == BrowseSort.Newest) "first_air_date.desc" else query.sort.seerrValue)
            query.genre?.let { parameter("genre", it.id) }
            query.provider?.let {
                parameter("watchProviders", it.id)
                parameter("watchRegion", WATCH_REGION)
            }
            query.studio?.let { parameter(if (it.network) "network" else "studio", it.tmdbId) }
            if (query.sort == BrowseSort.TopRated) parameter("voteCountGte", MIN_VOTES_FOR_TOP)
        }
        val mediaType = if (movie) "movie" else "tv"
        val items = body.results.mapNotNull { toItem(it.copy(mediaType = it.mediaType ?: mediaType)) }
        return items to ((body.page ?: page) < (body.totalPages ?: page))
    }

    suspend fun browseShelves(): List<DiscoverShelf> = coroutineScope {
        val sources = listOf(
            DiscoverShelfKind.Trending to "discover/trending",
            DiscoverShelfKind.PopularMovies to "discover/movies",
            DiscoverShelfKind.PopularShows to "discover/tv",
            DiscoverShelfKind.UpcomingMovies to "discover/movies/upcoming",
            DiscoverShelfKind.UpcomingShows to "discover/tv/upcoming",
        )
        sources.map { (kind, path) ->
            async {
                val page = runCatching { authed<SeerrPage>(path) { parameter("page", 1) } }.getOrNull()
                DiscoverShelf(kind, page?.results.orEmpty().mapNotNull(::toItem))
            }
        }.awaitAll().filter { it.items.isNotEmpty() }
    }

    suspend fun ratings(kind: DiscoverKind, tmdbId: Int): ExternalRatings? {
        if (_connected.value != true) return null
        ratingsCache[kind to tmdbId]?.let { return it.takeUnless { cached -> cached.isEmpty } }
        var definitive = true
        val ratings = runCatching {
            if (kind == DiscoverKind.Movie) {
                val combined = authed<SeerrCombinedRatings>("movie/$tmdbId/ratingscombined")
                ExternalRatings(
                    imdb = combined.imdb?.criticsScore?.takeIf { it > 0 },
                    tomatometer = combined.rt?.criticsScore,
                    audience = combined.rt?.audienceScore,
                )
            } else {
                val rt = authed<SeerrRtRating>("tv/$tmdbId/ratings")
                ExternalRatings(tomatometer = rt.criticsScore)
            }
        }.getOrElse { error ->
            definitive = error is ClientRequestException
            null
        } ?: ExternalRatings()
        val withImdb = if (ratings.imdb != null) {
            ratings
        } else {
            val imdbId = runCatching { detailsFor(kind, tmdbId).let { it.externalIds?.imdbId ?: it.imdbId } }
                .onFailure { definitive = false }
                .getOrNull()
            imdb.lookup(imdbId)?.let { ratings.copy(imdb = it.rating) } ?: ratings
        }
        if (!withImdb.isEmpty || definitive) ratingsCache[kind to tmdbId] = withImdb
        return withImdb.takeUnless { it.isEmpty }
    }

    suspend fun personCredits(tmdbId: Int): List<DiscoverItem> {
        val credits = authed<SeerrCredits>("person/$tmdbId/combined_credits")
        return (credits.cast + credits.crew)
            .distinctBy { it.mediaType to it.id }
            .filterNot { credit -> credit.genreIds.orEmpty().any { it in NON_ROLE_GENRES } }
            .filterNot { it.mediaType == "tv" && (it.episodeCount ?: Int.MAX_VALUE) <= GUEST_EPISODES }
            .sortedByDescending { it.popularity ?: 0.0 }
            .mapNotNull(::toItem)
    }

    suspend fun trending(): List<DiscoverItem> =
        authed<SeerrPage>("discover/trending") { parameter("page", 1) }.results.mapNotNull(::toItem)

    @Volatile
    private var requestsCache: DiscoverShelf? = null

    fun cachedRequests(): DiscoverShelf? = requestsCache

    suspend fun requestsShelf(): DiscoverShelf {
        val items = runCatching { myRequests() }.getOrNull() ?: return requestsCache ?: DiscoverShelf(DiscoverShelfKind.RecentlyRequested, emptyList())
        return DiscoverShelf(DiscoverShelfKind.RecentlyRequested, items).also { requestsCache = it }
    }

    suspend fun warmRequests() {
        restore()
        if (_connected.value == true && requestsCache == null) requestsShelf()
    }

    suspend fun search(query: String): SearchResults {
        val results = authed<SeerrPage>("search") {
            url.encodedParameters.append("query", query.encodeURLParameter())
            parameter("page", 1)
        }.results
        val people = results.filter { it.mediaType == "person" && it.name != null }.map { person ->
            PersonResult(jellyfinId = null, name = person.name.orEmpty(), imageUrl = person.profilePath?.let { PROFILE + it }, tmdbId = person.id)
        }
        return SearchResults(results.mapNotNull(::toItem), people)
    }

    suspend fun person(tmdbId: Int): PersonResult {
        val details = authed<SeerrPersonDetails>("person/$tmdbId")
        return PersonResult(jellyfinId = null, name = details.name.orEmpty(), imageUrl = details.profilePath?.let { PROFILE + it }, tmdbId = details.id)
    }

    suspend fun detail(kind: DiscoverKind, tmdbId: Int): DiscoverDetail {
        val details = fetchDetails(kind, tmdbId)
        val seasonStatus = details.mediaInfo?.seasons.orEmpty().associate { it.seasonNumber to availability(it.status) }
        val openRequests = details.mediaInfo?.requests.orEmpty().filter { it.status != REQUEST_DECLINED }
        val requestedSeasons = openRequests.flatMap { it.seasons.orEmpty() }.map { it.seasonNumber }.toSet()
        val item = toItem(details, kind)
        return DiscoverDetail(
            item = if (item.availability == Availability.None && openRequests.isNotEmpty()) item.copy(availability = Availability.Requested) else item,
            tagline = details.tagline?.takeIf { it.isNotBlank() },
            genres = details.genres.orEmpty().map { it.name },
            runtimeMinutes = details.runtime ?: details.episodeRunTime?.firstOrNull(),
            seasons = details.seasons.orEmpty()
                .filter { it.seasonNumber > 0 }
                .map { season ->
                    val status = seasonStatus[season.seasonNumber]?.takeIf { it != Availability.None }
                        ?: if (season.seasonNumber in requestedSeasons) Availability.Requested else Availability.None
                    DiscoverSeason(season.seasonNumber, season.name ?: "", season.episodeCount, status)
                },
            tvdbId = details.externalIds?.tvdbId,
            trailer = trailerOf(details),
            certification = certificationOf(details),
            collection = details.collection?.let(::collectionRef),
            studio = (details.networks ?: details.productionCompanies).orEmpty().firstNotNullOfOrNull { it.name?.takeIf(String::isNotBlank) },
            studioRef = studioRef(details),
            cast = details.credits?.cast.orEmpty().take(MAX_CAST).mapNotNull { member ->
                member.name?.let { DiscoverCast(member.id, it, member.character?.takeIf(String::isNotBlank), member.profilePath?.let { path -> PROFILE + path }) }
            },
        )
    }

    suspend fun status(kind: DiscoverKind, tmdbId: Int): Availability? =
        runCatching { detail(kind, tmdbId).item.availability }.getOrNull()

    @Volatile
    private var watchlistKeys: Set<Pair<DiscoverKind, Int>>? = null

    suspend fun watchlist(): List<DiscoverItem> = coroutineScope {
        val entries = authed<SeerrWatchlistPage>("discover/watchlist") { parameter("page", 1) }.results
        val keys = entries.mapNotNull { entry -> entry.tmdbId?.let { kindOf(entry.mediaType) to it } }
        watchlistKeys = keys.toSet()
        keys.map { (kind, id) -> async { runCatching { toItem(detailsFor(kind, id), kind) }.getOrNull() } }.awaitAll().filterNotNull()
    }

    suspend fun isWatchlisted(kind: DiscoverKind, tmdbId: Int): Boolean {
        val keys = watchlistKeys ?: runCatching { watchlist() }.map { watchlistKeys.orEmpty() }.getOrDefault(emptySet())
        return (kind to tmdbId) in keys
    }

    suspend fun setWatchlisted(item: DiscoverItem, watchlisted: Boolean) {
        val type = if (item.kind == DiscoverKind.Movie) "movie" else "tv"
        if (watchlisted) {
            authedSend("watchlist", SeerrWatchlistRequest(item.tmdbId, type, item.title))
        } else {
            authedDelete("watchlist/${item.tmdbId}") { parameter("mediaType", type) }
        }
        watchlistKeys = watchlistKeys.orEmpty().let { if (watchlisted) it + (item.kind to item.tmdbId) else it - (item.kind to item.tmdbId) }
        _changes.tryEmit(Unit)
    }

    private fun kindOf(mediaType: String?) = if (mediaType == "tv") DiscoverKind.Series else DiscoverKind.Movie

    suspend fun collectionOf(movieTmdbId: Int): CollectionRef? =
        detailsFor(DiscoverKind.Movie, movieTmdbId).collection?.let(::collectionRef)

    suspend fun collection(tmdbId: Int): MovieCollection {
        val body = authed<SeerrCollection>("collection/$tmdbId")
        val parts = body.parts
            .mapNotNull { toItem(it.copy(mediaType = it.mediaType ?: "movie")) }
            .sortedWith(compareBy(nullsLast()) { it.year })
        return MovieCollection(
            ref = CollectionRef(body.id, body.name.orEmpty(), body.posterPath?.let { POSTER + it }, body.backdropPath?.let { BACKDROP + it }),
            overview = body.overview?.takeIf { it.isNotBlank() },
            parts = parts,
        )
    }

    suspend fun requestMovies(tmdbIds: List<Int>, choice: RequestChoice?): Int {
        var sent = 0
        for (id in tmdbIds) {
            val server = choice?.server
            val ok = runCatching {
                authedPost(
                    "request",
                    SeerrCreateRequest(
                        mediaType = "movie",
                        mediaId = id,
                        is4k = server?.is4k,
                        serverId = server?.id,
                        profileId = choice?.profileId,
                        rootFolder = server?.rootFolder,
                    ),
                )
            }.isSuccess
            if (ok) {
                requested += DiscoverKind.Movie to id
                sent++
            }
        }
        if (sent > 0) _changes.tryEmit(Unit)
        return sent
    }

    private fun collectionRef(ref: SeerrCollectionRef) =
        CollectionRef(ref.id, ref.name.orEmpty(), ref.posterPath?.let { POSTER + it }, ref.backdropPath?.let { BACKDROP + it })

    suspend fun recommendations(kind: DiscoverKind, tmdbId: Int): List<DiscoverItem> {
        val path = if (kind == DiscoverKind.Movie) "movie/$tmdbId/recommendations" else "tv/$tmdbId/recommendations"
        val mediaType = if (kind == DiscoverKind.Movie) "movie" else "tv"
        return authed<SeerrPage>(path).results.mapNotNull { result -> toItem(result.copy(mediaType = result.mediaType ?: mediaType)) }
    }

    private fun certificationOf(details: SeerrDetails): String? {
        val movie = details.releases?.results.orEmpty().associate { country ->
            country.country to country.dates.firstNotNullOfOrNull { it.certification?.takeIf(String::isNotBlank) }
        }
        val tv = details.contentRatings?.results.orEmpty().associate { it.country to it.rating?.takeIf(String::isNotBlank) }
        return CERT_REGIONS.firstNotNullOfOrNull { region -> movie[region] ?: tv[region] }
    }

    suspend fun requestOptions(kind: DiscoverKind): RequestOptions = coroutineScope {
        val granted = currentPermissions()
        val canCustomise = granted.has(ADMIN) || granted.has(MANAGE_REQUESTS) || granted.has(REQUEST_ADVANCED)
        if (!canCustomise) return@coroutineScope RequestOptions(canCustomise = false, servers = emptyList())
        val service = if (kind == DiscoverKind.Movie) "radarr" else "sonarr"
        val can4k = granted.has(ADMIN) || granted.has(REQUEST_4K) ||
            granted.has(if (kind == DiscoverKind.Movie) REQUEST_4K_MOVIE else REQUEST_4K_TV)
        val servers = authed<List<SeerrServiceServer>>("service/$service").filter { !it.is4k || can4k }
        val detailed = servers.map { server ->
            async {
                val profiles = runCatching { authed<SeerrServiceDetails>("service/$service/${server.id}").profiles }.getOrDefault(emptyList())
                RequestServer(
                    id = server.id,
                    name = server.name,
                    is4k = server.is4k,
                    isDefault = server.isDefault,
                    defaultProfileId = server.activeProfileId,
                    rootFolder = server.activeDirectory,
                    profiles = profiles.map { QualityProfile(it.id, it.name) },
                )
            }
        }.awaitAll()
        RequestOptions(canCustomise = true, servers = detailed)
    }

    suspend fun trailer(kind: DiscoverKind, tmdbId: Int): Trailer.YouTube? =
        runCatching { trailerOf(detailsFor(kind, tmdbId)) }.getOrNull()

    private suspend fun detailsFor(kind: DiscoverKind, tmdbId: Int): SeerrDetails =
        posterCache[kind to tmdbId] ?: fetchDetails(kind, tmdbId)

    private suspend fun fetchDetails(kind: DiscoverKind, tmdbId: Int): SeerrDetails {
        val key = kind to tmdbId
        val call = inflight.computeIfAbsent(key) {
            scope.async { authed<SeerrDetails>(if (kind == DiscoverKind.Movie) "movie/$tmdbId" else "tv/$tmdbId") }
        }
        try {
            return call.await().also { posterCache[key] = it }
        } finally {
            inflight.remove(key, call)
        }
    }

    private fun trailerOf(details: SeerrDetails): Trailer.YouTube? {
        val youtube = details.relatedVideos.orEmpty().filter { it.site == "YouTube" && !it.key.isNullOrBlank() }
        val best = youtube.firstOrNull { it.type == "Trailer" } ?: youtube.firstOrNull { it.type == "Teaser" } ?: youtube.firstOrNull()
        return best?.key?.let(Trailer::YouTube)
    }

    suspend fun request(detail: DiscoverDetail, seasons: List<Int>?, choice: RequestChoice? = null) {
        val item = detail.item
        val server = choice?.server
        authedPost(
            "request",
            SeerrCreateRequest(
                mediaType = if (item.kind == DiscoverKind.Movie) "movie" else "tv",
                mediaId = item.tmdbId,
                tvdbId = detail.tvdbId,
                seasons = seasons,
                is4k = server?.is4k,
                serverId = server?.id,
                profileId = choice?.profileId,
                rootFolder = server?.rootFolder,
            ),
        )
        requested += item.kind to item.tmdbId
        _changes.tryEmit(Unit)
    }

    private suspend fun currentUser(): SeerrUser = authed<SeerrUser>("auth/me").also {
        userId = it.id
        permissions = it.permissions
    }

    private suspend fun currentPermissions(): Long = permissions ?: currentUser().permissions

    private fun Long.has(flag: Long) = this and flag != 0L

    suspend fun canManageRequests(): Boolean = runCatching { currentPermissions().let { it.has(ADMIN) || it.has(MANAGE_REQUESTS) } }.getOrDefault(false)

    suspend fun requests(scope: RequestScope): List<RequestEntry> = coroutineScope {
        val me = userId ?: currentUser().id
        val page = authed<SeerrRequestPage>("request") {
            parameter("take", REQUEST_PAGE)
            parameter("skip", 0)
            parameter("sort", "added")
            parameter("sortDirection", "desc")
            when (scope) {
                RequestScope.Mine -> parameter("requestedBy", me)
                RequestScope.Pending -> parameter("filter", "pending")
            }
        }
        page.results.mapNotNull { request ->
            val media = request.media ?: return@mapNotNull null
            val tmdbId = media.tmdbId ?: return@mapNotNull null
            val kind = if (media.mediaType == "tv") DiscoverKind.Series else DiscoverKind.Movie
            async {
                val item = runCatching { toItem(detailsFor(kind, tmdbId), kind) }.getOrNull() ?: return@async null
                RequestEntry(
                    id = request.id,
                    item = item.copy(availability = availability(media.status, kind to tmdbId)),
                    status = when (request.status) {
                        REQUEST_DECLINED -> RequestStatus.Declined
                        REQUEST_PENDING -> RequestStatus.Pending
                        else -> RequestStatus.Approved
                    },
                    requestedAt = request.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
                    requestedBy = request.requestedBy?.displayName,
                    mine = request.requestedBy?.id == null || request.requestedBy.id == me,
                    seasons = request.seasons.orEmpty().map { it.seasonNumber }.sorted(),
                    is4k = request.is4k == true,
                )
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun cancelRequest(id: Int) {
        authedDelete("request/$id")
        _changes.tryEmit(Unit)
    }

    suspend fun reviewRequest(id: Int, approve: Boolean) {
        authedSend("request/$id/${if (approve) "approve" else "decline"}", emptyMap<String, String>())
        _changes.tryEmit(Unit)
    }

    private suspend fun myRequests(): List<DiscoverItem> = coroutineScope {
        val me = userId ?: currentUser().id
        val page = authed<SeerrRequestPage>("request") {
            parameter("take", REQUEST_LIMIT)
            parameter("skip", 0)
            parameter("sort", "added")
            parameter("requestedBy", me)
        }
        page.results.mapNotNull { request ->
            val media = request.media ?: return@mapNotNull null
            val tmdbId = media.tmdbId ?: return@mapNotNull null
            val kind = if (media.mediaType == "tv") DiscoverKind.Series else DiscoverKind.Movie
            async {
                posterCache[kind to tmdbId]?.let { cached -> toItem(cached, kind).copy(availability = availability(media.status)) }
                    ?: runCatching { detail(kind, tmdbId).item }.getOrNull()
            }
        }.awaitAll().filterNotNull().distinctBy { it.kind to it.tmdbId }
    }

    private suspend fun complete(url: String, response: HttpResponse) {
        val cookie = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .firstOrNull { it.startsWith(COOKIE_NAME) }
            ?.substringBefore(';')
            ?: error("Seerr did not return a session")
        val saved = SeerrSession(url, cookie)
        sessions.saveSeerr(saved)
        session = saved
        val user = response.body<SeerrUser>()
        userId = user.id
        permissions = user.permissions
        _connected.value = true
    }

    private suspend inline fun <reified T> authed(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): T {
        val current = session ?: throw SeerrNotConnected()
        return guard {
            http.get("${current.url}/api/v1/$path") {
                header(HttpHeaders.Cookie, current.cookie)
                block()
            }.body()
        }
    }

    private suspend inline fun <reified B : Any> authedSend(path: String, payload: B) {
        val current = session ?: throw SeerrNotConnected()
        guard {
            http.post("${current.url}/api/v1/$path") {
                header(HttpHeaders.Cookie, current.cookie)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        }
    }

    private suspend fun authedDelete(path: String, block: HttpRequestBuilder.() -> Unit = {}) {
        val current = session ?: throw SeerrNotConnected()
        guard {
            http.delete("${current.url}/api/v1/$path") {
                header(HttpHeaders.Cookie, current.cookie)
                block()
            }
        }
    }

    private suspend fun authedPost(path: String, payload: SeerrCreateRequest) {
        val current = session ?: throw SeerrNotConnected()
        guard {
            http.post("${current.url}/api/v1/$path") {
                header(HttpHeaders.Cookie, current.cookie)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        }
    }

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.Unauthorized) disconnect()
        throw e
    }

    private fun toItem(result: SeerrResult): DiscoverItem? {
        val kind = when (result.mediaType) {
            "movie" -> DiscoverKind.Movie
            "tv" -> DiscoverKind.Series
            else -> return null
        }
        return DiscoverItem(
            tmdbId = result.id,
            kind = kind,
            title = result.title ?: result.name ?: return null,
            year = (result.releaseDate ?: result.firstAirDate)?.take(4)?.toIntOrNull(),
            overview = result.overview,
            rating = result.voteAverage?.takeIf { it > 0 },
            posterUrl = result.posterPath?.let { POSTER + it },
            backdropUrl = result.backdropPath?.let { BACKDROP + it },
            availability = availability(result.mediaInfo?.status, kind to result.id),
            jellyfinId = result.mediaInfo?.jellyfinMediaId?.let(::parseJellyfinId),
        )
    }

    private fun toItem(details: SeerrDetails, kind: DiscoverKind) = DiscoverItem(
        tmdbId = details.id,
        kind = kind,
        title = details.title ?: details.name.orEmpty(),
        year = (details.releaseDate ?: details.firstAirDate)?.take(4)?.toIntOrNull(),
        overview = details.overview,
        rating = details.voteAverage?.takeIf { it > 0 },
        posterUrl = details.posterPath?.let { POSTER + it },
        backdropUrl = details.backdropPath?.let { BACKDROP + it },
        availability = availability(details.mediaInfo?.status, kind to details.id),
        jellyfinId = details.mediaInfo?.jellyfinMediaId?.let(::parseJellyfinId),
    )

    private fun availability(status: Int?, key: Pair<DiscoverKind, Int>? = null): Availability = when (status) {
        2, 3 -> Availability.Requested
        4 -> Availability.PartiallyAvailable
        5 -> Availability.Available
        else -> if (key != null && key in requested) Availability.Requested else Availability.None
    }

    private fun parseJellyfinId(raw: String): UUID? = runCatching {
        if (raw.length == 32) {
            UUID.fromString("${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}")
        } else {
            UUID.fromString(raw)
        }
    }.getOrNull()

    private fun normalise(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
    }

    private companion object {
        const val REQUEST_DECLINED = 3
        const val REQUEST_PENDING = 1
        const val REQUEST_PAGE = 50
        const val MAX_PROVIDERS = 30
        const val MIN_VOTES_FOR_TOP = 200
        val WATCH_REGION: String = java.util.Locale.getDefault().country.takeIf { it.length == 2 } ?: "GB"
        const val REQUESTS_PER_HOST = 10
        const val MAX_CAST = 20
        val CERT_REGIONS = listOf("GB", "US")
        const val COOKIE_NAME = "connect.sid="
        const val POSTER = "https://image.tmdb.org/t/p/w342"
        const val BACKDROP = "https://image.tmdb.org/t/p/w1280"
        const val PROFILE = "https://image.tmdb.org/t/p/w342"
        const val REQUEST_LIMIT = 20
        const val GUEST_EPISODES = 2
        val NON_ROLE_GENRES = setOf(10763, 10764, 10767)
        const val APPROVAL_POLLS = 10
        const val APPROVAL_POLL_MS = 500L
        const val ADMIN = 2L
        const val MANAGE_REQUESTS = 16L
        const val REQUEST_4K = 1024L
        const val REQUEST_4K_MOVIE = 2048L
        const val REQUEST_4K_TV = 4096L
        const val REQUEST_ADVANCED = 8192L
    }
}

class SeerrNotConnected : IllegalStateException("Seerr is not connected")

data class SearchResults(val items: List<DiscoverItem>, val people: List<PersonResult>)
