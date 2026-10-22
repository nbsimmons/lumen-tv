package app.lumen.tv.ui.screens.detail

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.ItemDetail
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.data.model.Person
import app.lumen.tv.data.model.Trailer
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.CastEntry
import app.lumen.tv.ui.components.CastRow
import app.lumen.tv.ui.components.CollectionShelf
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.ui.components.HeroBackdrop
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.RatingBadges
import app.lumen.tv.ui.components.RoundIconButton
import app.lumen.tv.ui.components.ShelfRow
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.components.metadataLine
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import app.lumen.tv.ui.theme.LumenLayout
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

private const val TICKS_PER_MINUTE = 600_000_000L

@Composable
fun DetailScreen(
    itemId: UUID,
    focusEpisodeId: UUID?,
    onPlay: (MediaItem, Long) -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onTrailer: (Trailer, String, String?) -> Unit,
    onOpenPerson: (UUID) -> Unit,
    onOpenFilm: (DiscoverItem) -> Unit,
    onOpenCollection: (Int, Boolean) -> Unit,
    viewModel: DetailViewModel = koinViewModel(key = "detail-$itemId") { parametersOf(itemId, focusEpisodeId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.detail
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headerMin = maxHeight * LumenLayout.DetailHeaderMinFraction
        HeroBackdrop(url = detail?.item?.backdropUrl)
        if (state.error) {
            Text(
                stringResource(R.string.detail_error),
                style = MaterialTheme.typography.titleLarge,
                color = LumenColors.OnSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (detail == null) {
            if (!state.error) Spinner(Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = LumenSpacing.XXL),
            verticalArrangement = Arrangement.spacedBy(LumenSpacing.L),
        ) {
            item(key = "header") {
                Header(
                    detail = detail,
                    ratings = state.ratings,
                    trailer = state.trailer,
                    onTrailer = { onTrailer(it, detail.item.title, detail.item.backdropUrl) },
                    nextUp = state.nextUp,
                    onPlay = onPlay,
                    onToggleWatched = viewModel::togglePlayed,
                    onToggleFavorite = viewModel::toggleFavorite,
                    modifier = Modifier
                        .heightIn(min = headerMin)
                        .onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } },
                )
            }
            if (detail.item.kind == MediaKind.Series && state.seasons.isNotEmpty()) {
                item(key = "episodes") {
                    Episodes(state = state, onSelectSeason = viewModel::selectSeason, onPlay = onPlay)
                }
            }
            if (detail.people.isNotEmpty()) {
                item(key = "cast") {
                    CastRow(
                        people = detail.people.map { CastEntry(it.id.toString() + it.role, it.name, it.role, it.imageUrl) },
                        onOpen = { entry -> detail.people.firstOrNull { it.id.toString() + it.role == entry.key }?.let { onOpenPerson(it.id) } },
                    )
                }
            }
            state.collection?.let { collection ->
                item(key = "collection") {
                    CollectionShelf(
                        collection = collection,
                        currentTmdbId = detail.tmdbId,
                        onOpenFilm = onOpenFilm,
                        onOpenCollection = { request -> onOpenCollection(collection.ref.tmdbId, request) },
                    )
                }
            }
            if (state.similar.isNotEmpty()) {
                item(key = "similar") {
                    val entry = remember { FocusRequester() }
                    ShelfRow(title = stringResource(R.string.detail_more_like_this), entry = entry) {
                        items(state.similar, key = { it.id }) { item ->
                            LumenCard(
                                title = item.title,
                                imageUrl = item.posterUrl,
                                shape = CardShape.Poster,
                                onClick = { onOpenItem(item) },
                                watched = item.played,
                                modifier = if (item == state.similar.first()) Modifier.focusRequester(entry) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    detail: ItemDetail,
    ratings: ExternalRatings?,
    trailer: Trailer?,
    onTrailer: (Trailer) -> Unit,
    nextUp: MediaItem?,
    onPlay: (MediaItem, Long) -> Unit,
    onToggleWatched: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = detail.item
    val playTarget = if (item.kind == MediaKind.Series) nextUp else item
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(playTarget?.id) { runCatching { playFocus.requestFocus() } }

    Box(
        modifier.fillMaxWidth().padding(start = LumenSpacing.SafeHorizontal, end = LumenSpacing.SafeHorizontal, top = LumenSpacing.XXL),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(Modifier.widthIn(max = 620.dp)) {
            if (item.logoUrl != null) {
                AsyncImage(
                    model = item.logoUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier.heightIn(max = 110.dp).widthIn(max = 420.dp),
                )
            } else {
                Text(item.title, style = MaterialTheme.typography.displayLarge, color = LumenColors.OnSurface, maxLines = 2)
            }
            Spacer(Modifier.height(LumenSpacing.M))
            metadataLine(item, includeRating = ratings == null)?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
            }
            ratings?.let {
                Spacer(Modifier.height(LumenSpacing.S))
                RatingBadges(it)
            }
            item.overview?.let {
                Spacer(Modifier.height(LumenSpacing.M))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LumenColors.OnSurfaceMuted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(LumenSpacing.L))
            Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M), verticalAlignment = Alignment.CenterVertically) {
                if (playTarget != null) {
                    val resuming = playTarget.resumeTicks > 0
                    PillButton(
                        text = playLabel(item, playTarget, resuming),
                        onClick = { onPlay(playTarget, playTarget.resumeTicks) },
                        style = PillStyle.Primary,
                        modifier = Modifier.focusRequester(playFocus),
                        leading = { Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    )
                    if (resuming) {
                        RoundIconButton(
                            icon = R.drawable.ic_restart,
                            contentDescription = stringResource(R.string.detail_restart),
                            onClick = { onPlay(playTarget, 0L) },
                        )
                    }
                }
                if (trailer != null) {
                    PillButton(
                    text = stringResource(R.string.trailer_button),
                    onClick = { onTrailer(trailer) },
                    leading = { Icon(painterResource(R.drawable.ic_movie), contentDescription = null, modifier = Modifier.size(20.dp)) },
                )
                }
                RoundIconButton(
                    icon = R.drawable.ic_check,
                    contentDescription = stringResource(if (item.played) R.string.detail_mark_unwatched else R.string.detail_mark_watched),
                    onClick = onToggleWatched,
                    active = item.played,
                )
                RoundIconButton(
                    icon = if (item.favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                    contentDescription = stringResource(if (item.favorite) R.string.detail_unfavorite else R.string.detail_favorite),
                    onClick = onToggleFavorite,
                    active = item.favorite,
                )
            }
            playTarget?.takeIf { it.resumeTicks > 0 }?.lastPlayed?.let { lastPlayed ->
                Spacer(Modifier.height(LumenSpacing.M))
                Text(
                    stringResource(R.string.detail_resume_hint, formatPosition(playTarget.resumeTicks / TICKS_PER_MS), lastWatched(lastPlayed)),
                    style = MaterialTheme.typography.labelMedium,
                    color = LumenColors.OnSurfaceMuted,
                )
            }
            detail.studio?.let {
                Spacer(Modifier.height(LumenSpacing.M))
                Text(it, style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceFaint)
            }
        }
    }
}

@Composable
private fun lastWatched(epochMs: Long): String {
    val then = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(then, java.time.LocalDate.now()).toInt()
    return when {
        days <= 0 -> stringResource(R.string.detail_last_today)
        days == 1 -> stringResource(R.string.detail_last_yesterday)
        days < 7 -> then.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current)[0] ?: java.util.Locale.ENGLISH)
        else -> then.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
    }
}

private fun formatPosition(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private const val TICKS_PER_MS = 10_000L

@Composable
private fun playLabel(item: MediaItem, target: MediaItem, resuming: Boolean): String {
    val episodeCode = if (item.kind == MediaKind.Series) {
        listOfNotNull(target.parentIndexNumber?.let { "S$it" }, target.indexNumber?.let { "E$it" }).joinToString("")
    } else {
        ""
    }
    val base = when {
        episodeCode.isNotEmpty() && resuming -> stringResource(R.string.detail_resume_episode, episodeCode)
        episodeCode.isNotEmpty() -> stringResource(R.string.detail_play_episode, episodeCode)
        resuming -> stringResource(R.string.detail_resume)
        else -> stringResource(R.string.detail_play)
    }
    val total = target.runtimeMinutes ?: return base
    if (!resuming) return base
    val left = (total - (target.resumeTicks / TICKS_PER_MINUTE).toInt()).coerceAtLeast(1)
    return base + "  ·  " + stringResource(R.string.detail_time_left, left)
}

@Composable
private fun Episodes(
    state: DetailState,
    onSelectSeason: (UUID) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
) {
    val rowState = remember(state.selectedSeason) { LazyListState() }
    val restore = remember { FocusRequester() }
    val focusIndex = state.episodes.indexOfFirst { it.id == state.focusEpisode }.takeIf { it >= 0 } ?: 0
    LaunchedEffect(state.episodes) {
        if (state.episodes.isNotEmpty()) rowState.scrollToItem(focusIndex)
    }

    val selectedSeasonFocus = remember { FocusRequester() }
    Column {
        LazyRow(
            modifier = Modifier
                .focusProperties { onEnter = { selectedSeasonFocus.requestFocus() } }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.S),
            horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S),
        ) {
            items(state.seasons, key = { it.id }) { season ->
                val selected = season.id == state.selectedSeason
                Surface(
                    onClick = { onSelectSeason(season.id) },
                    modifier = Modifier
                        .then(if (selected) Modifier.focusRequester(selectedSeasonFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) onSelectSeason(season.id) },
                    shape = ClickableSurfaceDefaults.shape(LumenShapes.Pill),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) LumenColors.SurfaceRaised else LumenColors.Glass.copy(alpha = 0f),
                        contentColor = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
                        focusedContainerColor = LumenColors.FocusFill,
                        focusedContentColor = LumenColors.OnFocusFill,
                    ),
                ) {
                    Text(
                        season.name,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }
        ShelfRow(title = null, state = rowState, restoreFocus = restore) {
            itemsIndexed(state.episodes, key = { _, it -> it.id }) { index, episode ->
                LumenCard(
                    title = listOfNotNull(episode.indexNumber?.let { "$it." }, episode.name).joinToString(" "),
                    subtitle = episodeSubtitle(episode),
                    imageUrl = episode.thumbUrl,
                    shape = CardShape.Landscape,
                    progress = episode.progress,
                    watched = episode.played,
                    onClick = { onPlay(episode, episode.resumeTicks) },
                    modifier = if (index == focusIndex) Modifier.focusRequester(restore) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun episodeSubtitle(episode: MediaItem): String? {
    val runtime = episode.runtimeMinutes?.let { stringResource(R.string.runtime_minutes, it) }
    val watched = if (episode.played) stringResource(R.string.detail_watched) else null
    return listOfNotNull(runtime, watched).joinToString("  ·  ").ifEmpty { null }
}
