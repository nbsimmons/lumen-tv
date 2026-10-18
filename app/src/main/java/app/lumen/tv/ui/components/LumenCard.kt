package app.lumen.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import app.lumen.tv.R
import androidx.tv.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenMotion
import app.lumen.tv.ui.theme.LumenShapes
import coil3.compose.AsyncImage

enum class CardShape(val ratio: Float, val width: Dp) {
    Poster(2f / 3f, 116.dp),
    Landscape(16f / 9f, 236.dp),
}

@Composable
fun LumenCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: CardShape = CardShape.Poster,
    subtitle: String? = null,
    progress: Float? = null,
    onFocused: () -> Unit = {},
    captionOnFocusOnly: Boolean = shape == CardShape.Poster,
    fillWidth: Boolean = false,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    watched: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }

    Column(if (fillWidth) modifier.fillMaxWidth() else modifier.width(shape.width)) {
        Card(
            onClick = onClick,
            onLongClick = onMenu,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(shape.ratio)
                .then(
                    if (onMenu == null) {
                        Modifier
                    } else {
                        Modifier.onPreviewKeyEvent { event ->
                            val menu = event.key.nativeKeyCode == KeyEvent.KEYCODE_MENU
                            if (menu && event.type == KeyEventType.KeyUp) onMenu()
                            menu
                        }
                    },
                ),
            shape = CardDefaults.shape(LumenShapes.Card),
            scale = CardDefaults.scale(focusedScale = LumenMotion.FocusScale),
            border = CardDefaults.border(focusedBorder = Border.None),
            glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.7f), elevation = 24.dp)),
            colors = CardDefaults.colors(containerColor = LumenColors.Surface),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    PlaceholderArt(title)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .alpha(if (focused) 1f else 0f)
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Transparent))),
                )
                badge?.invoke(this)
                if (watched) WatchedTick(Modifier.align(Alignment.TopEnd).padding(8.dp))
                if (progress != null && progress > 0f) {
                    ProgressPill(
                        progress = progress,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().height(4.dp),
                    )
                }
            }
        }
        val captionAlpha by animateFloatAsState(
            targetValue = if (!captionOnFocusOnly || focused) 1f else 0f,
            animationSpec = tween(LumenMotion.Fast),
            label = "caption",
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp).alpha(captionAlpha),
        )
        if (subtitle != null && !captionOnFocusOnly) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = LumenColors.OnSurfaceFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WatchedTick(modifier: Modifier = Modifier) {
    Box(
        modifier.size(26.dp).background(LumenColors.Scrim.copy(alpha = 0.75f), CircleShape).border(1.dp, LumenColors.GlassStroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = stringResource(R.string.detail_watched),
            tint = LumenColors.OnSurface,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun PlaceholderArt(title: String) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LumenColors.SurfaceRaised, LumenColors.Surface))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(1).uppercase(),
            style = MaterialTheme.typography.displayMedium,
            color = LumenColors.OnSurfaceFaint,
        )
    }
}

@Composable
fun ProgressPill(progress: Float, modifier: Modifier = Modifier, color: Color = LumenColors.OnSurface) {
    Box(modifier.background(color.copy(alpha = 0.25f), LumenShapes.Pill)) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxSize()
                .background(color, LumenShapes.Pill),
        )
    }
}
