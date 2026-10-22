package app.lumen.tv.ui.screens.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.logos.TmdbKeyCheck
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.TvTextField
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing

@Composable
fun TmdbKeyDialog(
    hasKey: Boolean,
    check: TmdbKeyCheck?,
    saving: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    var backPressed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .onPreviewKeyEvent { event ->
                if (event.key.nativeKeyCode != KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> backPressed = true
                    KeyEventType.KeyUp -> if (backPressed) onDismiss()
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(620.dp)
                .background(LumenColors.Glass.copy(alpha = 0.96f), LumenShapes.Panel)
                .border(1.dp, LumenColors.GlassStroke, LumenShapes.Panel)
                .padding(LumenSpacing.L)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(LumenSpacing.M),
        ) {
            Text(stringResource(R.string.tmdb_title), style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)
            Text(stringResource(R.string.tmdb_body), style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
            TvTextField(
                value = key,
                onValueChange = { key = it },
                placeholder = stringResource(if (hasKey) R.string.tmdb_placeholder_replace else R.string.tmdb_placeholder),
                width = null,
                imeAction = ImeAction.Done,
                modifier = Modifier
                    .focusRequester(field)
                    .focusProperties { down = if (key.isNotBlank()) save else FocusRequester.Default },
            )
            val message = when {
                saving -> stringResource(R.string.tmdb_checking)
                check == TmdbKeyCheck.Invalid -> stringResource(R.string.tmdb_invalid)
                check == TmdbKeyCheck.Unreachable -> stringResource(R.string.tmdb_unreachable)
                else -> null
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (saving) LumenColors.OnSurfaceMuted else LumenColors.Error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S)) {
                PillButton(
                    text = stringResource(R.string.tmdb_save),
                    onClick = { onSave(key) },
                    style = PillStyle.Primary,
                    enabled = key.isNotBlank() && !saving,
                    modifier = Modifier.focusRequester(save),
                )
                if (hasKey) PillButton(text = stringResource(R.string.tmdb_remove), onClick = onRemove)
                PillButton(text = stringResource(R.string.player_cancel), onClick = onDismiss)
            }
        }
    }
}
