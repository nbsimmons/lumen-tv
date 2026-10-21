package app.lumen.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.MovieCollection
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing

@Composable
fun CollectionShelf(
    collection: MovieCollection,
    currentTmdbId: Int?,
    onOpenFilm: (DiscoverItem) -> Unit,
    onOpenCollection: (request: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val films = collection.parts.filter { it.tmdbId != currentTmdbId }
    if (films.isEmpty()) return
    val missing = collection.missing.size
    Column(modifier) {
        Row(
            Modifier.padding(horizontal = LumenSpacing.SafeHorizontal),
            horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(collection.ref.name, style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
            Text(
                stringResource(R.string.collection_in_library, collection.available, collection.parts.size),
                style = MaterialTheme.typography.labelMedium,
                color = LumenColors.OnSurfaceFaint,
            )
        }
        Row(
            Modifier.padding(start = LumenSpacing.SafeHorizontal, top = LumenSpacing.S),
            horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S),
        ) {
            PillButton(text = stringResource(R.string.collection_view), onClick = { onOpenCollection(false) })
            if (missing > 0) {
                PillButton(
                    text = pluralStringResource(R.plurals.collection_request_missing, missing, missing),
                    onClick = { onOpenCollection(true) },
                )
            }
        }
        val entry = remember { FocusRequester() }
        ShelfRow(title = null, entry = entry) {
            items(films, key = { it.tmdbId }) { film ->
                LumenCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    shape = CardShape.Poster,
                    onClick = { onOpenFilm(film) },
                    badge = { AvailabilityBadge(film.availability) },
                    modifier = if (film == films.first()) Modifier.focusRequester(entry) else Modifier,
                )
            }
        }
    }
}
