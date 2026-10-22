package app.lumen.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun Screensaver(repository: JellyfinRepository = koinInject()) {
    val items by produceState(emptyList<MediaItem>()) { value = runCatching { repository.screensaverItems() }.getOrDefault(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(items) {
        while (items.isNotEmpty()) {
            delay(SLIDE_MS)
            index = (index + 1) % items.size
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalTime.now()
            delay(CLOCK_MS)
        }
    }
    val drift = rememberInfiniteTransition(label = "drift")
    val scale by drift.animateFloat(1f, 1.08f, infiniteRepeatable(tween(SLIDE_MS.toInt(), easing = LinearEasing), RepeatMode.Reverse), label = "scale")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = items.getOrNull(index), animationSpec = tween(FADE_MS), label = "screensaver") { item ->
            if (item == null) return@Crossfade
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
                )
                Column(Modifier.align(Alignment.BottomStart).padding(LumenSpacing.XXL)) {
                    TitleLogo(title = item.title, logoUrl = item.logoUrl, style = MaterialTheme.typography.displayMedium, maxHeight = 90.dp, maxWidth = 380.dp)
                    item.year?.let { Text(it.toString(), style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted) }
                }
            }
        }
        Text(
            clock.format(CLOCK_FORMAT),
            style = MaterialTheme.typography.displayMedium,
            color = LumenColors.OnSurface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.TopEnd).padding(LumenSpacing.XXL),
        )
    }
}

private const val SLIDE_MS = 12_000L
private const val FADE_MS = 2_000
private const val CLOCK_MS = 15_000L
private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
