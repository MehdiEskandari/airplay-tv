package io.github.mehdieskandari.airplaytv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.LocalContentColor
import io.github.mehdieskandari.airplaytv.Prefs
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mehdieskandari.airplaytv.R

// Teli dark colors are sampled from the supplied Telegram reference.
private data class TvPalette(
    val IsDark: Boolean = true,
    val Background: Color = Color(0xFF000000),
    val Surface: Color = Color(0xFF1C1C1E),
    val SurfaceHigh: Color = Color(0xFF2C2C2E),
    val SurfaceHighest: Color = Color(0xFF3A3A3C),
    val Separator: Color = Color(0xFF38383A),
    val Label: Color = Color.White,
    val SecondaryLabel: Color = Color(0xFFABABAF),
    val TertiaryLabel: Color = Color(0xFF6E6E73),
    val Blue: Color = Color(0xFF64A8FF),
    val Green: Color = Color(0xFF30D158),
    val Red: Color = Color(0xFFFF453A),
    val Orange: Color = Color(0xFFFF9F0A),
    val Focus: Color = Color(0xFFF2F2F7),
    val OnFocus: Color = Color(0xFF1C1C1E),
    val OnBlue: Color = Color.Black,
    val Emblem: Color = Color.Black,
    val NavIndicator: Color = Color(0xFFF2F2F7),
    val NavForeground: Color = Color(0xFF1C1C1E),
    val NavLabel: Color = Color.White,
    val CardRadius: Int = 22,
)

private val solidDark = TvPalette()
private val teliDark = TvPalette(
    Background = Color(0xFF101820),
    Surface = Color(0xFF1B252F),
    SurfaceHigh = Color(0xFF21303F),
    SurfaceHighest = Color(0xFF2B3F50),
    Separator = Color(0xFF21303F),
    SecondaryLabel = Color(0xFFB1C3D4),
    TertiaryLabel = Color(0xFF8196A9),
    Blue = Color(0xFF30A9FB),
    Green = Color(0xFF4ACB87),
    Focus = Color(0xFF027FD1),
    OnFocus = Color.White,
    OnBlue = Color.White,
    Emblem = Color.Black,
    NavIndicator = Color.Transparent,
    NavForeground = Color(0xFF30A9FB),
    NavLabel = Color(0xFF30A9FB),
    CardRadius = 14,
)
private val teliLight = teliDark.copy(
    IsDark = false,
    Background = Color(0xFFF0F3F6),
    Surface = Color.White,
    SurfaceHigh = Color(0xFFE3EFF8),
    SurfaceHighest = Color(0xFFD7E8F4),
    Separator = Color(0xFFDAE3EB),
    Label = Color(0xFF17212B),
    SecondaryLabel = Color(0xFF566D80),
    TertiaryLabel = Color(0xFF6A8093),
    Blue = Color(0xFF087EBF),
    Green = Color(0xFF218353),
    Red = Color(0xFFC83532),
    Orange = Color(0xFF9B6500),
    Focus = Color(0xFF027FD1),
    Emblem = Color.Black,
    NavForeground = Color(0xFF087EBF),
    NavLabel = Color(0xFF087EBF),
)
private val LocalTvPalette = staticCompositionLocalOf { teliDark }

object TvColors {
    val Background: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Background
    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Surface
    val SurfaceHigh: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.SurfaceHigh
    val SurfaceHighest: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.SurfaceHighest
    val Separator: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Separator
    val Label: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Label
    val SecondaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.SecondaryLabel
    val TertiaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.TertiaryLabel
    val Blue: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Blue
    val Green: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Green
    val Red: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Red
    val Orange: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Orange
    val Focus: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Focus
    val OnFocus: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.OnFocus
    val OnBlue: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.OnBlue
    val Emblem: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.Emblem
    val NavIndicator: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.NavIndicator
    val NavForeground: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.NavForeground
    val NavLabel: Color
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.NavLabel
    val CardRadius: Int
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.CardRadius
    val IsDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalTvPalette.current.IsDark
}

private fun colorScheme(p: TvPalette) = (if (p.IsDark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.Blue,
    onPrimary = p.OnBlue,
    primaryContainer = p.SurfaceHigh,
    onPrimaryContainer = p.Label,
    secondary = p.Blue,
    onSecondary = p.OnBlue,
    secondaryContainer = p.SurfaceHigh,
    onSecondaryContainer = p.Label,
    tertiary = p.Green,
    background = p.Background,
    onBackground = p.Label,
    surface = p.Surface,
    onSurface = p.Label,
    surfaceVariant = p.SurfaceHigh,
    onSurfaceVariant = p.SecondaryLabel,
    surfaceContainerLowest = p.Background,
    surfaceContainerLow = p.Surface,
    surfaceContainer = p.Surface,
    surfaceContainerHigh = p.SurfaceHigh,
    surfaceContainerHighest = p.SurfaceHighest,
    outline = p.Separator,
    outlineVariant = p.Separator,
    surfaceTint = Color.Transparent,
    error = p.Red,
    onError = Color.White,
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

// Bundled Inter for English and Vazirmatn for Persian and bilingual menus.
val InterFamily = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

private val PersianFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

// Persian joins and diacritics need more leading, without Latin letter spacing.
private fun typography(persian: Boolean): Typography {
    fun text(size: Int, leading: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = if (persian) PersianFamily else InterFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (if (persian) leading else (leading - 2).coerceAtLeast(size)).sp,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
    )
    return Typography(
        displayLarge = text(48, 62, FontWeight.SemiBold),
        displayMedium = text(40, 54, FontWeight.SemiBold),
        displaySmall = text(34, 48, FontWeight.SemiBold),
        headlineLarge = text(30, 44, FontWeight.SemiBold),
        headlineMedium = text(28, 40, FontWeight.SemiBold),
        headlineSmall = text(24, 36, FontWeight.SemiBold),
        titleLarge = text(22, 34, FontWeight.SemiBold),
        titleMedium = text(17, 28, FontWeight.SemiBold),
        titleSmall = text(15, 24, FontWeight.Medium),
        bodyLarge = text(17, 30),
        bodyMedium = text(15, 26),
        bodySmall = text(13, 22),
        labelLarge = text(15, 24, FontWeight.Medium),
        labelMedium = text(13, 22, FontWeight.Medium),
        labelSmall = text(12, 20, FontWeight.Medium),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AirPlayTheme(
    appearance: String = Prefs.THEME_TELI_DARK,
    content: @Composable () -> Unit
) {
    val palette = when (appearance) {
        Prefs.THEME_TELI_LIGHT -> teliLight
        Prefs.THEME_SOLID_DARK -> solidDark
        else -> teliDark
    }
    val persian = androidx.core.os.ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language != "en"
    CompositionLocalProvider(LocalTvPalette provides palette, LocalContentColor provides palette.Label) {
        MaterialTheme(
            colorScheme = colorScheme(palette),
            typography = typography(persian),
            shapes = if (appearance == Prefs.THEME_SOLID_DARK) shapes else Shapes(
                extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(14.dp),
                extraLarge = RoundedCornerShape(18.dp)
            ),
            content = content
        )
    }
}
