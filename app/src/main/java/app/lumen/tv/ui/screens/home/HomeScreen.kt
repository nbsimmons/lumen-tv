package app.lumen.tv.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.Shelf
import app.lumen.tv.data.model.ShelfKind
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.DialogOption
import app.lumen.tv.ui.components.OptionsDialog
import app.lumen.tv.ui.components.HeroBackdrop
import app.lumen.tv.ui.components.HeroInfo
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PinnedShelves
import app.lumen.tv.ui.components.ShelfFocusMemory
import app.lumen.tv.ui.components.ShelfSpec
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing
import org.koin.androidx.compose.koinViewModel

private val NavClearance = 84.dp
private val HeroHeight = 180.dp

@Composable
fun HomeScreen(
    onOpenItem: (MediaItem) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focused by viewModel.focused.collectAsStateWithLifecycle()
    val hero by viewModel.hero.collectAsStateWithLifecycle()
    ReportDrawnWhen { state is HomeState.Content }
    var menu by remember { mutableStateOf<Pair<MediaItem, Shelf>?>(null) }
    var confirmReset by remember { mutableStateOf<MediaItem?>(null) }
    var refocus by remember { mutableIntStateOf(0) }
    var dialogClosed by remember { mutableIntStateOf(0) }
    LaunchedEffect(dialogClosed) {
        if (dialogClosed > 0 && menu == null && confirmReset == null) refocus++
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(url = focused?.backdropUrl)
        when (val current = state) {
            HomeState.Loading -> Spinner(Modifier.align(Alignment.Center))
            HomeState.Error -> ErrorState(onRetry = viewModel::refresh)
            is HomeState.Content -> if (current.shelves.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineMedium, color = LumenColors.OnSurface)
                    Spacer(Modifier.height(LumenSpacing.S))
                    Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyLarge, color = LumenColors.OnSurfaceMuted)
                }
            } else Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(NavClearance))
                Box(
                    Modifier.fillMaxWidth().height(HeroHeight).padding(horizontal = LumenSpacing.SafeHorizontal),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    HeroInfo(item = hero?.item, ratings = hero?.ratings)
                }
                Spacer(Modifier.height(LumenSpacing.M))
                Shelves(
                    shelves = current.shelves,
                    memory = viewModel.focusMemory,
                    onFocus = viewModel::onFocus,
                    onOpenItem = onOpenItem,
                    onMenu = { item, shelf -> menu = item to shelf },
                    refocus = refocus,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        menu?.let { (item, shelf) ->
            val sectionTitle = shelfTitle(shelf)
            OptionsDialog(
                title = item.title,
                options = listOfNotNull(
                    DialogOption(stringResource(R.string.home_remove_continue)) { viewModel.removeFromContinueWatching(item) }
                        .takeIf { shelf.kind == ShelfKind.ContinueWatching },
                    DialogOption(stringResource(R.string.home_remove_up_next)) { confirmReset = item }
                        .takeIf { shelf.kind == ShelfKind.NextUp && item.seriesId != null },
                    DialogOption(stringResource(R.string.detail_mark_watched)) { viewModel.markWatched(item) }
                        .takeIf { !item.played },
                    DialogOption(stringResource(R.string.home_hide_section, sectionTitle), destructive = true) { viewModel.hideSection(shelf.key) },
                    DialogOption(stringResource(R.string.player_cancel)) {},
                ),
                onDismiss = {
                    menu = null
                    dialogClosed++
                },
            )
        }
        confirmReset?.let { item ->
            OptionsDialog(
                title = stringResource(R.string.home_reset_show_title, item.title),
                message = stringResource(R.string.home_reset_show_body),
                options = listOf(
                    DialogOption(stringResource(R.string.player_cancel)) {},
                    DialogOption(stringResource(R.string.home_reset_show_confirm), destructive = true) { viewModel.resetShow(item) },
                ),
                onDismiss = {
                    confirmReset = null
                    dialogClosed++
                },
            )
        }
    }
}

@Composable
private fun Shelves(
    shelves: List<Shelf>,
    memory: ShelfFocusMemory,
    onFocus: (MediaItem) -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onMenu: (MediaItem, Shelf) -> Unit,
    refocus: Int,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.home_options_hint)
    val specs = shelves.mapIndexed { index, shelf -> ShelfSpec(shelf.key, shelfTitle(shelf), shelf.items, if (index == 0) hint else null) }
    val byKey = shelves.associateBy { it.key }
    PinnedShelves(shelves = specs, memory = memory, itemKey = { it.id }, modifier = modifier, refocus = refocus) { shelf, item, cardModifier, markFocused ->
        val source = byKey.getValue(shelf.key)
        val kind = source.kind
        val landscape = kind == ShelfKind.ContinueWatching || kind == ShelfKind.NextUp
        LumenCard(
            modifier = cardModifier,
            title = item.title,
            subtitle = if (landscape) item.subtitle else item.year?.toString(),
            imageUrl = if (landscape) item.thumbUrl else item.posterUrl,
            shape = if (landscape) CardShape.Landscape else CardShape.Poster,
            progress = if (kind == ShelfKind.ContinueWatching) item.progress else null,
            watched = item.played,
            onClick = { onOpenItem(item) },
            onMenu = { onMenu(item, source) },
            onFocused = {
                markFocused()
                onFocus(item)
            },
        )
    }
}

@Composable
private fun shelfTitle(shelf: Shelf): String = homeSectionTitle(shelf.kind, shelf.title)

@Composable
fun homeSectionTitle(kind: ShelfKind, library: String?): String = when (kind) {
    ShelfKind.ContinueWatching -> stringResource(R.string.shelf_continue_watching)
    ShelfKind.NextUp -> stringResource(R.string.shelf_next_up)
    ShelfKind.Watchlist -> stringResource(R.string.shelf_watchlist)
    ShelfKind.RecentlyAdded -> stringResource(R.string.shelf_recently_added, library.orEmpty())
    ShelfKind.NewReleases -> stringResource(R.string.shelf_new_releases, library.orEmpty())
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_error), style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)
        Spacer(Modifier.height(LumenSpacing.L))
        PillButton(text = stringResource(R.string.action_retry), onClick = onRetry)
    }
}
