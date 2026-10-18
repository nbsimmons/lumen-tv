package app.lumen.tv.ui.screens.person

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.ui.components.AvailabilityBadge
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.ShelfRow
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing
import coil3.compose.AsyncImage
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PersonScreen(
    ref: PersonRef,
    onOpenItem: (MediaItem) -> Unit,
    onOpenDiscover: (DiscoverItem) -> Unit,
    viewModel: PersonViewModel = koinViewModel(key = "person-${ref.jellyfinId}-${ref.tmdbId}") { parametersOf(ref) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val person = state.person
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Spinner(Modifier.align(Alignment.Center))
            state.error || person == null -> Text(
                stringResource(R.string.detail_error),
                style = MaterialTheme.typography.titleLarge,
                color = LumenColors.OnSurface,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> Column(Modifier.fillMaxSize().padding(top = LumenSpacing.XXL)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = LumenSpacing.SafeHorizontal), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(140.dp).clip(CircleShape).background(LumenColors.Surface), contentAlignment = Alignment.Center) {
                        if (person.imageUrl != null) {
                            AsyncImage(model = person.imageUrl, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(person.name.take(1), style = MaterialTheme.typography.displayMedium, color = LumenColors.OnSurfaceFaint)
                        }
                    }
                    Spacer(Modifier.width(LumenSpacing.L))
                    Column {
                        Text(person.name, style = MaterialTheme.typography.displayMedium, color = LumenColors.OnSurface)
                        Text(
                            pluralStringResource(R.plurals.person_in_library, state.library.size, state.library.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = LumenColors.OnSurfaceMuted,
                        )
                    }
                }
                Spacer(Modifier.height(LumenSpacing.L))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = LumenSpacing.XXL),
                    verticalArrangement = Arrangement.spacedBy(LumenSpacing.L),
                ) {
                    if (state.library.isNotEmpty()) {
                        item(key = "library") {
                            val entry = remember { FocusRequester() }
                            ShelfRow(title = stringResource(R.string.search_in_library), entry = entry) {
                                items(state.library, key = { it.id }) { item ->
                                    LumenCard(
                                        title = item.title,
                                        imageUrl = item.posterUrl,
                                        shape = CardShape.Poster,
                                        onClick = { onOpenItem(item) },
                                        watched = item.played,
                                        modifier = if (item == state.library.first()) Modifier.focusRequester(entry) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                    if (state.requestable.isNotEmpty()) {
                        item(key = "requestable") {
                            val entry = remember { FocusRequester() }
                            ShelfRow(title = stringResource(R.string.search_requestable), entry = entry) {
                                items(state.requestable, key = { "${it.kind}-${it.tmdbId}" }) { item ->
                                    LumenCard(
                                        title = item.title,
                                        imageUrl = item.posterUrl,
                                        shape = CardShape.Poster,
                                        onClick = { onOpenDiscover(item) },
                                        badge = { AvailabilityBadge(item.availability) },
                                        modifier = if (item == state.requestable.first()) Modifier.focusRequester(entry) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
