package io.github.mehdieskandari.airplaytv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mehdieskandari.airplaytv.ui.theme.TvColors

// soft colored light behind the content, like the tvOS home screen
fun Modifier.tvBackdrop(): Modifier = this
    .background(TvColors.Background)
    .background(
        Brush.radialGradient(
            listOf(Color(0xFF14305A).copy(alpha = 0.85f), Color.Transparent),
            center = Offset(0f, 0f), radius = 1500f
        )
    )
    .background(
        Brush.radialGradient(
            listOf(Color(0xFF3A1F5E).copy(alpha = 0.55f), Color.Transparent),
            center = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY), radius = 1300f
        )
    )

// pill tab bar at the top of the screen
@Composable
fun TvTabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            labels.forEachIndexed { i, label ->
                TvTab(label, selected = i == selected, onClick = { onSelect(i) })
            }
        }
    }
}

@Composable
private fun TvTab(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(0.62f, 420f), label = "tabLift")
    val bg by animateColorAsState(
        when {
            focused -> TvColors.Focus
            selected -> Color.White.copy(alpha = 0.18f)
            else -> Color.Transparent
        }, label = "tabBg"
    )
    val fg = if (focused) TvColors.OnFocus else if (selected) Color.White else TvColors.SecondaryLabel
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

// tvOS button: frosted pill that turns white and lifts when focused
@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(0.62f, 420f), label = "btnLift")
    val bg by animateColorAsState(if (focused) TvColors.Focus else Color.White.copy(alpha = 0.14f), label = "btnBg")
    val fg = if (focused) TvColors.OnFocus else Color.White
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .then(
                if (focused) Modifier.shadow(20.dp, CircleShape, ambientColor = Color.White.copy(0.3f), spotColor = Color.White.copy(0.4f))
                else Modifier
            )
            .clip(CircleShape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

// rounded glass card
@Composable
fun TvCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)))
            )
            .padding(22.dp),
        content = content
    )
}

enum class TvStatus { READY, STOPPED, ERROR, ATTENTION }

// idle home screen: receiver name, status and how to connect
@Composable
fun TvHome(
    name: String,
    status: TvStatus,
    statusText: String,
    message: String?,
    actionLabel: String,
    actionIcon: ImageVector,
    onAction: () -> Unit,
    actionModifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                name,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = when (status) {
                    TvStatus.READY -> TvColors.Green
                    TvStatus.ERROR -> TvColors.Red
                    TvStatus.ATTENTION -> TvColors.Orange
                    TvStatus.STOPPED -> TvColors.TertiaryLabel
                }
                Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(statusText, style = MaterialTheme.typography.titleLarge, color = TvColors.SecondaryLabel)
            }
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, color = TvColors.SecondaryLabel)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            HowToCard(
                Icons.Default.PhoneIphone, "iPhone · iPad",
                "Control Center → Screen Mirroring → $name",
                "کنترل سنتر ← اسکرین میرورینگ",
                Modifier.weight(1f)
            )
            HowToCard(
                Icons.Default.Laptop, "Mac",
                "Control Center → Screen Mirroring",
                "یا دکمهٔ AirPlay داخل ویدیو",
                Modifier.weight(1f)
            )
            HowToCard(
                Icons.Default.Android, "Android",
                "Cast from VLC, BubbleUPnP or Web Video Caster",
                "پخش با برنامه‌های DLNA",
                Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(actionLabel, onAction, actionModifier, icon = actionIcon)
        }
    }
}

@Composable
private fun HowToCard(icon: ImageVector, title: String, en: String, fa: String, modifier: Modifier) {
    TvCard(modifier.height(224.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)))),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(en, style = MaterialTheme.typography.bodyMedium, color = TvColors.SecondaryLabel, lineHeight = 20.sp)
            Text(fa, style = MaterialTheme.typography.bodyMedium, color = TvColors.TertiaryLabel)
        }
    }
}

// keeps a non-interactive box out of D-pad focus search
fun Modifier.noFocus(): Modifier = this.focusable(false)
