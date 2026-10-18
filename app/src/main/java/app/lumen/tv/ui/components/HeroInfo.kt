package app.lumen.tv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenLayout
import app.lumen.tv.ui.theme.LumenMotion
import app.lumen.tv.ui.theme.LumenSpacing

@Composable
fun HeroInfo(item: MediaItem?, ratings: ExternalRatings? = null, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = item to ratings,
        contentKey = { it.first?.id },
        transitionSpec = {
            fadeIn(tween(LumenMotion.Medium, delayMillis = 80, easing = LumenMotion.Standard)) togetherWith
                fadeOut(tween(LumenMotion.Fast))
        },
        modifier = modifier,
        label = "hero-info",
    ) { (current, currentRatings) ->
        if (current == null) return@AnimatedContent
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            TitleLogo(
                title = current.title,
                logoUrl = current.logoUrl,
                style = MaterialTheme.typography.displayMedium,
                maxHeight = 72.dp,
                maxWidth = 300.dp,
                modifier = Modifier.weight(1f, fill = false),
            )
            val meta = metadataLine(current, includeRating = currentRatings == null)
            Spacer(Modifier.height(LumenSpacing.S))
            if (current.kind == MediaKind.Episode && current.subtitle != null) {
                Row {
                    Text(
                        current.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = LumenColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                    )
                    meta?.let {
                        Spacer(Modifier.width(LumenSpacing.M))
                        Text(it, style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted, maxLines = 1, modifier = Modifier.alignByBaseline())
                    }
                }
            } else {
                meta?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted, maxLines = 1) }
            }
            currentRatings?.let {
                Spacer(Modifier.height(LumenSpacing.S))
                RatingBadges(it)
            }
            current.overview?.let {
                Spacer(Modifier.height(LumenSpacing.M))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LumenColors.OnSurfaceMuted,
                    maxLines = LumenLayout.HeroOverviewLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun metadataLine(item: MediaItem, includeRating: Boolean = true): String? {
    val parts = buildList {
        if (item.kind != MediaKind.Episode) item.year?.let { add(it.toString()) }
        item.officialRating?.let(::add)
        item.runtimeMinutes?.takeIf { it > 0 }?.let { add(formatRuntime(it)) }
        item.communityRating?.takeIf { includeRating }?.let { add(stringResource(R.string.rating_star, it)) }
        item.genres.take(2).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

@Composable
private fun formatRuntime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.runtime_minutes, m)
        m == 0 -> stringResource(R.string.runtime_hours, h)
        else -> stringResource(R.string.runtime_hours_minutes, h, m)
    }
}
