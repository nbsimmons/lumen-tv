package app.lumen.tv.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.LibrarySort
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val NavClearance = 92.dp

@Composable
fun LibraryScreen(
    kind: MediaKind,
    onOpenItem: (MediaItem) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(key = "library-$kind") { parametersOf(kind) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var filtering by remember { mutableStateOf(false) }
    BackHandler(enabled = filtering) { filtering = false }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!filtering || event.key.nativeKeyCode != KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) filtering = false
                true
            },
    ) {
        Column(Modifier.fillMaxSize().padding(top = NavClearance)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = LumenSpacing.SafeHorizontal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (kind == MediaKind.Series) R.string.nav_shows else R.string.nav_movies),
                        style = MaterialTheme.typography.displayMedium,
                        color = LumenColors.OnSurface,
                    )
                    state.total?.let { total ->
                        Text(
                            pluralStringResource(if (kind == MediaKind.Series) R.plurals.library_count_shows else R.plurals.library_count_movies, total, total),
                            style = MaterialTheme.typography.labelLarge,
                            color = LumenColors.OnSurfaceFaint,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S)) {
                    PillButton(
                        text = state.filter.activeCount.takeIf { it > 0 }?.let { stringResource(R.string.filter_button_count, it) }
                            ?: stringResource(R.string.filter_button),
                        onClick = { filtering = true },
                        style = if (state.filter.activeCount > 0) PillStyle.Primary else PillStyle.Secondary,
                    )
                    LibrarySort.entries.forEach { sort ->
                        PillButton(
                            text = stringResource(sortLabel(sort)),
                            onClick = { viewModel.setSort(sort) },
                            style = if (sort == state.sort) PillStyle.Primary else PillStyle.Secondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(LumenSpacing.M))
            when {
                state.error -> Text(
                    stringResource(R.string.home_error),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumenColors.OnSurface,
                    modifier = Modifier.padding(LumenSpacing.SafeHorizontal),
                )
                state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
                state.items.isEmpty() -> Text(
                    stringResource(R.string.filter_no_results),
                    style = MaterialTheme.typography.titleMedium,
                    color = LumenColors.OnSurfaceMuted,
                    modifier = Modifier.padding(LumenSpacing.SafeHorizontal),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
                    horizontalArrangement = Arrangement.spacedBy(LumenSpacing.RowGap),
                    verticalArrangement = Arrangement.spacedBy(LumenSpacing.S),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        LumenCard(
                            title = item.title,
                            imageUrl = item.posterUrl,
                            shape = CardShape.Poster,
                            fillWidth = true,
                            onClick = { onOpenItem(item) },
                            watched = item.played,
                            onFocused = { viewModel.onItemFocused(index) },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = filtering,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            LibraryFilterPanel(filter = state.filter, genres = state.genres, onChange = viewModel::setFilter)
        }
    }
}

private const val COLUMNS = 7

private fun sortLabel(sort: LibrarySort): Int = when (sort) {
    LibrarySort.Title -> R.string.sort_title
    LibrarySort.DateAdded -> R.string.sort_date_added
    LibrarySort.Year -> R.string.sort_year
    LibrarySort.Rating -> R.string.sort_rating
}
