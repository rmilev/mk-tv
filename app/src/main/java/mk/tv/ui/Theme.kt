package mk.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/** Palette: deep charcoal UI with the red and gold of the North Macedonian flag as accents. */
object Palette {
    val Background = Color(0xFF0B0D12)
    val Surface = Color(0xFF151922)
    val SurfaceHigh = Color(0xFF1E2430)
    val Red = Color(0xFFD20000)
    val RedDark = Color(0xFF7A0710)
    val Gold = Color(0xFFFFD200)
    val Text = Color(0xFFF2F4F8)
    val TextDim = Color(0xFF9AA3B2)
    val Live = Color(0xFFFF3B3B)
}

/** Safe-area padding so nothing is cut off on TVs with overscan. */
object Insets {
    val Horizontal = 48.dp
    val Top = 27.dp
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MkTvTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Palette.Gold,
        onPrimary = Color.Black,
        secondary = Palette.Red,
        background = Palette.Background,
        onBackground = Palette.Text,
        surface = Palette.Surface,
        onSurface = Palette.Text,
        surfaceVariant = Palette.SurfaceHigh,
        onSurfaceVariant = Palette.TextDim,
        border = Palette.Gold,
    )
    val typography = Typography(
        displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.Text),
        headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text),
        titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text),
        titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Palette.Text),
        bodyMedium = TextStyle(fontSize = 15.sp, color = Palette.TextDim),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
