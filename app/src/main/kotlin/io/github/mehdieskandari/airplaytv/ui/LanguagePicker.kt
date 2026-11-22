package io.github.mehdieskandari.airplaytv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mehdieskandari.airplaytv.R
import io.github.mehdieskandari.airplaytv.ui.theme.TvColors

@Composable
fun LanguagePicker(initial: String = "fa", onConfirm: (String) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    CompositionLocalProvider(LocalContentColor provides TvColors.Label) {
    Box(Modifier.fillMaxSize().tvBackdrop().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AirPlayEmblem(Modifier.size(64.dp))
            Text(stringResource(R.string.language_welcome), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.language_intro), color = TvColors.SecondaryLabel)
            listOf(
                Triple("fa", "فارسی", "محیط کاملا فارسی"),
                Triple("en", "English", "All menus in English"),
                Triple("mul", "فارسی + English", "ترکیبی · Bilingual"),
            ).forEach { (code, title, subtitle) ->
                val active = code == selected
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (code == initial) Modifier.focusRequester(focus) else Modifier)
                        .dpadFocus(RoundedCornerShape(18.dp))
                        .background(if (active) TvColors.SurfaceHigh else TvColors.Surface, RoundedCornerShape(18.dp))
                        .border(1.dp, if (active) TvColors.Blue else Color.Transparent, RoundedCornerShape(18.dp))
                        .selectable(selected = active, role = Role.RadioButton, onClick = { selected = code }).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(subtitle, color = TvColors.SecondaryLabel, style = MaterialTheme.typography.bodySmall)
                    }
                    if (active) Icon(Icons.Default.CheckCircle, null, tint = TvColors.Blue)
                }
            }
            Button(onClick = { onConfirm(selected) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).dpadFocus()) {
                Text(when (selected) { "en" -> "Continue"; "mul" -> "ادامه · Continue"; else -> "ادامه" })
            }
            Text(stringResource(R.string.language_change_hint), style = MaterialTheme.typography.bodySmall, color = TvColors.SecondaryLabel)
        }
    }
}
}
