package app.lumen.tv.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    ContentFrame(player = player, modifier = modifier, surfaceType = SURFACE_TYPE_SURFACE_VIEW, contentScale = ContentScale.Fit)
}
