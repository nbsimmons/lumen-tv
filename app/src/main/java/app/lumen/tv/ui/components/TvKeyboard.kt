package app.lumen.tv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing

private val KeySize = 44.dp
private val CHARACTERS = ("abcdefghijklmnopqrstuvwxyz" + "1234567890").map { it.toString() }

@Composable
fun TvKeyboard(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    firstKeyModifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(CHARACTERS, key = { it }) { key ->
            Key(onClick = { onKey(key) }, modifier = if (key == CHARACTERS.first()) firstKeyModifier else Modifier) {
                Text(key, style = MaterialTheme.typography.titleLarge)
            }
        }
        item(key = "space") {
            Key(onClick = { onKey(" ") }, wide = true) {
                Text(stringResource(R.string.keyboard_space), style = MaterialTheme.typography.labelLarge)
            }
        }
        item(key = "delete") {
            Key(onClick = onBackspace, onLongClick = onClear, wide = true) {
                KeyIcon(R.drawable.ic_backspace, stringResource(R.string.keyboard_delete))
            }
        }
        item(key = "clear") {
            Key(onClick = onClear, wide = true) {
                Text(stringResource(R.string.keyboard_clear), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Key(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    wide: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(KeySize).widthIn(min = KeySize),
        shape = ClickableSurfaceDefaults.shape(LumenShapes.Card),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = LumenColors.OnSurfaceMuted,
            focusedContainerColor = LumenColors.FocusFill,
            focusedContentColor = LumenColors.OnFocusFill,
            pressedContainerColor = LumenColors.SurfaceRaised,
        ),
    ) {
        Box(
            Modifier.height(KeySize).widthIn(min = KeySize).padding(horizontal = if (wide) LumenSpacing.M else 0.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun KeyIcon(@DrawableRes icon: Int, description: String) {
    Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(22.dp))
}
