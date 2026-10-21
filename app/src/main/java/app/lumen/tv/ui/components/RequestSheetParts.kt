package app.lumen.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun SheetHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LumenColors.OnSurfaceFaint,
        modifier = Modifier.padding(start = LumenSpacing.M, top = LumenSpacing.M, bottom = LumenSpacing.XS),
    )
}

@Composable
fun SheetRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    detailColor: Color = LumenColors.OnSurfaceFaint,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(LumenShapes.Card),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = LumenColors.OnSurface,
            focusedContainerColor = LumenColors.FocusFill,
            focusedContentColor = LumenColors.OnFocusFill,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = LumenColors.OnSurfaceFaint,
        ),
    ) {
        Row(Modifier.padding(horizontal = LumenSpacing.M, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(26.dp)) {
                if (checked) Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = detailColor) }
        }
    }
}
