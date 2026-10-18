package app.lumen.tv.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing

enum class TopDestination(val route: String, @StringRes val label: Int, val shown: Boolean = true) {
    Home("home", R.string.nav_home),
    Movies("movies", R.string.nav_movies),
    Shows("shows", R.string.nav_shows),
    LiveTv("live", R.string.nav_live_tv, shown = false),
    Discover("discover", R.string.nav_discover),
    Search("search", R.string.nav_search),
    Settings("settings", R.string.nav_settings),
}

@Composable
fun TopNav(
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedFocusRequester = remember { FocusRequester() }
    Box(modifier.fillMaxWidth().padding(top = LumenSpacing.SafeVertical), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .background(LumenColors.Glass, LumenShapes.Pill)
                .border(1.dp, LumenColors.GlassStroke, LumenShapes.Pill)
                .padding(6.dp)
                .focusProperties { onEnter = { selectedFocusRequester.requestFocus() } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TopDestination.entries.filter { it.shown }.forEach { destination ->
                val selected = destination == current
                Surface(
                    onClick = { onSelect(destination) },
                    modifier = if (selected) Modifier.focusRequester(selectedFocusRequester) else Modifier,
                    shape = ClickableSurfaceDefaults.shape(LumenShapes.Pill),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) LumenColors.NavSelected else LumenColors.Glass.copy(alpha = 0f),
                        contentColor = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
                        focusedContainerColor = LumenColors.FocusFill,
                        focusedContentColor = LumenColors.OnFocusFill,
                    ),
                ) {
                    Text(
                        text = stringResource(destination.label),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
