package com.shivam1410.intervaltimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/** Settings: connect Toggl Track with an API token, then pick a Toggl tag (or "don't send") per activity. */
@Composable
fun TogglSection() {
    val connected by TogglSync.connected.collectAsState()
    val enabled by TogglSync.enabled.collectAsState()
    val status by TogglSync.status.collectAsState()
    val cs = MaterialTheme.colorScheme

    Section("Connection") {
        if (!connected) {
            var token by remember { mutableStateOf("") }
            Text("Work blocks and break activities start and stop Toggl timers. Find your API token at track.toggl.com/profile.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            OutlinedTextField(
                token, { token = it.trim() }, Modifier.fillMaxWidth(), label = { Text("API token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Button({ TogglSync.connect(token) }, enabled = token.isNotEmpty()) { Text("Connect") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Send sessions to Toggl", Modifier.weight(1f))
                Switch(enabled, TogglSync::setEnabled)
            }
            if (enabled) TagMapping()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(TogglSync::refreshTags) { Text("Reload tags") }
                OutlinedButton(TogglSync::disconnect) { Text("Disconnect") }
            }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = cs.error)
    }
}

/** Work + every activity that can be sent (ambient sounds can't). Library items use the tag saved on each item. */
@Composable
private fun TagMapping() {
    val tags by TogglSync.tags.collectAsState()
    val mapping by TogglSync.mapping.collectAsState()
    val rows = listOf(TOGGL_WORK to "💻 Work") + ACTIVITIES.filter(::togglable).map { it.id to "${it.emoji} ${it.name}" }
    Text("Tag per activity", style = MaterialTheme.typography.titleSmall)
    rows.forEach { (id, label) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            TagPicker(mapping[id].orEmpty(), tags) { TogglSync.setMapping(id, it) }
        }
    }
    Text("Library items send the tag you gave them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TagPicker(value: String, tags: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when {
        value == TOGGL_SKIP -> "Don't send"
        value.isEmpty() -> "No tag"
        value !in tags -> "$value (missing)" // deleted in Toggl since: sent without it
        else -> value
    }
    Box {
        TextButton({ open = true }) { Text("$label ▾", maxLines = 1) }
        DropdownMenu(open, { open = false }) {
            (listOf(TOGGL_SKIP to "Don't send", "" to "No tag") + tags.map { it to it }).forEach { (v, text) ->
                DropdownMenuItem({ Text(text) }, { onPick(v); open = false })
            }
        }
    }
}
