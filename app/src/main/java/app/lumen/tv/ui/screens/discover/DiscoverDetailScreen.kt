package app.lumen.tv.ui.screens.discover

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.Availability
import app.lumen.tv.data.model.DiscoverDetail
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.Trailer
import app.lumen.tv.ui.components.HeroBackdrop
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.RatingBadges
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.components.availabilityColor
import app.lumen.tv.ui.components.availabilityLabel
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.ui.components.AvailabilityBadge
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.CastEntry
import app.lumen.tv.ui.components.CastRow
import app.lumen.tv.ui.components.CollectionShelf
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.ShelfRow
import app.lumen.tv.ui.components.SheetHeading
import app.lumen.tv.ui.components.SheetRow
import app.lumen.tv.ui.components.TitleLogo
import app.lumen.tv.ui.components.rememberLogo
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import app.lumen.tv.ui.theme.LumenLayout
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

@Composable
fun DiscoverDetailScreen(
    kind: DiscoverKind,
    tmdbId: Int,
    onWatch: (UUID) -> Unit,
    onTrailer: (Trailer, String, String?) -> Unit,
    onOpenPerson: (Int) -> Unit,
    onOpenDiscover: (DiscoverItem) -> Unit,
    onOpenFilm: (DiscoverItem) -> Unit,
    onOpenCollection: (Int, Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: DiscoverDetailViewModel = koinViewModel(key = "discover-$kind-$tmdbId") { parametersOf(kind, tmdbId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.detail
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = state.sheetOpen) { viewModel.dismissSheet() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key.nativeKeyCode != KeyEvent.KEYCODE_BACK || !state.sheetOpen) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) viewModel.dismissSheet()
                true
            },
    ) {
        val headerMin = maxHeight * LumenLayout.DetailHeaderMinFraction
        HeroBackdrop(url = detail?.item?.backdropUrl)
        when {
            state.error -> Text(
                stringResource(R.string.detail_error),
                style = MaterialTheme.typography.titleLarge,
                color = LumenColors.OnSurface,
                modifier = Modifier.align(Alignment.Center),
            )
            detail == null -> Spinner(Modifier.align(Alignment.Center))
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = LumenSpacing.XXL),
                verticalArrangement = Arrangement.spacedBy(LumenSpacing.L),
            ) {
                item(key = "header") {
                    Box(
                        Modifier
                            .heightIn(min = headerMin)
                            .fillMaxWidth()
                            .padding(top = LumenSpacing.XXL)
                            .onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Header(
                            detail = detail,
                            logoUrl = rememberLogo(detail.item),
                            ratings = state.ratings,
                            onTrailer = { onTrailer(it, detail.item.title, detail.item.backdropUrl) },
                            requesting = state.requesting,
                            outcome = state.outcome,
                            pickingSeasons = state.sheetOpen,
                            onRequest = viewModel::onRequest,
                            onWatch = onWatch,
                        )
                    }
                }
                if (detail.cast.isNotEmpty()) {
                    item(key = "cast") {
                        CastRow(
                            people = detail.cast.map { CastEntry(it.tmdbId.toString(), it.name, it.role, it.imageUrl) },
                            onOpen = { entry -> entry.key.toIntOrNull()?.let(onOpenPerson) },
                        )
                    }
                }
                state.collection?.let { collection ->
                    item(key = "collection") {
                        CollectionShelf(
                            collection = collection,
                            currentTmdbId = detail.item.tmdbId,
                            onOpenFilm = onOpenFilm,
                            onOpenCollection = { request -> onOpenCollection(collection.ref.tmdbId, request) },
                        )
                    }
                }
                if (state.similar.isNotEmpty()) {
                    item(key = "similar") {
                        val entry = remember { FocusRequester() }
                        ShelfRow(title = stringResource(R.string.detail_more_like_this), entry = entry) {
                            items(state.similar, key = { "${it.kind}-${it.tmdbId}" }) { item ->
                                LumenCard(
                                    title = item.title,
                                    imageUrl = item.posterUrl,
                                    shape = CardShape.Poster,
                                    onClick = { onOpenDiscover(item) },
                                    badge = { AvailabilityBadge(item.availability) },
                                    modifier = if (item == state.similar.first()) Modifier.focusRequester(entry) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state.sheetOpen && detail != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            if (detail != null) RequestSheet(detail = detail, state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun Header(
    detail: DiscoverDetail,
    logoUrl: String?,
    ratings: ExternalRatings?,
    onTrailer: (Trailer) -> Unit,
    requesting: Boolean,
    outcome: RequestOutcome?,
    pickingSeasons: Boolean,
    onRequest: () -> Unit,
    onWatch: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = detail.item
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(item.tmdbId, item.availability, requesting, pickingSeasons) {
        if (!pickingSeasons && !requesting) runCatching { primaryFocus.requestFocus() }
    }

    Column(modifier.widthIn(max = 680.dp).padding(horizontal = LumenSpacing.SafeHorizontal)) {
        TitleLogo(
            title = item.title,
            logoUrl = logoUrl,
            style = MaterialTheme.typography.displayLarge,
            maxHeight = 110.dp,
            maxWidth = 420.dp,
        )
        detail.tagline?.let {
            Spacer(Modifier.height(LumenSpacing.S))
            Text(it, style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurfaceMuted)
        }
        Spacer(Modifier.height(LumenSpacing.M))
        val runtime = detail.runtimeMinutes?.takeIf { it > 0 }?.let { stringResource(R.string.runtime_minutes, it) }
        val meta = listOfNotNull(
            discoverMetadata(item, includeRating = ratings == null),
            detail.certification,
            runtime,
            detail.genres.take(2).joinToString(", ").ifEmpty { null },
        )
        Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
        ratings?.let {
            Spacer(Modifier.height(LumenSpacing.S))
            RatingBadges(it)
        }
        item.overview?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(LumenSpacing.M))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(LumenSpacing.L))
        Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M), verticalAlignment = Alignment.CenterVertically) {
            val watchable = item.jellyfinId != null &&
                (item.availability == Availability.Available || item.availability == Availability.PartiallyAvailable)
            val requestable = item.availability == Availability.None ||
                (item.kind == DiscoverKind.Series && detail.seasons.any { it.availability == Availability.None })
            val partlyRequested = item.availability != Availability.None || detail.seasons.any { it.availability != Availability.None }
            if (watchable) {
                PillButton(
                    text = stringResource(R.string.discover_watch_now),
                    onClick = { item.jellyfinId?.let(onWatch) },
                    style = PillStyle.Primary,
                    modifier = Modifier.focusRequester(primaryFocus),
                    leading = { Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(20.dp)) },
                )
            }
            if (requestable) {
                PillButton(
                    text = stringResource(
                        when {
                            requesting -> R.string.discover_requesting
                            partlyRequested -> R.string.discover_request_more
                            else -> R.string.discover_request
                        },
                    ),
                    onClick = onRequest,
                    style = if (watchable) PillStyle.Secondary else PillStyle.Primary,
                    enabled = !requesting,
                    modifier = if (watchable) Modifier else Modifier.focusRequester(primaryFocus),
                    leading = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp)) },
                )
            }
            if (!watchable && !requestable) {
                PillButton(
                    text = availabilityLabel(item.availability) ?: stringResource(R.string.availability_requested),
                    onClick = {},
                    modifier = Modifier.focusRequester(primaryFocus),
                )
            }
            detail.trailer?.let { trailer ->
                PillButton(
                        text = stringResource(R.string.trailer_button),
                        onClick = { onTrailer(trailer) },
                        leading = { Icon(painterResource(R.drawable.ic_movie), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    )
            }
        }
        outcome?.let {
            Spacer(Modifier.height(LumenSpacing.M))
            Text(
                stringResource(if (it == RequestOutcome.Sent) R.string.discover_request_sent else R.string.discover_request_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it == RequestOutcome.Sent) LumenColors.Available else LumenColors.Error,
            )
        }
        detail.studio?.let {
            Spacer(Modifier.height(LumenSpacing.M))
            Text(it, style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceFaint)
        }
    }
}

@Composable
private fun RequestSheet(detail: DiscoverDetail, state: DiscoverDetailState, viewModel: DiscoverDetailViewModel) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
        withFrameNanos { }
        listState.scrollToItem(0)
    }
    val isSeries = detail.item.kind == DiscoverKind.Series
    val options = state.options
    val showVersion = options?.canCustomise == true && state.has4kChoice
    val showServer = options?.canCustomise == true && state.serversForVersion.size > 1
    val profiles = if (options?.canCustomise == true) state.server?.profiles.orEmpty() else emptyList()
    val requestable = detail.seasons.filter { it.availability == Availability.None }
    val firstKey = remember(options, isSeries) {
        when {
            profiles.isNotEmpty() -> "profile-${state.profileId ?: profiles.first().id}"
            showVersion -> "version-standard"
            showServer -> "server-${state.serversForVersion.first().id}"
            isSeries && requestable.size > 1 -> "seasons-all"
            isSeries && requestable.isNotEmpty() -> "season-${requestable.first().number}"
            else -> "confirm"
        }
    }
    fun focusFor(key: String): Modifier = if (key == firstKey) Modifier.focusRequester(firstFocus) else Modifier

    Column(
        Modifier
            .fillMaxHeight()
            .width(420.dp)
            .padding(LumenSpacing.L)
            .background(LumenColors.Glass.copy(alpha = 0.94f), LumenShapes.Panel)
            .border(1.dp, LumenColors.GlassStroke, LumenShapes.Panel)
            .padding(LumenSpacing.M),
    ) {
        Text(
            stringResource(R.string.discover_request_options),
            style = MaterialTheme.typography.titleLarge,
            color = LumenColors.OnSurface,
            modifier = Modifier.padding(start = LumenSpacing.M, bottom = LumenSpacing.S),
        )
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(vertical = LumenSpacing.S),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showVersion) {
                item { SheetHeading(stringResource(R.string.discover_version)) }
                item {
                    SheetRow(
                        label = stringResource(R.string.discover_version_standard),
                        checked = !state.want4k,
                        onClick = { viewModel.selectVersion(false) },
                        modifier = focusFor("version-standard"),
                    )
                }
                item { SheetRow(label = stringResource(R.string.discover_version_4k), checked = state.want4k, onClick = { viewModel.selectVersion(true) }) }
            }
            if (showServer) {
                item { SheetHeading(stringResource(R.string.discover_server)) }
                items(state.serversForVersion, key = { "server-${it.id}" }) { server ->
                    SheetRow(
                        label = server.name,
                        checked = server.id == state.serverId,
                        onClick = { viewModel.selectServer(server.id) },
                        modifier = focusFor("server-${server.id}"),
                    )
                }
            }
            if (profiles.isNotEmpty()) {
                item { SheetHeading(stringResource(R.string.discover_quality)) }
                items(profiles, key = { "profile-${it.id}" }) { profile ->
                    SheetRow(
                        label = profile.name,
                        detail = if (profile.id == state.server?.defaultProfileId) stringResource(R.string.discover_default) else null,
                        checked = profile.id == state.profileId,
                        onClick = { viewModel.selectProfile(profile.id) },
                        modifier = focusFor("profile-${profile.id}"),
                    )
                }
            }
            if (isSeries) {
                item { SheetHeading(stringResource(R.string.discover_seasons)) }
                if (requestable.size > 1) {
                    item {
                        SheetRow(
                            label = stringResource(R.string.discover_all_seasons),
                            checked = state.selectedSeasons.containsAll(requestable.map { it.number }),
                            onClick = viewModel::toggleAllSeasons,
                            modifier = focusFor("seasons-all"),
                        )
                    }
                }
                items(detail.seasons, key = { "season-${it.number}" }) { season ->
                    val enabled = season.availability == Availability.None
                    SheetRow(
                        label = season.name.ifBlank { stringResource(R.string.discover_season_n, season.number) },
                        detail = availabilityLabel(season.availability)
                            ?: season.episodes?.let { pluralStringResource(R.plurals.episode_count, it, it) },
                        detailColor = if (enabled) LumenColors.OnSurfaceFaint else availabilityColor(season.availability),
                        checked = season.number in state.selectedSeasons,
                        enabled = enabled,
                        onClick = { viewModel.toggleSeason(season.number) },
                        modifier = focusFor("season-${season.number}"),
                    )
                }
            }
        }
        PillButton(
            text = when {
                state.requesting -> stringResource(R.string.discover_requesting)
                isSeries -> pluralStringResource(R.plurals.discover_request_seasons, state.selectedSeasons.size, state.selectedSeasons.size)
                else -> stringResource(R.string.discover_request)
            },
            onClick = viewModel::confirm,
            style = PillStyle.Primary,
            enabled = !state.requesting && (!isSeries || state.selectedSeasons.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().then(focusFor("confirm")),
        )
    }
}
