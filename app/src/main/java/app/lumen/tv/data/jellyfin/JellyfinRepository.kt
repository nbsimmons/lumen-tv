package app.lumen.tv.data.jellyfin

import app.lumen.tv.data.model.HOME_ARRIVALS
import app.lumen.tv.data.model.HOME_WATCHLIST
import app.lumen.tv.data.model.HomeSection
import app.lumen.tv.data.model.ItemDetail
import app.lumen.tv.data.model.LibraryFilter
import app.lumen.tv.data.model.LibraryPage
import app.lumen.tv.data.model.WatchFilter
import app.lumen.tv.data.model.LibraryView
import app.lumen.tv.data.model.LibrarySort
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.data.model.Person
import app.lumen.tv.data.model.PersonResult
import app.lumen.tv.data.model.Season
import app.lumen.tv.data.model.Shelf
import app.lumen.tv.data.model.Trailer
import app.lumen.tv.data.model.ShelfKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.extensions.genreApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.personApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import java.time.LocalDateTime
import java.util.UUID

class JellyfinRepository(private val client: JellyfinClient, private val homeCache: HomeCache) {
    private val userId: UUID get() = checkNotNull(client.session.value).userId

    private val recent = object : LinkedHashMap<UUID, MediaItem>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, MediaItem>?): Boolean = size > RECENT_ITEMS
    }

    fun peek(id: UUID): MediaItem? = synchronized(recent) { recent[id] }

    private fun remember(item: MediaItem) {
        synchronized(recent) { recent[item.id] = item }
    }

    suspend fun cachedHome(): List<Shelf>? =
        homeCache.read(userId)?.shelves?.takeIf { it.isNotEmpty() }?.also { shelves -> shelves.forEach { it.items.forEach(::remember) } }

    suspend fun homeSections(): List<HomeSection> =
        listOf(
            HomeSection(HOME_ARRIVALS, ShelfKind.Arrivals, null),
            HomeSection(KEY_RESUME, ShelfKind.ContinueWatching, null),
            HomeSection(KEY_NEXT_UP, ShelfKind.NextUp, null),
            HomeSection(HOME_WATCHLIST, ShelfKind.Watchlist, null),
        ) +
            libraryViews().flatMap { view ->
                listOfNotNull(
                    HomeSection("latest-${view.id}", ShelfKind.RecentlyAdded, view.name),
                    HomeSection("new-${view.id}", ShelfKind.NewReleases, view.name).takeIf { view.movies },
                )
            }

    suspend fun homeShelves(hidden: Set<String>): List<Shelf> = withContext(Dispatchers.Default) {
        coroutineScope {
            val user = userId
            val resume = async { if (KEY_RESUME in hidden) null else resumeShelf() }
            val nextUp = async { if (KEY_NEXT_UP in hidden) null else nextUpShelf() }
            val known = knownViews(user)
            if (known != null) launch { runCatching { fetchViews(user) } }
            val views = known ?: fetchViews(user)
            val libraries = views.flatMap { view ->
                listOfNotNull(
                    async { latestShelf(view) }.takeIf { "latest-${view.id}" !in hidden },
                    async { newReleasesShelf(view) }.takeIf { view.movies && "new-${view.id}" !in hidden },
                )
            }
            val shelves = (listOfNotNull(resume.await(), nextUp.await()) + libraries.awaitAll()).filter { it.items.isNotEmpty() }
            homeCache.update(user) { it.copy(shelves = shelves) }
            shelves
        }
    }

    @Volatile
    private var cachedViews: Pair<UUID, List<LibraryView>>? = null

    private suspend fun knownViews(user: UUID): List<LibraryView>? =
        cachedViews?.takeIf { it.first == user }?.second
            ?: homeCache.read(user)?.views?.takeIf { it.isNotEmpty() }?.also { cachedViews = user to it }

    private suspend fun libraryViews(): List<LibraryView> = knownViews(userId) ?: fetchViews(userId)

    private suspend fun fetchViews(user: UUID): List<LibraryView> = client.io {
        client.api.userViewApi.getUserViews(userId = user).content.items
    }.filter { it.collectionType == CollectionType.MOVIES || it.collectionType == CollectionType.TVSHOWS }
        .map { LibraryView(it.id, it.name, it.collectionType == CollectionType.MOVIES) }
        .also { views ->
            cachedViews = user to views
            homeCache.update(user) { it.copy(views = views) }
        }

    private suspend fun resumeShelf(): Shelf {
        val items = client.io {
            client.api.libraryApi.getResumeItems(
                GetResumeItemsRequest(
                    userId = userId,
                    limit = SHELF_LIMIT,
                    fields = FIELDS,
                    mediaTypes = listOf(MediaType.VIDEO),
                    enableUserData = true,
                    imageTypeLimit = 1,
                    enableImageTypes = IMAGE_TYPES,
                ),
            ).content.items
        }
        return Shelf("resume", ShelfKind.ContinueWatching, null, items.map(::toMediaItem))
    }

    private suspend fun nextUpShelf(): Shelf {
        val items = client.io {
            client.api.showApi.getNextUp(
                GetNextUpRequest(
                    userId = userId,
                    limit = SHELF_LIMIT,
                    fields = FIELDS,
                    enableUserData = true,
                    imageTypeLimit = 1,
                    enableImageTypes = IMAGE_TYPES,
                    enableResumable = false,
                    enableRewatching = false,
                ),
            ).content.items
        }
        return Shelf("nextup", ShelfKind.NextUp, null, items.map(::toMediaItem))
    }

    private suspend fun latestShelf(view: LibraryView): Shelf {
        val items = runCatching {
            client.io {
                client.api.libraryApi.getLatestMedia(
                    GetLatestMediaRequest(
                        userId = userId,
                        parentId = view.id,
                        fields = FIELDS,
                        enableUserData = true,
                        imageTypeLimit = 1,
                        enableImageTypes = IMAGE_TYPES,
                        limit = SHELF_LIMIT,
                        groupItems = true,
                    ),
                ).content
            }
        }.getOrDefault(emptyList())
        return Shelf("latest-${view.id}", ShelfKind.RecentlyAdded, view.name, items.map(::toMediaItem))
    }

    private suspend fun newReleasesShelf(view: LibraryView): Shelf {
        val items = runCatching {
            client.io {
                client.api.libraryApi.getItems(
                    GetItemsRequest(
                        userId = userId,
                        parentId = view.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        maxPremiereDate = LocalDateTime.now(),
                        sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                        fields = FIELDS,
                        enableUserData = true,
                        imageTypeLimit = 1,
                        enableImageTypes = IMAGE_TYPES,
                        limit = SHELF_LIMIT,
                    ),
                ).content.items
            }
        }.getOrDefault(emptyList())
        return Shelf("new-${view.id}", ShelfKind.NewReleases, view.name, items.map(::toMediaItem))
    }

    fun toMediaItem(dto: BaseItemDto): MediaItem = buildMediaItem(dto).also(::remember)

    private fun buildMediaItem(dto: BaseItemDto): MediaItem {
        val kind = when (dto.type) {
            BaseItemKind.MOVIE -> MediaKind.Movie
            BaseItemKind.SERIES -> MediaKind.Series
            BaseItemKind.SEASON -> MediaKind.Season
            BaseItemKind.EPISODE -> MediaKind.Episode
            else -> MediaKind.Other
        }
        val tags = dto.imageTags.orEmpty()
        val isEpisode = kind == MediaKind.Episode

        val poster = when {
            isEpisode && dto.seriesId != null && dto.seriesPrimaryImageTag != null ->
                image(dto.seriesId!!, ImageType.PRIMARY, dto.seriesPrimaryImageTag, POSTER_WIDTH)
            tags[ImageType.PRIMARY] != null -> image(dto.id, ImageType.PRIMARY, tags[ImageType.PRIMARY], POSTER_WIDTH)
            else -> null
        }

        val backdrop = when {
            !dto.backdropImageTags.isNullOrEmpty() -> image(dto.id, ImageType.BACKDROP, dto.backdropImageTags!!.first(), BACKDROP_WIDTH)
            dto.parentBackdropItemId != null && !dto.parentBackdropImageTags.isNullOrEmpty() ->
                image(dto.parentBackdropItemId!!, ImageType.BACKDROP, dto.parentBackdropImageTags!!.first(), BACKDROP_WIDTH)
            else -> null
        }

        val thumb = when {
            isEpisode && tags[ImageType.PRIMARY] != null -> image(dto.id, ImageType.PRIMARY, tags[ImageType.PRIMARY], THUMB_WIDTH)
            tags[ImageType.THUMB] != null -> image(dto.id, ImageType.THUMB, tags[ImageType.THUMB], THUMB_WIDTH)
            dto.parentThumbItemId != null && dto.parentThumbImageTag != null ->
                image(dto.parentThumbItemId!!, ImageType.THUMB, dto.parentThumbImageTag, THUMB_WIDTH)
            !dto.backdropImageTags.isNullOrEmpty() -> image(dto.id, ImageType.BACKDROP, dto.backdropImageTags!!.first(), THUMB_WIDTH)
            dto.parentBackdropItemId != null && !dto.parentBackdropImageTags.isNullOrEmpty() ->
                image(dto.parentBackdropItemId!!, ImageType.BACKDROP, dto.parentBackdropImageTags!!.first(), THUMB_WIDTH)
            else -> null
        }

        val logo = when {
            tags[ImageType.LOGO] != null -> image(dto.id, ImageType.LOGO, tags[ImageType.LOGO], LOGO_WIDTH)
            dto.parentLogoItemId != null && dto.parentLogoImageTag != null ->
                image(dto.parentLogoItemId!!, ImageType.LOGO, dto.parentLogoImageTag, LOGO_WIDTH)
            else -> null
        }

        val episodeLabel = if (isEpisode) {
            listOfNotNull(
                dto.parentIndexNumber?.let { "S$it" },
                dto.indexNumber?.let { "E$it" },
            ).joinToString("").ifEmpty { null }
        } else {
            null
        }

        return MediaItem(
            id = dto.id,
            kind = kind,
            title = if (isEpisode) dto.seriesName ?: dto.name.orEmpty() else dto.name.orEmpty(),
            name = dto.name.orEmpty(),
            subtitle = if (isEpisode) listOfNotNull(episodeLabel, dto.name).joinToString(" · ") else dto.productionYear?.toString(),
            overview = dto.overview,
            year = dto.productionYear,
            officialRating = dto.officialRating,
            communityRating = dto.communityRating,
            runtimeMinutes = dto.runTimeTicks?.let { (it / TICKS_PER_MINUTE).toInt() },
            genres = dto.genres.orEmpty(),
            progress = dto.userData?.playedPercentage?.let { (it / 100.0).toFloat() },
            resumeTicks = dto.userData?.playbackPositionTicks ?: 0L,
            played = dto.userData?.played == true,
            lastPlayed = dto.userData?.lastPlayedDate?.toInstant(java.time.ZoneOffset.UTC)?.toEpochMilli(),
            favorite = dto.userData?.isFavorite == true,
            posterUrl = poster,
            thumbUrl = thumb,
            backdropUrl = backdrop,
            logoUrl = logo,
            seriesId = dto.seriesId,
            seasonId = dto.seasonId,
            indexNumber = dto.indexNumber,
            parentIndexNumber = dto.parentIndexNumber,
            unplayedCount = dto.userData?.unplayedItemCount,
            tmdbId = dto.providerIds?.entries?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }?.value?.toIntOrNull(),
        )
    }

    suspend fun favourites(): List<MediaItem> = client.io {
        client.api.libraryApi.getItems(
            GetItemsRequest(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                isFavorite = true,
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
                fields = FIELDS,
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = IMAGE_TYPES,
                limit = SHELF_LIMIT,
            ),
        ).content.items
    }.map(::toMediaItem)

    suspend fun byStudio(name: String): List<MediaItem> = client.io {
        client.api.libraryApi.getItems(
            GetItemsRequest(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                studios = listOf(name),
                sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                sortOrder = listOf(SortOrder.DESCENDING),
                fields = FIELDS,
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = IMAGE_TYPES,
                limit = SHELF_LIMIT,
            ),
        ).content.items
    }.map(::toMediaItem)

    suspend fun genres(kind: MediaKind): List<String> = client.io {
        client.api.genreApi.getGenres(
            GetGenresRequest(
                userId = userId,
                includeItemTypes = listOf(if (kind == MediaKind.Series) BaseItemKind.SERIES else BaseItemKind.MOVIE),
                sortBy = listOf(ItemSortBy.SORT_NAME),
            ),
        ).content.items
    }.mapNotNull { it.name?.takeIf(String::isNotBlank) }

    suspend fun allMovies(): List<MediaItem> = client.io {
        client.api.libraryApi.getItems(
            GetItemsRequest(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                recursive = true,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                fields = FIELDS,
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = IMAGE_TYPES,
            ),
        ).content.items
    }.map(::toMediaItem)

    suspend fun library(kind: MediaKind, sort: LibrarySort, start: Int, limit: Int, filter: LibraryFilter = LibraryFilter()): LibraryPage {
        val (sortBy, order) = when (sort) {
            LibrarySort.Title -> ItemSortBy.SORT_NAME to SortOrder.ASCENDING
            LibrarySort.DateAdded -> ItemSortBy.DATE_CREATED to SortOrder.DESCENDING
            LibrarySort.Year -> ItemSortBy.PRODUCTION_YEAR to SortOrder.DESCENDING
            LibrarySort.Rating -> ItemSortBy.COMMUNITY_RATING to SortOrder.DESCENDING
        }
        val result = client.io {
            client.api.libraryApi.getItems(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(if (kind == MediaKind.Series) BaseItemKind.SERIES else BaseItemKind.MOVIE),
                    recursive = true,
                    sortBy = listOf(sortBy, ItemSortBy.SORT_NAME),
                    sortOrder = listOf(order, SortOrder.ASCENDING),
                    fields = FIELDS,
                    enableUserData = true,
                    imageTypeLimit = 1,
                    enableImageTypes = IMAGE_TYPES,
                    startIndex = start,
                    limit = limit,
                    enableTotalRecordCount = true,
                    genres = filter.genre?.let(::listOf),
                    isPlayed = when (filter.watched) {
                        WatchFilter.All -> null
                        WatchFilter.Unwatched -> false
                        WatchFilter.Watched -> true
                    },
                    years = filter.decade?.let { (it until it + 10).toList() },
                    minCommunityRating = filter.minRating?.toDouble(),
                    is4k = if (filter.uhdOnly) true else null,
                ),
            ).content
        }
        return LibraryPage(result.items.map(::toMediaItem), result.totalRecordCount)
    }

    suspend fun search(query: String): List<MediaItem> = client.io {
        client.api.libraryApi.getItems(
            GetItemsRequest(
                userId = userId,
                searchTerm = query,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                fields = FIELDS,
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = IMAGE_TYPES,
                limit = SEARCH_LIMIT,
            ),
        ).content.items
    }.map(::toMediaItem)

    suspend fun person(id: UUID): PersonResult = toPerson(
        client.io { client.api.libraryApi.getItem(itemId = id, userId = userId).content },
    )

    suspend fun itemsWithPerson(id: UUID): List<MediaItem> = client.io {
        client.api.libraryApi.getItems(
            GetItemsRequest(
                userId = userId,
                personIds = listOf(id),
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                sortOrder = listOf(SortOrder.DESCENDING),
                fields = FIELDS,
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = IMAGE_TYPES,
            ),
        ).content.items
    }.map(::toMediaItem)

    suspend fun findPerson(name: String): PersonResult? = runCatching {
        client.io {
            client.api.personApi.getPersons(
                GetPersonsRequest(searchTerm = name, userId = userId, limit = PERSON_MATCHES, fields = listOf(ItemFields.PROVIDER_IDS)),
            ).content.items
        }.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let(::toPerson)
    }.getOrNull()

    private fun toPerson(dto: BaseItemDto) = PersonResult(
        jellyfinId = dto.id,
        name = dto.name.orEmpty(),
        imageUrl = dto.imageTags?.get(ImageType.PRIMARY)?.let { image(dto.id, ImageType.PRIMARY, it, PERSON_WIDTH) },
        tmdbId = dto.providerIds?.entries?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }?.value?.toIntOrNull(),
    )

    suspend fun tmdbId(id: UUID): Int? =
        client.io { client.api.libraryApi.getItem(itemId = id, userId = userId).content }
            .providerIds?.entries?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }?.value?.toIntOrNull()

    suspend fun detail(id: UUID): ItemDetail {
        val dto = client.io { client.api.libraryApi.getItem(itemId = id, userId = userId).content }
        val people = dto.people.orEmpty()
            .filter { it.type == PersonKind.ACTOR || it.type == PersonKind.DIRECTOR }
            .take(MAX_PEOPLE)
            .map { person ->
                Person(
                    id = person.id,
                    name = person.name.orEmpty(),
                    role = if (person.type == PersonKind.DIRECTOR) null else person.role,
                    imageUrl = person.primaryImageTag?.let { image(person.id, ImageType.PRIMARY, it, PERSON_WIDTH) },
                )
            }
        return ItemDetail(
            item = toMediaItem(dto),
            people = people,
            studio = dto.studios?.firstOrNull()?.name,
            tmdbId = dto.providerIds?.entries?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }?.value?.toIntOrNull(),
            criticRating = dto.criticRating?.toInt(),
            trailer = trailerFor(dto),
        )
    }

    private suspend fun trailerFor(dto: BaseItemDto): Trailer? {
        if ((dto.localTrailerCount ?: 0) > 0) {
            val local = runCatching {
                client.io { client.api.libraryApi.getLocalTrailers(itemId = dto.id, userId = userId).content.firstOrNull() }
            }.getOrNull()
            if (local != null) return Trailer.Local(local.id)
        }
        return dto.remoteTrailers.orEmpty().firstNotNullOfOrNull { Trailer.fromUrl(it.url) }
    }

    suspend fun similar(id: UUID): List<MediaItem> = runCatching {
        client.io {
            client.api.libraryApi.getSimilarItems(itemId = id, userId = userId, limit = SHELF_LIMIT, fields = FIELDS).content.items
        }.map(::toMediaItem)
    }.getOrDefault(emptyList())

    suspend fun seasons(seriesId: UUID): List<Season> = client.io {
        client.api.showApi.getSeasons(seriesId = seriesId, userId = userId).content.items
    }.map { Season(it.id, it.name.orEmpty(), it.indexNumber) }

    suspend fun episodes(seriesId: UUID, seasonId: UUID): List<MediaItem> = client.io {
        client.api.showApi.getEpisodes(
            seriesId = seriesId,
            userId = userId,
            seasonId = seasonId,
            fields = FIELDS,
            enableUserData = true,
            enableImageTypes = IMAGE_TYPES,
        ).content.items
    }.map(::toMediaItem)

    suspend fun nextUp(seriesId: UUID): MediaItem? = runCatching {
        client.io {
            client.api.showApi.getNextUp(
                GetNextUpRequest(userId = userId, seriesId = seriesId, limit = 1, fields = FIELDS, enableUserData = true),
            ).content.items.firstOrNull()
        }
    }.getOrNull()?.let(::toMediaItem)

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun setPlayed(id: UUID, played: Boolean) = client.io {
        if (played) client.api.userDataApi.markPlayedItem(itemId = id, userId = userId)
        else client.api.userDataApi.markUnplayedItem(itemId = id, userId = userId)
        _changes.tryEmit(Unit)
        Unit
    }

    suspend fun clearResume(id: UUID) = client.io {
        client.api.userDataApi.updateItemUserData(
            itemId = id,
            userId = userId,
            data = UpdateUserItemDataDto(playbackPositionTicks = 0),
        )
        Unit
    }

    suspend fun setFavorite(id: UUID, favorite: Boolean) = client.io {
        if (favorite) client.api.userDataApi.markFavoriteItem(itemId = id, userId = userId)
        else client.api.userDataApi.unmarkFavoriteItem(itemId = id, userId = userId)
        _changes.tryEmit(Unit)
        Unit
    }

    fun logoUrl(id: UUID): String = image(id, ImageType.LOGO, null, LOGO_WIDTH)

    private fun image(id: UUID, type: ImageType, tag: String?, width: Int): String =
        client.api.imageApi.getItemImageUrl(itemId = id, imageType = type, maxWidth = width, tag = tag, quality = IMAGE_QUALITY)

    private companion object {
        const val SHELF_LIMIT = 20
        const val RECENT_ITEMS = 400
        const val KEY_RESUME = "resume"
        const val KEY_NEXT_UP = "nextup"
        const val POSTER_WIDTH = 260
        const val THUMB_WIDTH = 480
        const val BACKDROP_WIDTH = 1280
        const val LOGO_WIDTH = 800
        const val PERSON_WIDTH = 240
        const val MAX_PEOPLE = 20
        const val SEARCH_LIMIT = 30
        const val PERSON_MATCHES = 5
        const val IMAGE_QUALITY = 80
        const val TICKS_PER_MINUTE = 600_000_000L
        val FIELDS = listOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.PROVIDER_IDS)
        val IMAGE_TYPES = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.THUMB, ImageType.LOGO)
    }
}
