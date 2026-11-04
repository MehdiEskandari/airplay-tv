package io.github.mehdieskandari.airplaytv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mehdieskandari.airplaytv.R

// tvOS-inspired look: near-black glass surfaces, white focus, system blue accents
object TvColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceHigh = Color(0xFF2C2C2E)
    val SurfaceHighest = Color(0xFF3A3A3C)
    val Separator = Color(0xFF38383A)
    val Label = Color(0xFFFFFFFF)
    val SecondaryLabel = Color(0xFFA1A1A6)
    val TertiaryLabel = Color(0xFF6E6E73)
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
    val Focus = Color(0xFFF2F2F7)
    val OnFocus = Color(0xFF1C1C1E)
}

private val scheme = darkColorScheme(
    primary = TvColors.Blue,
    onPrimary = Color.White,
    primaryContainer = TvColors.Focus,
    onPrimaryContainer = TvColors.OnFocus,
    secondary = TvColors.SecondaryLabel,
    onSecondary = Color.Black,
    // selected chips, segmented buttons and the nav indicator turn white, like tvOS
    secondaryContainer = TvColors.Focus,
    onSecondaryContainer = TvColors.OnFocus,
    tertiary = TvColors.Green,
    background = TvColors.Background,
    onBackground = TvColors.Label,
    surface = TvColors.Background,
    onSurface = TvColors.Label,
    surfaceVariant = TvColors.Surface,
    onSurfaceVariant = TvColors.SecondaryLabel,
    surfaceContainerLowest = TvColors.Background,
    surfaceContainerLow = TvColors.Surface,
    surfaceContainer = TvColors.Surface,
    surfaceContainerHigh = TvColors.SurfaceHigh,
    surfaceContainerHighest = TvColors.SurfaceHighest,
    outline = TvColors.Separator,
    outlineVariant = TvColors.Separator,
    error = TvColors.Red,
    onError = Color.White,
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

// Inter (SIL OFL) for Latin text; Persian falls back to the system font
val InterFamily = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

private fun TextStyle.inter() = copy(fontFamily = InterFamily)

private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.inter().copy(fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.inter().copy(fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.inter().copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.inter().copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.inter().copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.inter().copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.inter().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.inter(),
    bodyMedium = base.bodyMedium.inter(),
    bodySmall = base.bodySmall.inter(),
    labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.inter().copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.inter(),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AirPlayTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
