package app.lumen.tv.ui.screens.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.DiscoverShelf
import app.lumen.tv.data.model.DiscoverShelfKind
import app.lumen.tv.ui.components.AvailabilityBadge
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.HeroBackdrop
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PinnedShelves
import app.lumen.tv.ui.components.ShelfSpec
import app.lumen.tv.ui.components.TitleLogo
import app.lumen.tv.ui.components.rememberLogo
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.components.availabilityLabel
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenLayout
import app.lumen.tv.ui.theme.LumenMotion
import app.lumen.tv.ui.theme.LumenSpacing
import org.koin.androidx.compose.koinViewModel

private val NavClearance = 84.dp
private val HeroHeight = 180.dp

@Composable
fun DiscoverScreen(
    onOpen: (DiscoverItem) -> Unit,
    onBrowse: (DiscoverKind) -> Unit,
    onRequests: () -> Unit,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoConnecting by viewModel.autoConnecting.collectAsStateWithLifecycle()

    when (val current = state) {
        DiscoverState.NotConnected -> if (autoConnecting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
        } else {
            SeerrConnectScreen()
        }
        DiscoverState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
        DiscoverState.Error -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.discover_error), style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)
            Spacer(Modifier.height(LumenSpacing.L))
            PillButton(text = stringResource(R.string.action_retry), onClick = viewModel::refresh)
        }
        is DiscoverState.Content -> Box(Modifier.fillMaxSize()) {
            FocusedBackdrop(viewModel)
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(NavClearance))
                Box(
                    Modifier.fillMaxWidth().height(HeroHeight).padding(horizontal = LumenSpacing.SafeHorizontal),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    FocusedHero(viewModel)
                }
                Row(
                    Modifier.padding(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.S),
                    horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S),
                ) {
                    PillButton(text = stringResource(R.string.browse_movies), onClick = { onBrowse(DiscoverKind.Movie) })
                    PillButton(text = stringResource(R.string.browse_tv), onClick = { onBrowse(DiscoverKind.Series) })
                    PillButton(text = stringResource(R.string.requests_title), onClick = onRequests)
                }
                DiscoverShelves(current.shelves, viewModel, onOpen, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FocusedBackdrop(viewModel: DiscoverViewModel) {
    val focused by viewModel.focused.collectAsStateWithLifecycle()
    HeroBackdrop(url = focused?.backdropUrl)
}

@Composable
private fun FocusedHero(viewModel: DiscoverViewModel) {
    val focused by viewModel.focused.collectAsStateWithLifecycle()
    DiscoverHero(focused)
}

@Composable
private fun DiscoverShelves(
    shelves: List<DiscoverShelf>,
    viewModel: DiscoverViewModel,
    onOpen: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titles = shelves.map { shelfTitle(it) }
    val specs = remember(shelves, titles) { shelves.mapIndexed { index, shelf -> ShelfSpec(shelf.kind.name, titles[index], shelf.items) } }
    PinnedShelves(
        shelves = specs,
        memory = viewModel.focusMemory,
        itemKey = { "${it.kind}-${it.tmdbId}" },
        modifier = modifier,
    ) { _, item, cardModifier, markFocused ->
        LumenCard(
            modifier = cardModifier,
            title = item.title,
            imageUrl = item.posterUrl,
            shape = CardShape.Poster,
            onClick = { onOpen(item) },
            onFocused = {
                markFocused()
                viewModel.onFocus(item)
            },
            badge = { AvailabilityBadge(item.availability) },
        )
    }
}

@Composable
fun DiscoverHero(item: DiscoverItem?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = item,
        contentKey = { it?.let { i -> "${i.kind}-${i.tmdbId}" } },
        transitionSpec = {
            fadeIn(tween(LumenMotion.Medium, delayMillis = 80, easing = LumenMotion.Standard)) togetherWith fadeOut(tween(LumenMotion.Fast))
        },
        modifier = modifier,
        label = "discover-hero",
    ) { current ->
        if (current == null) return@AnimatedContent
        Column(Modifier.widthIn(max = 560.dp)) {
            TitleLogo(
                title = current.title,
                logoUrl = rememberLogo(current),
                style = MaterialTheme.typography.displayMedium,
                maxHeight = 90.dp,
                maxWidth = 380.dp,
                modifier = Modifier.weight(1f, fill = false),
            )
            discoverMetadata(current)?.let {
                Spacer(Modifier.height(LumenSpacing.S))
                Text(it, style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
            }
            current.overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(LumenSpacing.M))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted, maxLines = LumenLayout.HeroOverviewLines, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun discoverMetadata(item: DiscoverItem, includeRating: Boolean = true): String? {
    val parts = listOfNotNull(
        stringResource(if (item.kind == DiscoverKind.Movie) R.string.kind_movie else R.string.kind_series),
        item.year?.toString(),
        item.rating?.takeIf { includeRating }?.let { stringResource(R.string.rating_star, it.toFloat()) },
        availabilityLabel(item.availability),
    )
    return parts.joinToString("  ·  ").ifEmpty { null }
}

@Composable
private fun shelfTitle(shelf: DiscoverShelf): String = stringResource(
    when (shelf.kind) {
        DiscoverShelfKind.Trending -> R.string.discover_trending
        DiscoverShelfKind.PopularMovies -> R.string.discover_popular_movies
        DiscoverShelfKind.PopularShows -> R.string.discover_popular_shows
        DiscoverShelfKind.UpcomingMovies -> R.string.discover_upcoming_movies
        DiscoverShelfKind.UpcomingShows -> R.string.discover_upcoming_shows
        DiscoverShelfKind.RecentlyRequested -> R.string.discover_recently_requested
    },
)
