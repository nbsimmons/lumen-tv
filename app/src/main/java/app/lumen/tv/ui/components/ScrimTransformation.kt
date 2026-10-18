package app.lumen.tv.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.lumen.tv.ui.theme.LumenColors
import coil3.size.Size
import coil3.transform.Transformation

class ScrimTransformation : Transformation() {
    override val cacheKey: String = "lumen-scrim-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scale = maxOf(WIDTH.toFloat() / input.width, HEIGHT.toFloat() / input.height)
        val srcWidth = (WIDTH / scale).toInt()
        val srcHeight = (HEIGHT / scale).toInt()
        val left = (input.width - srcWidth) / 2
        val top = (input.height - srcHeight) / 2
        canvas.drawBitmap(input, Rect(left, top, left + srcWidth, top + srcHeight), Rect(0, 0, WIDTH, HEIGHT), Paint(Paint.FILTER_BITMAP_FLAG))

        val scrim = LumenColors.Scrim
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, WIDTH.toFloat(), 0f,
            intArrayOf(scrim.copy(alpha = 0.92f).toArgb(), scrim.copy(alpha = 0.55f).toArgb(), Color.Transparent.toArgb()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = LinearGradient(
            0f, 0f, 0f, HEIGHT.toFloat(),
            intArrayOf(scrim.copy(alpha = 0.5f).toArgb(), Color.Transparent.toArgb(), scrim.copy(alpha = 0.6f).toArgb(), scrim.toArgb()),
            floatArrayOf(0f, 0.25f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        return output
    }

    companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720
    }
}
