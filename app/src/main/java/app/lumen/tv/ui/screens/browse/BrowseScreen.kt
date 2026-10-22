package app.lumen.tv.ui.screens.browse

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.BrowseQuery
import app.lumen.tv.data.model.BrowseSort
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.Option
import app.lumen.tv.ui.components.AvailabilityBadge
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.SheetRow
import app.lumen.tv.ui.components.ShelfRow
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private enum class Picker { Genre, Provider, Sort }

@Composable
fun BrowseScreen(
    initial: BrowseQuery,
    onOpen: (DiscoverItem) -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    viewModel: BrowseViewModel = koinViewModel(key = "browse-$initial") { parametersOf(initial) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query = state.query
    var picker by remember { mutableStateOf<Picker?>(null) }
    BackHandler(enabled = picker != null) { picker = null }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (picker == null || event.key.nativeKeyCode != KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) picker = null
                true
            },
    ) {
        Column(Modifier.fillMaxSize().padding(top = LumenSpacing.XXL)) {
            Text(
                query.studio?.name ?: stringResource(if (query.kind == DiscoverKind.Movie) R.string.browse_movies else R.string.browse_tv),
                style = MaterialTheme.typography.displayMedium,
                color = LumenColors.OnSurface,
                modifier = Modifier.padding(horizontal = LumenSpacing.SafeHorizontal),
            )
            Row(
                Modifier.padding(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
                horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S),
            ) {
                if (query.studio == null) {
                    PillButton(
                        text = stringResource(R.string.browse_kind_movies),
                        onClick = { viewModel.setKind(DiscoverKind.Movie) },
                        style = if (query.kind == DiscoverKind.Movie) PillStyle.Primary else PillStyle.Secondary,
                    )
                    PillButton(
                        text = stringResource(R.string.browse_kind_tv),
                        onClick = { viewModel.setKind(DiscoverKind.Series) },
                        style = if (query.kind == DiscoverKind.Series) PillStyle.Primary else PillStyle.Secondary,
                    )
                }
                PillButton(
                    text = query.genre?.name ?: stringResource(R.string.browse_genre),
                    onClick = { picker = Picker.Genre },
                    style = if (query.genre != null) PillStyle.Primary else PillStyle.Secondary,
                )
                if (query.studio == null) {
                    PillButton(
                        text = query.provider?.name ?: stringResource(R.string.browse_service),
                        onClick = { picker = Picker.Provider },
                        style = if (query.provider != null) PillStyle.Primary else PillStyle.Secondary,
                    )
                }
                PillButton(text = stringResource(sortLabel(query.sort)), onClick = { picker = Picker.Sort })
            }
            when {
                state.error -> Text(
                    stringResource(R.string.discover_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = LumenColors.OnSurfaceMuted,
                    modifier = Modifier.padding(LumenSpacing.SafeHorizontal),
                )
                state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
                state.items.isEmpty() && state.library.isEmpty() -> Text(
                    stringResource(R.string.filter_no_results),
                    style = MaterialTheme.typography.titleMedium,
                    color = LumenColors.OnSurfaceMuted,
                    modifier = Modifier.padding(LumenSpacing.SafeHorizontal),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
                    horizontalArrangement = Arrangement.spacedBy(LumenSpacing.RowGap),
                    verticalArrangement = Arrangement.spacedBy(LumenSpacing.S),
                ) {
                    if (state.library.isNotEmpty()) {
                        item(key = "library", span = { GridItemSpan(maxLineSpan) }) {
                            ShelfRow(title = stringResource(R.string.search_in_library), modifier = Modifier.bleed(LumenSpacing.SafeHorizontal)) {
                                items(state.library, key = { it.id }) { item ->
                                    LumenCard(
                                        title = item.title,
                                        imageUrl = item.posterUrl,
                                        shape = CardShape.Poster,
                                        onClick = { onOpenItem(item) },
                                        watched = item.played,
                                    )
                                }
                            }
                        }
                        item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(R.string.browse_more_from, query.studio?.name.orEmpty()), style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
                        }
                    }
                    itemsIndexed(state.items, key = { _, item -> "${item.kind}-${item.tmdbId}" }) { index, item ->
                        LumenCard(
                            title = item.title,
                            imageUrl = item.posterUrl,
                            shape = CardShape.Poster,
                            fillWidth = true,
                            onClick = { onOpen(item) },
                            onFocused = { viewModel.onItemFocused(index) },
                            badge = { AvailabilityBadge(item.availability) },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = picker != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            when (picker) {
                Picker.Genre -> OptionPanel(stringResource(R.string.browse_genre), state.genres, query.genre) {
                    viewModel.setGenre(it)
                    picker = null
                }
                Picker.Provider -> OptionPanel(stringResource(R.string.browse_service), state.providers, query.provider) {
                    viewModel.setProvider(it)
                    picker = null
                }
                Picker.Sort -> SortPanel(query.sort) {
                    viewModel.setSort(it)
                    picker = null
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun PanelFrame(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(420.dp)
            .padding(LumenSpacing.L)
            .background(LumenColors.Glass.copy(alpha = 0.94f), LumenShapes.Panel)
            .border(1.dp, LumenColors.GlassStroke, LumenShapes.Panel)
            .padding(LumenSpacing.M),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface, modifier = Modifier.padding(start = LumenSpacing.M, bottom = LumenSpacing.S))
        content()
    }
}

@Composable
private fun OptionPanel(title: String, options: List<Option>, selected: Option?, onPick: (Option?) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    PanelFrame(title) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item(key = "any") {
                SheetRow(label = stringResource(R.string.filter_any), checked = selected == null, onClick = { onPick(null) }, modifier = Modifier.focusRequester(first))
            }
            items(options, key = { it.id }) { option ->
                SheetRow(label = option.name, checked = option.id == selected?.id, onClick = { onPick(option) })
            }
        }
    }
}

@Composable
private fun SortPanel(selected: BrowseSort, onPick: (BrowseSort) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    PanelFrame(stringResource(R.string.browse_sort)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BrowseSort.entries.forEach { sort ->
                SheetRow(
                    label = stringResource(sortLabel(sort)),
                    checked = sort == selected,
                    onClick = { onPick(sort) },
                    modifier = if (sort == BrowseSort.entries.first()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

private fun sortLabel(sort: BrowseSort): Int = when (sort) {
    BrowseSort.Popular -> R.string.browse_sort_popular
    BrowseSort.TopRated -> R.string.browse_sort_top
    BrowseSort.Newest -> R.string.browse_sort_newest
}

private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

private const val COLUMNS = 7
