package app.lumen.tv.ui.screens.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.Availability
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.RequestEntry
import app.lumen.tv.data.model.RequestScope
import app.lumen.tv.data.model.RequestStatus
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.components.availabilityColor
import app.lumen.tv.ui.components.availabilityLabel
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun RequestsScreen(onOpen: (DiscoverItem) -> Unit, viewModel: RequestsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(top = LumenSpacing.XXL)) {
        Text(
            stringResource(R.string.requests_title),
            style = MaterialTheme.typography.displayMedium,
            color = LumenColors.OnSurface,
            modifier = Modifier.padding(horizontal = LumenSpacing.SafeHorizontal),
        )
        if (state.canManage) {
            Row(
                Modifier.padding(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
                horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S),
            ) {
                PillButton(
                    text = stringResource(R.string.requests_mine),
                    onClick = { viewModel.setScope(RequestScope.Mine) },
                    style = if (state.scope == RequestScope.Mine) PillStyle.Primary else PillStyle.Secondary,
                )
                PillButton(
                    text = stringResource(R.string.requests_pending),
                    onClick = { viewModel.setScope(RequestScope.Pending) },
                    style = if (state.scope == RequestScope.Pending) PillStyle.Primary else PillStyle.Secondary,
                )
            }
        } else {
            Spacer(Modifier.height(LumenSpacing.M))
        }
        if (state.failed) {
            Text(
                stringResource(R.string.requests_action_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.Error,
                modifier = Modifier.padding(horizontal = LumenSpacing.SafeHorizontal),
            )
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            state.error -> Text(
                stringResource(R.string.discover_error),
                style = MaterialTheme.typography.titleMedium,
                color = LumenColors.OnSurfaceMuted,
                modifier = Modifier.padding(LumenSpacing.SafeHorizontal),
            )
            state.entries.isEmpty() -> Text(
                stringResource(if (state.scope == RequestScope.Mine) R.string.requests_empty else R.string.requests_none_pending),
                style = MaterialTheme.typography.titleMedium,
                color = LumenColors.OnSurfaceMuted,
                modifier = Modifier.padding(LumenSpacing.SafeHorizontal),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusRestorer(),
                contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
                verticalArrangement = Arrangement.spacedBy(LumenSpacing.S),
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    RequestRow(
                        entry = entry,
                        scope = state.scope,
                        busy = entry.id in state.busy,
                        onOpen = { onOpen(entry.item) },
                        onCancel = { viewModel.cancel(entry) },
                        onReview = { approve -> viewModel.review(entry, approve) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestRow(
    entry: RequestEntry,
    scope: RequestScope,
    busy: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onReview: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(LumenColors.Glass, LumenShapes.Panel)
            .padding(LumenSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumenSpacing.L),
    ) {
        AsyncImage(
            model = entry.item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(64.dp).aspectRatio(2f / 3f).clip(LumenShapes.Card).background(LumenColors.Surface),
        )
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(entry.item.title, entry.item.year?.toString()).joinToString("  ·  "),
                style = MaterialTheme.typography.titleMedium,
                color = LumenColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when {
                entry.status == RequestStatus.Declined -> stringResource(R.string.requests_status_declined) to LumenColors.Error
                entry.status == RequestStatus.Pending -> stringResource(R.string.requests_status_pending) to LumenColors.Pending
                entry.item.availability != Availability.None ->
                    (availabilityLabel(entry.item.availability) ?: "") to availabilityColor(entry.item.availability)
                else -> stringResource(R.string.requests_status_approved) to LumenColors.Accent
            }
            Text(status.first, style = MaterialTheme.typography.labelLarge, color = status.second)
            val details = listOfNotNull(
                entry.requestedAt?.let { stringResource(R.string.requests_requested_on, formatDate(it)) },
                entry.requestedBy?.takeIf { !entry.mine }?.let { stringResource(R.string.requests_by, it) },
                entry.seasons.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.requests_seasons, it.joinToString(", ")) },
                stringResource(R.string.discover_version_4k).takeIf { entry.is4k },
            ).joinToString("  ·  ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceFaint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S)) {
            PillButton(text = stringResource(R.string.requests_open), onClick = onOpen)
            when {
                scope == RequestScope.Pending -> {
                    PillButton(text = stringResource(R.string.requests_approve), onClick = { onReview(true) }, style = PillStyle.Primary, enabled = !busy)
                    PillButton(text = stringResource(R.string.requests_decline), onClick = { onReview(false) }, enabled = !busy)
                }
                entry.mine && entry.status == RequestStatus.Pending ->
                    PillButton(text = stringResource(R.string.requests_cancel), onClick = onCancel, enabled = !busy)
            }
        }
    }
}

@Composable
private fun formatDate(epochMs: Long): String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ENGLISH
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMs))
}
