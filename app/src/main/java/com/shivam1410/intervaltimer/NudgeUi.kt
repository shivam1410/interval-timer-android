package com.shivam1410.intervaltimer

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Settings: nudge after N minutes on Instagram / YouTube, plus the two Android permissions it needs. */
@Composable
fun NudgeSection() {
    val c by Nudges.config.collectAsState()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // Re-read while visible: the permissions are granted in Android's own settings screens.
    val access by produceState(Nudges.usageAccess() to Nudges.mediaAccess()) {
        while (true) {
            delay(2000)
            value = Nudges.usageAccess() to Nudges.mediaAccess()
        }
    }
    val (usage, media) = access
    fun open(action: String) = ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    Section("Instagram & YouTube nudges") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nudge me to sketch or relax instead")
                Text("Never while a YouTube video or song is playing", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(c.enabled, { Nudges.save(c.copy(enabled = it)) })
        }
        if (!c.enabled) return@Section
        if (!usage) Permission("Needs Usage access to see how long the apps are open", "Allow") { open(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS) }
        AppRow("📸 Instagram", c.ig, c.igMin, { Nudges.save(c.copy(ig = it)) }) { Nudges.save(c.copy(igMin = it)) }
        AppRow("▶️ YouTube", c.yt, c.ytMin, { Nudges.save(c.copy(yt = it)) }) { Nudges.save(c.copy(ytMin = it)) }
        if (c.yt && !media) Permission(
            "To tell Shorts from a video you're watching, allow Notification access (it only checks YouTube's player). " +
                "Greyed out? App info › ⋮ › Allow restricted settings first.",
            "Allow",
        ) { open(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
    }
}

@Composable
private fun Permission(text: String, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        FilledTonalButton(onClick) { Text(button) }
    }
}

@Composable
private fun AppRow(label: String, on: Boolean, minutes: Int, onToggle: (Boolean) -> Unit, onMinutes: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (on) Box {
            TextButton({ open = true }) { Text("after $minutes min ▾") }
            DropdownMenu(open, { open = false }) {
                Nudges.LIMITS.forEach { m -> DropdownMenuItem({ Text("$m min") }, { onMinutes(m); open = false }) }
            }
        }
        Switch(on, onToggle)
    }
}
