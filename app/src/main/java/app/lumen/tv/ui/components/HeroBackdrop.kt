package app.lumen.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenMotion
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.request.transformations
import kotlinx.coroutines.delay

@Composable
fun HeroBackdrop(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf(url?.takeIf { it in loaded }) }

    LaunchedEffect(url) {
        if (url == null) {
            shown = null
            return@LaunchedEffect
        }
        if (url !in loaded) {
            if (shown != null) delay(FOCUS_SETTLE_MS)
            val result = SingletonImageLoader.get(context).execute(request(context, url))
            if (result !is SuccessResult) return@LaunchedEffect
            loaded += url
        }
        shown = url
    }

    Crossfade(
        targetState = shown,
        animationSpec = tween(LumenMotion.Slow, easing = LumenMotion.Standard),
        modifier = modifier.fillMaxSize(),
        label = "backdrop",
    ) { current ->
        if (current != null) {
            AsyncImage(
                model = request(context, current),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(LumenColors.Background))
        }
    }
}

private val scrim = ScrimTransformation()

private val loaded: MutableSet<String> = java.util.Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>() {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > LOADED_LIMIT
})

private fun request(context: PlatformContext, url: String) =
    ImageRequest.Builder(context).data(url).size(ScrimTransformation.WIDTH, ScrimTransformation.HEIGHT).transformations(scrim).crossfade(false).build()

private const val FOCUS_SETTLE_MS = 220L
private const val LOADED_LIMIT = 40
