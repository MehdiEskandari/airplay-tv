package io.github.mehdieskandari.airplaytv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// large, high-contrast subtitles for a TV across the room; RTL (Persian) lays out on its own
@Composable
fun SubtitleOverlay(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(Color.Black, Offset(2f, 2f), blurRadius = 6f)
            ),
            modifier = Modifier
                .padding(start = 48.dp, end = 48.dp, bottom = 40.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )
    }
}
