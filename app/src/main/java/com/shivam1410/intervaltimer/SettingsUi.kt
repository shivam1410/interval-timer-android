package com.shivam1410.intervaltimer

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** Opened from the profile photo: account + Drive sync, alerts, workday behaviour, app version. */
@Composable
fun SettingsScreen(s: Settings, onBack: () -> Unit) {
    val p by Drive.profile.collectAsState()
    val status by Drive.status.collectAsState()
    val update by Updater.ready.collectAsState()
    val ctx = LocalContext.current
    val activity = ctx as? MainActivity
    val cs = MaterialTheme.colorScheme
    fun set(f: Settings.() -> Settings) = Timer.saveSettings(s.f())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }

        Section("Account") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p, 56.dp)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(p?.displayName?.ifEmpty { null } ?: if (p == null) "Not signed in" else "Google account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val prof = p
                    val sub = when {
                        prof == null -> "Sign in to back up history to your Google Drive"
                        prof.lastSync == 0L -> "Not synced yet"
                        else -> "Last synced " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(prof.lastSync))
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p == null) {
                    Button({ activity?.signIn() }) { Text("Sign in with Google") }
                } else {
                    Button({ Drive.sync() }) { Text("Sync now") }
                    OutlinedButton({ Drive.signOut() }) { Text("Sign out") }
                }
            }
        }

        Section("Alerts") {
            Toggle(R.drawable.ic_gong, "Gong", s.gong) { v -> set { copy(gong = v) } }
            Toggle(R.drawable.ic_vibrate, "Vibration", s.vibrate) { v -> set { copy(vibrate = v) } }
            Text("Volume ${s.volume}%", style = MaterialTheme.typography.bodyMedium)
            Slider(s.volume.toFloat(), { v -> set { copy(volume = v.toInt()) } }, valueRange = 10f..100f)
        }

        Section("Lock screen") {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            val allowed = nm.canUseFullScreenIntent()
            Toggle(R.drawable.ic_lock, "Show phase changes on the lock screen", s.lockPage) { v -> set { copy(lockPage = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !s.lockPage -> "Off · phase changes only show as a normal alert"
                        allowed -> "Black-and-white, display only · screen on ~12 s per change"
                        else -> "Android needs your OK for full-screen alerts"
                    },
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                if (s.lockPage && !allowed) FilledTonalButton({
                    ctx.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:${ctx.packageName}"))
                    )
                }) { Text("Allow") }
            }
        }

        Section("Daily reminder") {
            val p0 = PRESETS[0]
            Toggle(R.drawable.ic_gong, "Remind me to start the ${p0.cycles * (p0.work + p0.brk) / 60}-hour ${p0.work}:${p0.brk}", s.reminder) { v -> set { copy(reminder = v) } }
            if (s.reminder) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every day at", Modifier.weight(1f))
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, s.reminderMin / 60); set(java.util.Calendar.MINUTE, s.reminderMin % 60)
                }
                FilledTonalButton({
                    android.app.TimePickerDialog(
                        ctx, { _, h, m -> set { copy(reminderMin = h * 60 + m) } },
                        s.reminderMin / 60, s.reminderMin % 60, android.text.format.DateFormat.is24HourFormat(ctx),
                    ).show()
                }) { Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(cal.time)) }
            }
        }

        Section("About") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Interval Timer", style = MaterialTheme.typography.titleMedium)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (update != null) Button({ Updater.install(ctx) }) { Text("Install $update") }
                else FilledTonalButton({ Updater.check(ctx) }) { Text("Check for updates") }
            }
        }
        Box(Modifier.height(24.dp))
    }
}
