package app.lumen.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
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
import app.lumen.tv.data.model.Availability
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes

@Composable
fun availabilityLabel(availability: Availability): String? = when (availability) {
    Availability.None -> null
    Availability.Requested -> stringResource(R.string.availability_requested)
    Availability.PartiallyAvailable -> stringResource(R.string.availability_partial)
    Availability.Available -> stringResource(R.string.availability_available)
}

fun availabilityColor(availability: Availability): Color = when (availability) {
    Availability.Available, Availability.PartiallyAvailable -> LumenColors.Available
    Availability.Requested -> LumenColors.Pending
    Availability.None -> Color.Transparent
}

@Composable
fun BoxScope.AvailabilityBadge(availability: Availability) {
    val label = if (availability == Availability.PartiallyAvailable) {
        stringResource(R.string.availability_partial_short)
    } else {
        availabilityLabel(availability) ?: return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = LumenColors.OnFocusFill,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .background(availabilityColor(availability), LumenShapes.Pill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
