package app.lumen.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

private val LumenFont = FontFamily.SansSerif

private val LumenTypography = Typography(
    displayLarge = TextStyle(fontFamily = LumenFont, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = LumenFont, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontFamily = LumenFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = LumenFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = LumenFont, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = LumenFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = LumenFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = LumenFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = LumenFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
)

private val LumenColorScheme = darkColorScheme(
    primary = LumenColors.Accent,
    onPrimary = LumenColors.OnSurface,
    background = LumenColors.Background,
    onBackground = LumenColors.OnSurface,
    surface = LumenColors.Surface,
    onSurface = LumenColors.OnSurface,
    surfaceVariant = LumenColors.SurfaceRaised,
    onSurfaceVariant = LumenColors.OnSurfaceMuted,
    inverseSurface = LumenColors.FocusFill,
    inverseOnSurface = LumenColors.OnFocusFill,
    error = LumenColors.Error,
)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LumenColorScheme, typography = LumenTypography, content = content)
}
