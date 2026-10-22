package app.lumen.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.ui.theme.LumenColors

@Composable
fun StudioLink(name: String, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (onOpen == null) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceFaint, modifier = modifier)
    } else {
        PillButton(text = name, onClick = onOpen, modifier = modifier)
    }
}
