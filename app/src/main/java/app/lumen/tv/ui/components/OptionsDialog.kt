package app.lumen.tv.ui.components

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing

data class DialogOption(val label: String, val destructive: Boolean = false, val onSelect: () -> Unit)

@Composable
fun OptionsDialog(title: String, options: List<DialogOption>, onDismiss: () -> Unit, message: String? = null) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .onPreviewKeyEvent { event ->
                if (event.key.nativeKeyCode != KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) onDismiss()
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .background(LumenColors.Glass.copy(alpha = 0.96f), LumenShapes.Panel)
                .border(1.dp, LumenColors.GlassStroke, LumenShapes.Panel)
                .padding(LumenSpacing.L)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(LumenSpacing.S),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = LumenColors.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = LumenSpacing.S),
            )
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LumenColors.OnSurfaceMuted,
                    modifier = Modifier.padding(bottom = LumenSpacing.S),
                )
            }
            options.forEachIndexed { index, option ->
                Surface(
                    onClick = {
                        onDismiss()
                        option.onSelect()
                    },
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(LumenShapes.Card),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = LumenColors.Surface.copy(alpha = 0.6f),
                        contentColor = if (option.destructive) LumenColors.Error else LumenColors.OnSurface,
                        focusedContainerColor = LumenColors.FocusFill,
                        focusedContentColor = if (option.destructive) LumenColors.Error else LumenColors.OnFocusFill,
                    ),
                ) {
                    Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = LumenSpacing.L, vertical = 14.dp))
                }
            }
        }
    }
}
