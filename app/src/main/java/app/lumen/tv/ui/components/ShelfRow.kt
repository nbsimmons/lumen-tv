package app.lumen.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfRow(
    title: String?,
    modifier: Modifier = Modifier,
    hint: String? = null,
    state: LazyListState = rememberLazyListState(),
    restoreFocus: FocusRequester? = null,
    entry: FocusRequester? = null,
    content: LazyListScope.() -> Unit,
) {
    val marginPx = with(LocalDensity.current) { LumenSpacing.SafeHorizontal.toPx() }
    val spec = remember(marginPx) { EdgeBringIntoViewSpec(marginPx) }
    Column(modifier) {
        if (title != null) {
            Row(Modifier.padding(start = LumenSpacing.SafeHorizontal), verticalAlignment = Alignment.Bottom) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelMedium,
                        color = LumenColors.OnSurfaceFaint,
                        modifier = Modifier.padding(start = LumenSpacing.M, bottom = 2.dp),
                    )
                }
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyRow(
                state = state,
                modifier = if (restoreFocus != null) {
                    Modifier
                        .focusProperties { onEnter = { restoreFocus.requestFocus() } }
                        .focusGroup()
                } else {
                    Modifier.focusRestorer(entry ?: FocusRequester.Default)
                },
                contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
                horizontalArrangement = Arrangement.spacedBy(LumenSpacing.RowGap),
                content = content,
            )
        }
    }
}
