package app.lumen.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing

@Composable
fun PrivacyScreen() {
    val headings = stringArrayResource(R.array.privacy_headings)
    val bodies = stringArrayResource(R.array.privacy_bodies)
    val sections = headings.zip(bodies)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = LumenSpacing.XXL),
            verticalArrangement = Arrangement.spacedBy(LumenSpacing.XS),
        ) {
            item {
                Text(
                    stringResource(R.string.settings_privacy),
                    style = MaterialTheme.typography.displayMedium,
                    color = LumenColors.OnSurface,
                    modifier = Modifier.padding(horizontal = LumenSpacing.M, vertical = LumenSpacing.M),
                )
            }
            itemsIndexed(sections) { index, (heading, body) ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Column(
                    Modifier
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .focusable(interactionSource = interaction)
                        .background(if (focused) LumenColors.Surface else Color.Transparent, LumenShapes.Card)
                        .padding(horizontal = LumenSpacing.M, vertical = LumenSpacing.S),
                ) {
                    Text(heading, style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = LumenColors.OnSurfaceMuted)
                }
            }
        }
    }
}
