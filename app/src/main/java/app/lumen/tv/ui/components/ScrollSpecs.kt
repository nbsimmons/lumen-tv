package app.lumen.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

@OptIn(ExperimentalFoundationApi::class)
class PinnedBringIntoViewSpec(private val leadPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - leadPx
}

@OptIn(ExperimentalFoundationApi::class)
class EdgeBringIntoViewSpec(private val marginPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = offset - marginPx
        val trailing = offset + size - (containerSize - marginPx)
        return when {
            leading < 0f -> leading
            trailing > 0f -> trailing
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
object StaticBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
