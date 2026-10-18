package app.lumen.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing
import coil3.compose.AsyncImage

data class CastEntry(val key: String, val name: String, val role: String?, val imageUrl: String?)

@Composable
fun CastRow(people: List<CastEntry>, onOpen: (CastEntry) -> Unit, modifier: Modifier = Modifier) {
    val entry = remember { FocusRequester() }
    Column(modifier) {
        Text(
            stringResource(R.string.detail_cast),
            style = MaterialTheme.typography.titleMedium,
            color = LumenColors.OnSurface,
            modifier = Modifier.padding(start = LumenSpacing.SafeHorizontal),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(entry),
            contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
            horizontalArrangement = Arrangement.spacedBy(LumenSpacing.L),
        ) {
            items(people, key = { it.key }) { person ->
                Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { onOpen(person) },
                        modifier = Modifier.size(88.dp).then(if (person == people.first()) Modifier.focusRequester(entry) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        colors = ClickableSurfaceDefaults.colors(containerColor = LumenColors.Surface, focusedContainerColor = LumenColors.SurfaceRaised),
                    ) {
                        if (person.imageUrl != null) {
                            AsyncImage(
                                model = person.imageUrl,
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(person.name.take(1), style = MaterialTheme.typography.headlineMedium, color = LumenColors.OnSurfaceFaint)
                            }
                        }
                    }
                    Spacer(Modifier.height(LumenSpacing.S))
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = LumenColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    person.role?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = LumenColors.OnSurfaceFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
