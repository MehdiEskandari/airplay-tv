package io.github.mehdieskandari.airplaytv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import io.github.mehdieskandari.airplaytv.viewmodel.MainViewModel

// "new version available" prompt; hidden while a video plays
@Composable
fun UpdateDialog(viewModel: MainViewModel, hidden: Boolean) {
    val rel by viewModel.update.collectAsState()
    val progress by viewModel.updateProgress.collectAsState()
    val r = rel ?: return
    if (hidden && progress == null) return
    val focus = remember(r) { FocusRequester() }
    LaunchedEffect(r) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = { if (progress == null) viewModel.postponeUpdate() },
        title = { Text("Update available · نسخه جدید ${r.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نسخه فعلی: ${viewModel.currentVersion}  →  ${r.version}")
                val notes = r.notes.lineSequence()
                    .map { it.trim().removePrefix("#").trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("---") }
                    .take(14).joinToString("\n")
                if (notes.isNotEmpty()) {
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                    )
                }
                progress?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading… ${(it * 100).toInt()}% · در حال دانلود")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.installUpdate() },
                enabled = progress == null,
                modifier = Modifier.focusRequester(focus)
            ) { Text("Update now · به‌روزرسانی") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.postponeUpdate() }, enabled = progress == null) {
                Text("Later · بعدا")
            }
        }
    )
}
