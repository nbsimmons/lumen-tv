package app.lumen.tv.ui.screens.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
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
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.screens.discover.SeerrConnectScreen
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing
import org.koin.androidx.compose.koinViewModel

private val NavClearance = 92.dp
private const val COLUMNS = 7

@Composable
fun CollectionsScreen(
    onOpenCollection: (Int) -> Unit,
    viewModel: CollectionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        CollectionsState.NotConnected -> SeerrConnectScreen()
        CollectionsState.Loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner()
            Spacer(Modifier.height(LumenSpacing.M))
            Text(stringResource(R.string.collections_loading), style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
        }
        CollectionsState.Error -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.home_error), style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)
            Spacer(Modifier.height(LumenSpacing.L))
            PillButton(text = stringResource(R.string.action_retry), onClick = viewModel::refresh)
        }
        is CollectionsState.Content -> Column(Modifier.fillMaxSize().padding(top = NavClearance)) {
            Column(Modifier.padding(horizontal = LumenSpacing.SafeHorizontal)) {
                Text(stringResource(R.string.nav_collections), style = MaterialTheme.typography.displayMedium, color = LumenColors.OnSurface)
                Text(
                    pluralStringResource(R.plurals.collections_count, current.collections.size, current.collections.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumenColors.OnSurfaceFaint,
                )
            }
            Spacer(Modifier.height(LumenSpacing.M))
            if (current.collections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.collections_empty), style = MaterialTheme.typography.bodyLarge, color = LumenColors.OnSurfaceMuted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
                    horizontalArrangement = Arrangement.spacedBy(LumenSpacing.RowGap),
                    verticalArrangement = Arrangement.spacedBy(LumenSpacing.S),
                ) {
                    items(current.collections, key = { it.ref.tmdbId }) { collection ->
                        LumenCard(
                            title = collection.ref.name,
                            imageUrl = collection.ref.posterUrl,
                            shape = CardShape.Poster,
                            fillWidth = true,
                            subtitle = pluralStringResource(R.plurals.collections_owned, collection.owned, collection.owned),
                            captionOnFocusOnly = false,
                            onClick = { onOpenCollection(collection.ref.tmdbId) },
                        )
                    }
                }
            }
        }
    }
}
