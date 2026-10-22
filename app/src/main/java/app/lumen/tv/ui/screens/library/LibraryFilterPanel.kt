package app.lumen.tv.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.LibraryFilter
import app.lumen.tv.data.model.WatchFilter
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.SheetHeading
import app.lumen.tv.ui.components.SheetRow
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import java.time.Year

@Composable
fun LibraryFilterPanel(filter: LibraryFilter, genres: List<String>, onChange: (LibraryFilter) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val decades = remember {
        val latest = Year.now().value / 10 * 10
        (latest downTo FIRST_DECADE step 10).toList()
    }
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
            stringResource(R.string.filter_title),
            style = MaterialTheme.typography.titleLarge,
            color = LumenColors.OnSurface,
            modifier = Modifier.padding(start = LumenSpacing.M, bottom = LumenSpacing.S),
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = LumenSpacing.S),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SheetHeading(stringResource(R.string.filter_show)) }
            items(WatchFilter.entries, key = { "watch-$it" }) { option ->
                SheetRow(
                    label = stringResource(
                        when (option) {
                            WatchFilter.All -> R.string.filter_all
                            WatchFilter.Unwatched -> R.string.filter_unwatched
                            WatchFilter.Watched -> R.string.filter_watched
                        },
                    ),
                    checked = filter.watched == option,
                    onClick = { onChange(filter.copy(watched = option)) },
                    modifier = if (option == WatchFilter.All) Modifier.focusRequester(first) else Modifier,
                )
            }
            item { SheetHeading(stringResource(R.string.filter_quality)) }
            item { SheetRow(label = stringResource(R.string.filter_4k), checked = filter.uhdOnly, onClick = { onChange(filter.copy(uhdOnly = !filter.uhdOnly)) }) }
            item { SheetHeading(stringResource(R.string.filter_rating)) }
            items(listOf<Int?>(null) + RATINGS, key = { "rating-$it" }) { rating ->
                SheetRow(
                    label = rating?.let { stringResource(R.string.filter_rating_min, it) } ?: stringResource(R.string.filter_any),
                    checked = filter.minRating == rating,
                    onClick = { onChange(filter.copy(minRating = rating)) },
                )
            }
            item { SheetHeading(stringResource(R.string.filter_decade)) }
            items(listOf<Int?>(null) + decades, key = { "decade-$it" }) { decade ->
                SheetRow(
                    label = decade?.let { stringResource(R.string.filter_decade_value, it) } ?: stringResource(R.string.filter_any),
                    checked = filter.decade == decade,
                    onClick = { onChange(filter.copy(decade = decade)) },
                )
            }
            if (genres.isNotEmpty()) {
                item { SheetHeading(stringResource(R.string.filter_genre)) }
                items(listOf<String?>(null) + genres, key = { "genre-$it" }) { genre ->
                    SheetRow(
                        label = genre ?: stringResource(R.string.filter_any),
                        checked = filter.genre == genre,
                        onClick = { onChange(filter.copy(genre = genre)) },
                    )
                }
            }
        }
        if (filter.activeCount > 0) {
            PillButton(text = stringResource(R.string.filter_clear), onClick = { onChange(LibraryFilter()) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private const val FIRST_DECADE = 1950
private val RATINGS = listOf(6, 7, 8)
