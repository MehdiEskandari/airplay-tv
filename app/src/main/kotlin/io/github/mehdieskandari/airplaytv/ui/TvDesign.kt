package io.github.mehdieskandari.airplaytv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.mehdieskandari.airplaytv.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mehdieskandari.airplaytv.ui.theme.TvColors

// Flat, neutral surfaces keep the AirPlay symbols and receiver state in focus.
@Composable
fun Modifier.tvBackdrop(): Modifier = background(TvColors.Background)

// pill tab bar at the top of the screen
@Composable
fun TvTabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(TvColors.Surface)
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
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, spring(0.9f, 420f), label = "tabLift")
    val bg by animateColorAsState(
        when {
            focused -> TvColors.Focus
            selected -> TvColors.SurfaceHigh
            else -> Color.Transparent
        }, label = "tabBg"
    )
    val fg = if (focused) TvColors.OnFocus else if (selected) TvColors.NavLabel else TvColors.SecondaryLabel
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

// A solid button with a clear white focus state for TV remotes.
@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, spring(0.9f, 420f), label = "btnLift")
    val bg by animateColorAsState(if (focused) TvColors.Focus else TvColors.SurfaceHigh, label = "btnBg")
    val fg = if (focused) TvColors.OnFocus else TvColors.Label
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

// Solid grouped surfaces, without glass effects or gradients.
@Composable
fun TvCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(TvColors.CardRadius.dp))
            .background(TvColors.Surface)
            .padding(20.dp),
        content = content
    )
}

enum class TvStatus { READY, STOPPED, ERROR, ATTENTION }

// A responsive receiver dashboard: the same AirPlay and mirroring symbols used in the guide.
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val showIntro = compact || maxHeight >= 560.dp
        val dot = when (status) {
            TvStatus.READY -> TvColors.Green
            TvStatus.ERROR -> TvColors.Red
            TvStatus.ATTENTION -> TvColors.Orange
            TvStatus.STOPPED -> TvColors.TertiaryLabel
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 24.dp else 48.dp)
                .padding(top = if (compact) 24.dp else 16.dp, bottom = if (compact) 44.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 22.dp else 14.dp)
        ) {
            if (showIntro) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_eyebrow), color = TvColors.SecondaryLabel,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.home_headline), color = TvColors.Label,
                        style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.home_subtitle), color = TvColors.SecondaryLabel,
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (!compact) {
                    Spacer(Modifier.width(32.dp))
                    AirPlayEmblem(Modifier.size(92.dp))
                }
            }
            TvCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (compact) {
                            AirPlayEmblem(Modifier.size(52.dp))
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.home_receiver), color = TvColors.SecondaryLabel,
                                style = MaterialTheme.typography.labelSmall)
                            Text(name, style = MaterialTheme.typography.titleLarge, color = TvColors.Label,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                                Spacer(Modifier.width(8.dp))
                                Text(statusText, style = MaterialTheme.typography.bodySmall, color = dot)
                            }
                        }
                        if (!compact) {
                            Spacer(Modifier.width(20.dp))
                            TvButton(actionLabel, onAction, actionModifier, icon = actionIcon)
                        }
                    }
                    if (!message.isNullOrBlank()) Text(message, color = TvColors.SecondaryLabel,
                        style = MaterialTheme.typography.bodyMedium)
                    if (compact) TvButton(actionLabel, onAction, actionModifier.fillMaxWidth(), icon = actionIcon)
                }
            }
            val cards = listOf(
                Triple(AirPlayIcons.Mirroring, stringResource(R.string.home_mirror_title), stringResource(R.string.home_mirror_desc, name)),
                Triple(AirPlayIcons.Video, stringResource(R.string.home_video_title), stringResource(R.string.home_video_desc, name)),
                Triple(Icons.Default.Android, stringResource(R.string.home_android_title), stringResource(R.string.home_android_desc)),
            )
            if (compact) {
                Text(stringResource(R.string.home_guide), color = TvColors.SecondaryLabel, style = MaterialTheme.typography.labelMedium)
                cards.forEachIndexed { index, c -> HowToCard(c.first, c.second, c.third, index + 1, Modifier.fillMaxWidth(), compact = true) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    cards.forEachIndexed { index, c -> HowToCard(c.first, c.second, c.third, index + 1, Modifier.weight(1f).fillMaxHeight(), compact = false) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Default.Wifi, null, tint = TvColors.SecondaryLabel, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_same_network), color = TvColors.SecondaryLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun AirPlayEmblem(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(percent = 22))
        .background(TvColors.Emblem), contentAlignment = Alignment.Center) {
        Icon(AirPlayIcons.Video, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.55f))
    }
}

@Composable
private fun HowToCard(icon: ImageVector, title: String, description: String, step: Int, modifier: Modifier, compact: Boolean) {
    TvCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = TvColors.Label, modifier = Modifier.size(28.dp))
                Spacer(Modifier.weight(1f))
                Text("0$step", color = TvColors.SecondaryLabel, style = MaterialTheme.typography.labelLarge)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = TvColors.Label)
            Text(description, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall, color = TvColors.SecondaryLabel)
        }
    }
}

// keeps a non-interactive box out of D-pad focus search
fun Modifier.noFocus(): Modifier = this.focusable(false)
