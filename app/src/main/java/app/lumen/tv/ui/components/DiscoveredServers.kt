package app.lumen.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.discovery.DiscoveredServer
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing
import java.net.URI

@Composable
fun DiscoveredServers(
    servers: List<DiscoveredServer>,
    searching: Boolean,
    onPick: (DiscoveredServer) -> Unit,
    modifier: Modifier = Modifier,
    firstModifier: Modifier = Modifier,
) {
    if (servers.isEmpty() && !searching) return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (servers.isEmpty()) R.string.discovery_searching else R.string.discovery_found),
                style = MaterialTheme.typography.labelMedium,
                color = LumenColors.OnSurfaceFaint,
            )
            if (searching) {
                Spacer(Modifier.width(LumenSpacing.S))
                Spinner(size = 14.dp)
            }
        }
        Spacer(Modifier.height(LumenSpacing.S))
        Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S)) {
            servers.take(MAX_SHOWN).forEachIndexed { index, server ->
                PillButton(
                    text = "${server.name}  ·  ${hostOf(server.url)}",
                    onClick = { onPick(server) },
                    style = PillStyle.Primary,
                    modifier = if (index == 0) firstModifier else Modifier,
                )
            }
        }
    }
}

private fun hostOf(url: String): String = runCatching {
    val uri = URI(url)
    if (uri.port > 0) "${uri.host}:${uri.port}" else uri.host
}.getOrDefault(url)

private const val MAX_SHOWN = 3
