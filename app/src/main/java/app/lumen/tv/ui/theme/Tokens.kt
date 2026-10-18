package app.lumen.tv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object LumenColors {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF16161D)
    val SurfaceRaised = Color(0xFF1F1F28)
    val Glass = Color(0x99202028)
    val GlassStroke = Color(0x1FFFFFFF)
    val NavSelected = Color(0x38FFFFFF)
    val FieldFill = Color(0x26FFFFFF)
    val FieldFillFocused = Color(0x47FFFFFF)
    val OnSurface = Color(0xFFF5F5F7)
    val OnSurfaceMuted = Color(0xFFA1A1AA)
    val OnSurfaceFaint = Color(0xFF6B6B75)
    val Accent = Color(0xFF6E8BFF)
    val FocusFill = Color(0xFFF5F5F7)
    val OnFocusFill = Color(0xFF0B0B0F)
    val Scrim = Color(0xFF0B0B0F)
    val Error = Color(0xFFFF6B6B)
    val Available = Color(0xFF5BD68A)
    val Pending = Color(0xFFF5C451)
    val Imdb = Color(0xFFF5C518)
    val ImdbText = Color(0xFF000000)
    val Tomato = Color(0xFFFA320A)
}

object LumenSpacing {
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp
    val XS = 4.dp
    val S = 8.dp
    val M = 16.dp
    val L = 24.dp
    val XL = 32.dp
    val XXL = 48.dp
    val RowGap = 20.dp
    val ShelfGap = 36.dp
}

object LumenLayout {
    const val DetailHeaderMinFraction = 0.6f
    const val HeroOverviewLines = 2
}

object LumenShapes {
    val Card = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(percent = 50)
    val Panel = RoundedCornerShape(20.dp)
}

object LumenMotion {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val Fast = 160
    const val Medium = 260
    const val Slow = 420
    const val FocusScale = 1.08f
}
