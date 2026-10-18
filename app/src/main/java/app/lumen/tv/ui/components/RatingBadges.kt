package app.lumen.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing

@Composable
fun RatingBadges(ratings: ExternalRatings, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S), verticalAlignment = Alignment.CenterVertically) {
        ratings.imdb?.let {
            Badge(
                label = stringResource(R.string.rating_imdb_label),
                value = stringResource(R.string.rating_imdb_value, it),
                labelColor = LumenColors.ImdbText,
                labelBackground = LumenColors.Imdb,
            )
        }
        ratings.tomatometer?.let {
            Badge(
                label = stringResource(R.string.rating_tomatometer),
                value = stringResource(R.string.rating_percent, it),
                labelColor = LumenColors.OnSurface,
                labelBackground = LumenColors.Tomato,
            )
        }
        ratings.audience?.let {
            Badge(
                label = stringResource(R.string.rating_audience),
                value = stringResource(R.string.rating_percent, it),
                labelColor = LumenColors.OnSurface,
                labelBackground = LumenColors.SurfaceRaised,
            )
        }
    }
}

@Composable
private fun Badge(label: String, value: String, labelColor: Color, labelBackground: Color) {
    Row(
        Modifier.background(LumenColors.Glass, LumenShapes.Pill).padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier.background(labelBackground, LumenShapes.Pill).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = LumenColors.OnSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
