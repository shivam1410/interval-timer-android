package com.shivam1410.intervaltimer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // Pixel 10 always has Material You; follow the wallpaper palette.
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

private fun clock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

@Composable
fun Root(now: Long) {
    val session by Timer.session.collectAsState()
    val settings by Timer.settings.collectAsState()
    val update by Updater.ready.collectAsState()
    val updateError by Updater.error.collectAsState()
    val plan = remember(settings) { plan(settings) }
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        update?.let { tag ->
            Banner("Update $tag downloaded", "Install") { Updater.install(ctx) }
        }
        updateError?.let { Banner(it, "OK") { Updater.error.value = null } }
        when {
            session.idle -> Setup(settings, now)
            session.done(plan) -> Done(plan, session, settings)
            else -> Running(plan, session, settings, now)
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer)
        TextButton(onClick) { Text(action) }
    }
}

// ---------- Setup ----------

@Composable
private fun Setup(s: Settings, now: Long) {
    val total = plan(s).sumOf { it.ms }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 4..11 -> "Good morning."; in 12..16 -> "Good afternoon."; else -> "Good evening." }
    val downloading by Media.downloading.collectAsState()
    val sounds by Media.sounds.collectAsState()
    fun set(f: Settings.() -> Settings) = Timer.saveSettings(s.f())

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(greeting, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Your workday", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            Text(
                "${clock(now)} — ${clock(now + total)}",
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${s.cycles} cycles · ${s.workMin} min work · ${s.breakMin} min break",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { p ->
                    FilterChip(
                        selected = s.workMin == p.work && s.breakMin == p.brk && s.cycles == p.cycles,
                        onClick = { set { copy(workMin = p.work, breakMin = p.brk, cycles = p.cycles) } },
                        label = { Text("${p.name} ${p.work}/${p.brk}") },
                    )
                }
            }

            Section("Schedule") {
                Stepper("Work", s.workMin, "min", 1..180) { v -> set { copy(workMin = v) } }
                Stepper("Break", s.breakMin, "min", 1..60) { v -> set { copy(breakMin = v) } }
                Stepper("Prepare (in break)", s.prepMin, "min", 0..10) { v -> set { copy(prepMin = v) } }
                Stepper("Cycles", s.cycles, "", 1..16) { v -> set { copy(cycles = v) } }
            }

            Section("Break activity") {
                ActivityPicker(s.activity) { id -> set { copy(activity = id) } }
            }

            Section("Long break") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 2, 3, 4).forEach { n ->
                        FilterChip(s.longEvery == n, { set { copy(longEvery = n) } }, { Text(if (n == 0) "Off" else "Every $n") })
                    }
                }
                if (s.longEvery > 0) {
                    Stepper("Long break", s.longMin, "min", 5..60) { v -> set { copy(longMin = v) } }
                    ActivityPicker(s.longActivity) { id -> set { copy(longActivity = id) } }
                }
            }

            Section("Focus music during work") {
                val loops = sounds.filter { it.loop }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(s.focusSound.isEmpty(), { set { copy(focusSound = "") } }, { Text("Off") })
                    loops.forEach { snd ->
                        FilterChip(s.focusSound == snd.id, { set { copy(focusSound = snd.id) } }, { Text(snd.name) })
                    }
                }
                if (loops.isEmpty()) {
                    Text(
                        if (downloading) "Downloading sounds from GitHub…" else "Sounds not downloaded yet",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!downloading) TextButton({ Media.sync() }) { Text("Retry download") }
                }
            }

            Section("Alerts") {
                Toggle("🔔 Gong", s.gong) { v -> set { copy(gong = v) } }
                Toggle("📳 Vibration", s.vibrate) { v -> set { copy(vibrate = v) } }
                Toggle("Wait for me before each work block", s.waitBeforeWork) { v -> set { copy(waitBeforeWork = v) } }
                Text("Volume ${s.volume}%", style = MaterialTheme.typography.bodyMedium)
                Slider(s.volume.toFloat(), { v -> set { copy(volume = v.toInt()) } }, valueRange = 10f..100f)
            }
            Text(
                "v${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth().padding(vertical = 16.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
            )
        }
        Button(
            onClick = { Timer.start() },
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(64.dp),
            shape = RoundedCornerShape(32.dp),
        ) { Text("Start workday", fontSize = 20.sp) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Stepper(label: String, value: Int, unit: String, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton({ onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−", fontSize = 22.sp) }
        Text("$value $unit".trim(), Modifier.width(64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        IconButton({ onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+", fontSize = 22.sp) }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun ActivityPicker(selected: String, onPick: (String) -> Unit) {
    ACTIVITIES.groupBy { it.group }.forEach { (group, items) ->
        Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { a -> FilterChip(selected == a.id, { onPick(a.id) }, { Text("${a.emoji} ${a.name}") }) }
        }
    }
}

// ---------- Running ----------

@Composable
private fun Running(plan: List<Phase>, s: Session, settings: Settings, now: Long) {
    val p = plan[s.index]
    val left = s.left(now)
    val isWork = p.kind == Kind.WORK
    val accent = if (isWork) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    var confirmEnd by remember { mutableStateOf(false) }
    val (workDone, breakDone) = totals(plan, s, now)
    val label = when (p.kind) {
        Kind.WORK -> "WORK"
        Kind.PREP -> if (p.long) "LONG BREAK · PREPARE" else "BREAK · PREPARE"
        Kind.ACTIVITY -> if (p.long) "LONG BREAK" else "BREAK"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = accent, letterSpacing = 3.sp, modifier = Modifier.padding(top = 16.dp))
        Box(Modifier.padding(vertical = 24.dp).size(300.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(14.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = stroke)
                drawArc(accent, -90f, 360f * left / p.ms, false, style = stroke)
            }
            if (p.kind == Kind.ACTIVITY && p.activity?.id == "breathing" && !s.paused) Breathing(accent)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt(left), fontSize = 64.sp, fontWeight = FontWeight.Light)
                Text("Cycle ${p.cycle} / ${settings.cycles}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Segments(plan, s, accent)

        if (!isWork) {
            Card(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    val a = p.activity
                    if (p.kind == Kind.PREP) {
                        Text("Prepare", style = MaterialTheme.typography.titleLarge)
                        Text("Lie down.\nPut your phone aside.\nClose your eyes.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                        a?.let { Text("Then: ${it.emoji} ${it.name}", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onTertiaryContainer) }
                    } else if (a != null) {
                        Text("${a.emoji}  ${a.name}", style = MaterialTheme.typography.titleLarge)
                        Text(a.hint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Today", "${fmtDur(workDone)} work", "${fmtDur(breakDone)} breaks", Modifier.weight(1f))
            Stat("Next", Timer.subtitle(s, now).removePrefix("Next: ").takeIf { isWork } ?: nextLabel(plan, s), "", Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                { if (s.paused) Timer.resume() else Timer.pause() },
                Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) { Text(if (s.paused) (if (s.pausedLeft == p.ms) "Start" else "Resume") else "Pause", fontSize = 18.sp) }
            FilledTonalButton({ Timer.skip() }, Modifier.weight(1f).height(56.dp)) { Text("Skip", fontSize = 18.sp) }
        }
        OutlinedButton({ confirmEnd = true }, Modifier.padding(top = 12.dp)) { Text("End workday") }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End workday?") },
            text = { Text("The schedule and alarms will be cleared.") },
            confirmButton = { TextButton({ confirmEnd = false; Timer.stop() }) { Text("End") } },
            dismissButton = { TextButton({ confirmEnd = false }) { Text("Keep going") } },
        )
    }
}

private fun nextLabel(plan: List<Phase>, s: Session): String {
    val n = plan.getOrNull(s.index + 1) ?: return "Day complete"
    return when (n.kind) {
        Kind.WORK -> "Work · ${fmtDur(n.ms)}"
        else -> "${n.activity?.emoji.orEmpty()} ${n.activity?.name.orEmpty()} · ${fmtDur(n.ms)}"
    } + " at ${clock(s.endsAt)}"
}

@Composable
private fun Stat(title: String, line1: String, line2: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(line1, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            if (line2.isNotEmpty()) Text(line2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Segments(plan: List<Phase>, s: Session, accent: Color) {
    val cycles = plan.last().cycle
    val current = plan[s.index].cycle
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(cycles) { i ->
            val color = when {
                i + 1 < current -> accent
                i + 1 == current -> accent.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(color))
        }
    }
}

/** Box breathing: inhale 4s, hold 4s, exhale 4s, hold 4s. */
@Composable
private fun Breathing(color: Color) {
    val t = rememberInfiniteTransition(label = "breath")
    val scale by t.animateFloat(
        0.55f, 0.55f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 16_000
                0.55f at 0 using LinearEasing
                0.95f at 4_000 using LinearEasing
                0.95f at 8_000 using LinearEasing
                0.55f at 12_000 using LinearEasing
            },
            RepeatMode.Restart,
        ),
        label = "scale",
    )
    Box(Modifier.size(260.dp).scale(scale).clip(CircleShape).background(color.copy(alpha = 0.15f)))
}

// ---------- Done ----------

@Composable
private fun Done(plan: List<Phase>, s: Session, settings: Settings) {
    val (work, rest) = totals(plan, s.copy(index = plan.size), s.finishedAt)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🎉", fontSize = 64.sp)
        Text("Workday complete", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
        Card(Modifier.fillMaxWidth().padding(top = 24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Line("Focused", fmtDur(work))
                Line("Breaks", fmtDur(rest))
                Line("Cycles", "${settings.cycles} / ${settings.cycles}")
                Line("Started", clock(s.startedAt))
                Line("Finished", clock(s.finishedAt))
            }
        }
        Button({ Timer.stop() }, Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp)) { Text("Done") }
    }
}

@Composable
private fun Line(k: String, v: String) {
    Row {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.SemiBold)
    }
}
