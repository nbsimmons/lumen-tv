package app.lumen.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.lumen.tv.ui.theme.LumenSpacing

class ShelfFocusMemory {
    val lastFocused = mutableStateMapOf<String, Int>()
    var lastShelf: String? = null
    val rows = mutableMapOf<String, Pair<Int, Int>>()
    var columnIndex = 0
    var columnOffset = 0
}

data class ShelfSpec<T>(val key: String, val title: String, val items: List<T>, val hint: String? = null)

private val ShelfLead = 50.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> PinnedShelves(
    shelves: List<ShelfSpec<T>>,
    memory: ShelfFocusMemory,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    refocus: Int = 0,
    card: @Composable (shelf: ShelfSpec<T>, item: T, modifier: Modifier, onFocused: () -> Unit) -> Unit,
) {
    val leadPx = with(LocalDensity.current) { ShelfLead.toPx() }
    val spec = remember(leadPx) { PinnedBringIntoViewSpec(leadPx) }
    val columnState = remember { LazyListState(memory.columnIndex, memory.columnOffset) }
    val rowStates = remember { mutableMapOf<String, LazyListState>() }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val navFocused = LocalNavFocused.current

    DisposableEffect(Unit) {
        onDispose {
            memory.columnIndex = columnState.firstVisibleItemIndex
            memory.columnOffset = columnState.firstVisibleItemScrollOffset
            rowStates.forEach { (key, state) -> memory.rows[key] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        }
    }
    LaunchedEffect(shelves.isNotEmpty()) {
        if (shelves.isEmpty() || navFocused) return@LaunchedEffect
        withFrameNanos { }
        val target = memory.lastShelf?.takeIf { key -> shelves.any { it.key == key } } ?: shelves.first().key
        runCatching { rowRequesters[target]?.requestFocus() }
    }

    LaunchedEffect(refocus) {
        if (refocus == 0) return@LaunchedEffect
        withFrameNanos { }
        val target = memory.lastShelf?.takeIf { key -> shelves.any { it.key == key } } ?: shelves.firstOrNull()?.key
        runCatching { rowRequesters[target]?.requestFocus() }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyColumn(
            state = columnState,
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 400.dp),
            verticalArrangement = Arrangement.spacedBy(LumenSpacing.S),
        ) {
            items(shelves, key = { it.key }) { shelf ->
                val restore = remember(shelf.key) { rowRequesters.getOrPut(shelf.key) { FocusRequester() } }
                val remembered = (memory.lastFocused[shelf.key] ?: 0).coerceAtMost((shelf.items.size - 1).coerceAtLeast(0))
                ShelfRow(
                    title = shelf.title,
                    hint = shelf.hint,
                    state = remember(shelf.key) {
                        rowStates.getOrPut(shelf.key) {
                            val (index, offset) = memory.rows[shelf.key] ?: (0 to 0)
                            LazyListState(index, offset)
                        }
                    },
                    restoreFocus = restore,
                ) {
                    itemsIndexed(shelf.items, key = { _, item -> itemKey(item) }) { index, item ->
                        card(
                            shelf,
                            item,
                            if (index == remembered) Modifier.focusRequester(restore) else Modifier,
                        ) {
                            memory.lastFocused[shelf.key] = index
                            memory.lastShelf = shelf.key
                        }
                    }
                }
            }
        }
    }
}
