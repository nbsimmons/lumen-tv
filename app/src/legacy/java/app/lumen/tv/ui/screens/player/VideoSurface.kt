package app.lumen.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    val presentation = rememberPresentationState(player)
    Box(modifier, contentAlignment = Alignment.Center) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp),
        )
        if (presentation.coverSurface) Box(Modifier.fillMaxSize().background(Color.Black))
    }
}
