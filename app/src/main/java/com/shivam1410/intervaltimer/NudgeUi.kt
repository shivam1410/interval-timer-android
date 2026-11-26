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

    Section("INSTAGRAM & YOUTUBE NUDGES") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // It gates the watching, not one of the two asks: with it off nothing is counted at all,
                // so naming only the sketch nudge made Just breathe look like it had been hidden away.
                Text("Watch Instagram and YouTube")
                Text("Never interrupts a YouTube video or song", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(c.enabled, { Nudges.save(c.copy(enabled = it)) })
        }
        if (!c.enabled) return@Section
        if (!usage) Permission("Needs Usage access to see how long the apps are open", "Allow") { open(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS) }
        AppRow("📸 Instagram", c.ig, c.igMin, { Nudges.save(c.copy(ig = it)) }) { Nudges.save(c.copy(igMin = it)) }
        AppRow("▶️ YouTube", c.yt, c.ytMin, { Nudges.save(c.copy(yt = it)) }) { Nudges.save(c.copy(ytMin = it)) }
        // The gap between nudges is the one number the screen used to keep to itself.
        Text(
            "Counted inside a window twice as long (5 min in any 10), and at most one nudge every 10 min per app.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
        var breathe by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Just breathe")
                Text(
                    if (c.breatheMin > 0)
                        "Once you pass ${c.breatheMin} min on one app today it asks for five minutes of breathing " +
                            "\u2014 the moment you cross, not at the next nudge. Then at most every 10 min."
                    else "Off \u00b7 the nudge stays an offer, however long the day has gone",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            Box {
                TextButton({ breathe = true }) { Text(if (c.breatheMin > 0) "after ${c.breatheMin} min \u25be" else "Off \u25be") }
                DropdownMenu(breathe, { breathe = false }) {
                    Nudges.BREATHE.forEach { m ->
                        DropdownMenuItem({ Text(if (m > 0) "$m min" else "Off") }, { Nudges.save(c.copy(breatheMin = m)); breathe = false })
                    }
                }
            }
        }
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
