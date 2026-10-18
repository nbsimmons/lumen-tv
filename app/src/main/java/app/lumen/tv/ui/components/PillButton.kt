package app.lumen.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes

enum class PillStyle { Primary, Secondary, Selected }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Secondary,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(LumenShapes.Pill),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when (style) {
                PillStyle.Primary -> LumenColors.SurfaceRaised
                PillStyle.Selected -> LumenColors.NavSelected
                PillStyle.Secondary -> LumenColors.Glass
            },
            contentColor = LumenColors.OnSurface,
            focusedContainerColor = LumenColors.FocusFill,
            focusedContentColor = LumenColors.OnFocusFill,
            disabledContainerColor = LumenColors.Surface,
            disabledContentColor = LumenColors.OnSurfaceFaint,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke(this)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
