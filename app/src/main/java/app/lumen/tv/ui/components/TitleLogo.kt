package app.lumen.tv.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import app.lumen.tv.ui.theme.LumenColors
import coil3.compose.AsyncImage

@Composable
fun TitleLogo(title: String, logoUrl: String?, style: TextStyle, maxHeight: Dp, maxWidth: Dp, modifier: Modifier = Modifier) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl == null || failed) {
        Text(title, style = style, color = LumenColors.OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
    } else {
        AsyncImage(
            model = logoUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onError = { failed = true },
            modifier = modifier.heightIn(max = maxHeight).widthIn(max = maxWidth),
        )
    }
}
