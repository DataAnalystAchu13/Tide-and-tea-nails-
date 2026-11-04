package com.tideandtea.owner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tideandtea.owner.R

// Royal matte purple, shell pink and the logo's gold.
val Royal = Color(0xFF3A1D5C)
val Royal2 = Color(0xFF4F2B7B)
val Plum = Color(0xFF241238)
val Mist = Color(0xFFF7F2FB)
val Lilac = Color(0xFFECE2F6)
val Line = Color(0xFFDED0EC)
val Ink = Color(0xFF261437)
val Muted = Color(0xFF6F5E82)
val Pink = Color(0xFFF4B3CB)
val PinkDeep = Color(0xFFC75A86)
val Gold = Color(0xFFC9A35E)
val Ok = Color(0xFF2F7D6F)
val Danger = Color(0xFFB3364F)
val BunnyWhite = Color(0xFFFFFAFF)

val Cormorant = FontFamily(
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_italic, FontWeight.Medium, FontStyle.Italic),
)
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private val LightColors = lightColorScheme(
    primary = Royal, onPrimary = Color.White,
    primaryContainer = Lilac, onPrimaryContainer = Royal,
    secondary = Pink, onSecondary = Plum,
    secondaryContainer = Color(0xFFFDE8F0), onSecondaryContainer = PinkDeep,
    tertiary = Gold, onTertiary = Plum,
    background = Mist, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Lilac, onSurfaceVariant = Muted,
    surfaceContainer = Color.White, surfaceContainerLow = Color(0xFFFBF8FE), surfaceContainerHigh = Color(0xFFF4EEFA),
    outline = Line, outlineVariant = Line,
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD2BDF2), onPrimary = Plum,
    primaryContainer = Royal2, onPrimaryContainer = Color(0xFFF1E7FF),
    secondary = Pink, onSecondary = Plum,
    secondaryContainer = Color(0xFF5A2B45), onSecondaryContainer = Pink,
    tertiary = Gold, onTertiary = Plum,
    background = Color(0xFF1A0F28), onBackground = Color(0xFFEDE3F7),
    surface = Color(0xFF241736), onSurface = Color(0xFFEDE3F7),
    surfaceVariant = Color(0xFF33244A), onSurfaceVariant = Color(0xFFC3B3D6),
    surfaceContainer = Color(0xFF241736), surfaceContainerLow = Color(0xFF20142F), surfaceContainerHigh = Color(0xFF2D1F42),
    outline = Color(0xFF4A3A60), outlineVariant = Color(0xFF3B2D50),
    error = Color(0xFFF08AA0),
)

private val TideType = Typography(
    displaySmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Nunito, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

private val TideShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun TideTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = TideType,
        shapes = TideShapes,
        content = content,
    )
}
