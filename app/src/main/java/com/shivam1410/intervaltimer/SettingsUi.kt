package com.shivam1410.intervaltimer

import androidx.compose.runtime.getValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/**
 * Opened from the profile photo: three entries (Profile, Notifications, Toggl Track), each opening
 * its own page. Back goes to the list, then home.
 */
@Composable
fun SettingsScreen(s: Settings, onBack: () -> Unit) {
    var sub by rememberSaveable { mutableStateOf("") } // "", "profile", "notifications", "toggl"
    BackHandler(sub.isNotEmpty()) { sub = "" }
    val title = when (sub) { "profile" -> "Profile"; "notifications" -> "Notifications"; "toggl" -> "Toggl Track"; else -> "Settings" }
    val cs = MaterialTheme.colorScheme

    val signedIn = Drive.profile.collectAsState().value != null
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton({ if (sub.isEmpty()) onBack() else sub = "" }) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (sub) {
                "profile" -> ProfilePage()
                "notifications" -> NotificationsPage(s)
                "toggl" -> TogglSection()
                else -> SettingsList { sub = it }
            }
            Box(Modifier.height(24.dp))
        }
        // Pinned to the bottom of the Profile page, in red: it's the one action here that takes something away.
        if (sub == "profile" && signedIn) OutlinedButton(
            { Drive.signOut() }, Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
            border = BorderStroke(1.dp, cs.error),
        ) { Text("Sign out") }
    }
}

@Composable
private fun SettingsList(open: (String) -> Unit) {
    val p by Drive.profile.collectAsState()
    val toggl by TogglSync.connected.collectAsState()
    val c by Nudges.config.collectAsState()
    val s by Timer.settings.collectAsState()
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Entry(open = { open("profile") }) {
            Avatar(p, 48.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(p?.displayName?.ifEmpty { null } ?: if (p == null) "Not signed in" else "Google account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(syncLine(p), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Entry(open = { open("notifications") }) {
            Icon(painterResource(R.drawable.ic_gong), null, Modifier.size(28.dp), tint = cs.primary)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(if (s.gong) "Gong" else null, if (s.vibrate) "vibration" else null, if (s.reminder) "daily reminder" else null, if (c.enabled) "nudges" else null)
                        .joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "All off" },
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
        Entry(open = { open("toggl") }) {
            Text("⏱", fontSize = 26.sp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Toggl Track", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (toggl) "Connected" else "Not connected", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** A tappable card row with a › at the end. */
@Composable
private fun Entry(open: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = open), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun syncLine(p: Drive.Profile?) = when {
    p == null -> "Sign in to back up history to your Google Drive"
    p.lastSync == 0L -> "Not synced yet"
    else -> "Last synced " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.lastSync))
}

/** Account, Drive sync, app updates; sign out at the bottom. */
@Composable
private fun ProfilePage() {
    val p by Drive.profile.collectAsState()
    val status by Drive.status.collectAsState()
    val update by Updater.ready.collectAsState()
    val ctx = LocalContext.current
    val activity = ctx as? MainActivity
    val cs = MaterialTheme.colorScheme

    Section("Account") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p, 56.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(p?.displayName?.ifEmpty { null } ?: if (p == null) "Not signed in" else "Google account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                p?.email?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
            }
        }
        if (p == null) Button({ activity?.signIn() }) { Text("Sign in with Google") }
    }
    if (p != null) Section("Sync") {
        Text(syncLine(p), style = MaterialTheme.typography.bodyMedium)
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Button({ Drive.sync() }) { Text("Sync now") }
    }
    Section("App updates") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Interval Timer", style = MaterialTheme.typography.titleMedium)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            val checking by Updater.checking.collectAsState()
            val downloading = Updater.progress.collectAsState().value
            when {
                update != null -> Button({ Updater.install(ctx) }) { Text("Install $update") }
                checking && downloading == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Checking…", color = cs.onSurfaceVariant)
                }
                downloading == null -> FilledTonalButton({ Updater.check(ctx, manual = true) }) { Text("Check for updates") }
            }
        }
        Updater.progress.collectAsState().value?.let { (tag, p) -> UpdateProgress(tag, p) }
    }
}

/** Gong / vibration / volume, lock screen, daily reminder, Instagram & YouTube nudges. */
@Composable
private fun NotificationsPage(s: Settings) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    fun set(f: Settings.() -> Settings) = Timer.saveSettings(s.f())

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

        NudgeSection()
}
