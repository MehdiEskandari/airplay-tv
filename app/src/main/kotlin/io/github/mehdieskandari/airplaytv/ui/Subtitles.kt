package io.github.mehdieskandari.airplaytv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mehdieskandari.airplaytv.Prefs
import io.github.mehdieskandari.airplaytv.R
import io.github.mehdieskandari.airplaytv.viewmodel.MainViewModel

// bundled Persian fonts (SIL Open Font License, see fonts-licenses/)
object SubtitleFonts {
    private val families: Map<String, FontFamily> = mapOf(
        "system" to FontFamily.Default,
        "vazirmatn" to FontFamily(
            Font(R.font.vazirmatn_regular, FontWeight.Normal),
            Font(R.font.vazirmatn_bold, FontWeight.Bold)
        ),
        "sahel" to FontFamily(
            Font(R.font.sahel_regular, FontWeight.Normal),
            Font(R.font.sahel_bold, FontWeight.Bold)
        ),
        "shabnam" to FontFamily(
            Font(R.font.shabnam_regular, FontWeight.Normal),
            Font(R.font.shabnam_bold, FontWeight.Bold)
        ),
        "samim" to FontFamily(
            Font(R.font.samim_regular, FontWeight.Normal),
            Font(R.font.samim_bold, FontWeight.Bold)
        ),
        "estedad" to FontFamily(
            Font(R.font.estedad_regular, FontWeight.Normal),
            Font(R.font.estedad_bold, FontWeight.Bold)
        ),
        "lalezar" to FontFamily(Font(R.font.lalezar_regular, FontWeight.Normal)),
    )

    val labels = mapOf(
        "system" to "پیش‌فرض",
        "vazirmatn" to "وزیرمتن",
        "sahel" to "ساحل",
        "shabnam" to "شبنم",
        "samim" to "صمیم",
        "estedad" to "استعداد",
        "lalezar" to "لاله‌زار",
    )

    fun family(key: String): FontFamily = families[key] ?: FontFamily.Default
}

data class SubtitleStyle(
    val enabled: Boolean,
    val font: String,
    val bold: Boolean,
    val sizeSp: Int,
    val color: Color,
    val edge: String,
    val bgPercent: Int,
    val marginDp: Int,
)

@Composable
fun rememberSubtitleStyle(viewModel: MainViewModel): SubtitleStyle {
    val enabled by viewModel.pref(Prefs.SUB_ENABLED).collectAsState()
    val font by viewModel.pref(Prefs.SUB_FONT).collectAsState()
    val bold by viewModel.pref(Prefs.SUB_BOLD).collectAsState()
    val size by viewModel.pref(Prefs.SUB_SIZE).collectAsState()
    val color by viewModel.pref(Prefs.SUB_COLOR).collectAsState()
    val edge by viewModel.pref(Prefs.SUB_EDGE).collectAsState()
    val bg by viewModel.pref(Prefs.SUB_BG).collectAsState()
    val margin by viewModel.pref(Prefs.SUB_MARGIN).collectAsState()
    return SubtitleStyle(enabled, font, bold, size, parseHexColor(color), edge, bg, margin)
}

fun parseHexColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.White)

// the styled subtitle text itself; shared by the player overlay and the settings preview
@Composable
fun SubtitleText(text: String, style: SubtitleStyle, modifier: Modifier = Modifier) {
    val family = SubtitleFonts.family(style.font)
    val weight = if (style.bold) FontWeight.Bold else FontWeight.Normal
    val base = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = style.sizeSp.sp,
        lineHeight = (style.sizeSp * 1.4f).sp,
        textAlign = TextAlign.Center,
    )
    val bgAlpha = style.bgPercent / 100f
    Box(
        modifier = modifier
            .then(
                if (bgAlpha > 0f) Modifier.background(Color.Black.copy(alpha = bgAlpha), RoundedCornerShape(6.dp))
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (style.edge == "outline") {
            // draw a black stroke underneath, then the fill on top
            Text(
                text = text,
                style = base.copy(
                    color = Color.Black,
                    drawStyle = Stroke(width = style.sizeSp * 0.22f, join = StrokeJoin.Round)
                )
            )
        }
        Text(
            text = text,
            style = base.copy(
                color = style.color,
                shadow = if (style.edge == "shadow") Shadow(Color.Black, Offset(3f, 3f), blurRadius = 8f) else null
            )
        )
    }
}

@Composable
fun SubtitleOverlay(text: String, style: SubtitleStyle, modifier: Modifier = Modifier) {
    if (text.isBlank() || !style.enabled) return
    // a phone is held close: same setting, smaller text
    @Suppress("NAME_SHADOWING")
    val style = if (isTv()) style else style.copy(sizeSp = (style.sizeSp * 0.65f).toInt().coerceAtLeast(12))
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SubtitleText(
            text = text,
            style = style,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = style.marginDp.dp)
        )
    }
}
