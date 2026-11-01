package io.github.mehdieskandari.airplaytv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.mehdieskandari.airplaytv.viewmodel.MainViewModel
import kotlinx.coroutines.delay

// "X wants to connect" prompt for devices that were never approved
@Composable
fun DeviceApprovalDialog(viewModel: MainViewModel) {
    val req by viewModel.pendingApproval.collectAsState()
    val r = req ?: return
    val focus = remember(r) { FocusRequester() }
    var secondsLeft by remember(r) { mutableIntStateOf(30) }
    LaunchedEffect(r) {
        runCatching { focus.requestFocus() }
        while (secondsLeft > 0) { delay(1000); secondsLeft-- }
    }
    AlertDialog(
        onDismissRequest = { viewModel.answerApproval(allow = false, remember = false) },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("New device · دستگاه جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "«${r.name}» می‌خواهد به تلویزیون وصل شود",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("\"${r.name}\" wants to connect via ${r.source}")
                if (r.model.isNotEmpty()) Text(r.model, style = MaterialTheme.typography.bodySmall)
                Text(
                    "اگر «همیشه» را بزنید، دفعه‌های بعد بدون سؤال وصل می‌شود ($secondsLeft)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.answerApproval(allow = true, remember = true) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                ) { Text("Always allow · همیشه اجازه بده") }
                OutlinedButton(
                    onClick = { viewModel.answerApproval(allow = true, remember = false) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Only this time · فقط این بار") }
                TextButton(
                    onClick = { viewModel.answerApproval(allow = false, remember = false) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Deny · رد کن") }
            }
        }
    )
}
