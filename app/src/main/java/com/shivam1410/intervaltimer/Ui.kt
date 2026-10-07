package com.shivam1410.intervaltimer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    var showHistory by rememberSaveable { mutableStateOf(false) }
    BackHandler(showHistory) { showHistory = false }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        update?.let { tag ->
            Banner("Update $tag downloaded", "Install") { Updater.install(ctx) }
        }
        updateError?.let { Banner(it, "OK") { Updater.error.value = null } }
        when {
            showHistory -> HistoryScreen { showHistory = false }
            session.idle -> Setup(settings, now) { showHistory = true }
            session.done(plan) -> Done(session, settings) { showHistory = true }
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
        Text(text, Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
        // Solid tertiary button: the default primary-coloured text button was unreadable on the dark banner.
        Button(
            onClick, Modifier.padding(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
        ) { Text(action) }
    }
}

// ---------- Setup ----------

@Composable
private fun Setup(s: Settings, now: Long, onHistory: () -> Unit) {
    val total = plan(s).sumOf { it.ms }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val first = Drive.profile.collectAsState().value?.displayName?.substringBefore(' ').orEmpty()
    val greeting = when (hour) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" } +
        if (first.isNotEmpty()) ", $first" else ""
    fun set(f: Settings.() -> Settings) = Timer.saveSettings(s.f())

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(greeting, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistoryPill(onHistory)
                Spacer(Modifier.width(8.dp))
                ProfileChip()
            }
            Text("Your workday", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            Text(
                "${clock(now)} — ${clock(now + total)}",
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${s.cycles} ${if (s.cycles == 1) "cycle" else "cycles"} · ${s.workMin} min work · ${s.breakMin} min break",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // One row, equal widths: name above the bold ratio so all three fit.
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { p ->
                    FilterChip(
                        selected = s.workMin == p.work && s.breakMin == p.brk && s.cycles == p.cycles,
                        onClick = { set { copy(workMin = p.work, breakMin = p.brk, cycles = p.cycles) } },
                        label = {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(p.name, style = MaterialTheme.typography.labelMedium)
                                Text("${p.work}:${p.brk}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Section("Schedule") {
                Stepper("Work", s.workMin, "min", 1..180) { v -> set { copy(workMin = v) } }
                Stepper("Break", s.breakMin, "min", 1..60) { v -> set { copy(breakMin = v) } }
                Stepper("Prepare (in break)", s.prepMin, "min", 0..10) { v -> set { copy(prepMin = v) } }
                Stepper("Cycles", s.cycles, "", 1..16) { v -> set { copy(cycles = v) } }
            }

            Section("Alerts") {
                Toggle(R.drawable.ic_gong, "Gong", s.gong) { v -> set { copy(gong = v) } }
                Toggle(R.drawable.ic_vibrate, "Vibration", s.vibrate) { v -> set { copy(vibrate = v) } }
                Toggle(R.drawable.ic_hourglass, "Wait before each work block", s.waitBeforeWork) { v -> set { copy(waitBeforeWork = v) } }
                Text("Volume ${s.volume}%", style = MaterialTheme.typography.bodyMedium)
                Slider(s.volume.toFloat(), { v -> set { copy(volume = v.toInt()) } }, valueRange = 10f..100f)
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = { Timer.start() },
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(64.dp),
            shape = RoundedCornerShape(32.dp),
        ) { Text("Start workday", fontSize = 20.sp) }
    }
}

/** Header shortcut to History: chart icon plus the current streak, next to the profile photo. */
@Composable
private fun HistoryPill(onClick: () -> Unit) {
    val days by History.days.collectAsState()
    val streak = streaks(days, History.today()).first
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(cs.secondaryContainer).clickable(onClickLabel = "History", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_chart), "History", Modifier.size(20.dp), tint = cs.onSecondaryContainer)
        if (streak > 0) Text(" 🔥$streak", style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
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
private fun Toggle(icon: Int, label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f).padding(start = 14.dp))
        Switch(value, onChange)
    }
}

/** Lives on the tertiary break card, so chips are solid fills rather than outlines on a tinted card. */
@Composable
private fun ActivityPicker(selected: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = cs.surface,
        labelColor = cs.onSurface,
        selectedContainerColor = cs.tertiary,
        selectedLabelColor = cs.onTertiary,
    )
    ACTIVITIES.groupBy { it.group }.forEach { (group, items) ->
        Text(group, style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { a ->
                val on = selected == a.id
                FilterChip(
                    on, { onPick(a.id) }, { Text("${a.emoji} ${a.name}") },
                    colors = colors,
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on, borderColor = Color.Transparent),
                )
            }
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
    val chosen = activity(settings.activity)
    val phaseMs = s.phaseMs.takeIf { it > 0 } ?: p.ms
    val brk = if (s.bonus > 0) "LONG BREAK · +${extraMin(s)} MIN" else "BREAK"
    val label = when (p.kind) {
        Kind.WORK -> "WORK"
        Kind.PREP -> "$brk · PREPARE"
        Kind.ACTIVITY -> brk
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
                drawArc(accent, -90f, 360f * left / phaseMs, false, style = stroke)
            }
            if (p.kind == Kind.ACTIVITY && chosen.id == "breathing" && !s.paused) Breathing(accent)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt(left), fontSize = 64.sp, fontWeight = FontWeight.Light)
                Text("Cycle ${p.cycle} / ${settings.cycles}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Segments(plan, s, accent)

        if (isWork) FocusMusic(settings.focusSound) else BreakChoices(p, s, chosen)

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stat("Today", "${fmtDur(workDone)} work", "${fmtDur(breakDone)} breaks", Modifier.weight(1f).fillMaxHeight())
            Stat("Next", Timer.subtitle(s, now).removePrefix("Next: ").takeIf { isWork } ?: nextLabel(plan, s), "", Modifier.weight(1f).fillMaxHeight())
        }

        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                { if (s.paused) Timer.resume() else Timer.pause() },
                Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) { Text(if (s.paused) (if (s.pausedLeft == phaseMs) "Start" else "Resume") else "Pause", fontSize = 18.sp) }
            FilledTonalButton(
                { Timer.skip() }, Modifier.weight(1f).height(56.dp),
                colors = if (isWork) ButtonDefaults.filledTonalButtonColors()
                else ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) { Text("Skip", fontSize = 18.sp) }
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
        Kind.PREP -> "Break · ${fmtDur(plan.drop(s.index + 1).takeWhile { it.kind != Kind.WORK }.sumOf { it.ms })}"
        Kind.ACTIVITY -> activity(Timer.settings.value.activity).let { "${it.emoji} ${it.name} · ${fmtDur(n.ms + s.bonus)}" }
    } + " at ${clock(s.endsAt)}"
}

/** Chosen during the break itself: what to do, and whether to make it a long one. */
@Composable
private fun BreakChoices(p: Phase, s: Session, chosen: Activity) {
    var changing by remember(s.index) { mutableStateOf(p.kind == Kind.PREP) }
    Card(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.kind == Kind.PREP) {
                Text("Prepare", style = MaterialTheme.typography.titleLarge)
                Text("Lie down.\nPut your phone aside.\nClose your eyes.", style = MaterialTheme.typography.bodyLarge)
                Text("Then: ${chosen.emoji} ${chosen.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("${chosen.emoji}  ${chosen.name}", style = MaterialTheme.typography.titleLarge)
                Text(chosen.hint, style = MaterialTheme.typography.bodyLarge)
            }
            if (changing) ActivityPicker(chosen.id) { Timer.chooseActivity(it) }
            // Same solid surface style as the activity chips; fixed height keeps the pair even.
            val cs = MaterialTheme.colorScheme
            val colors = ButtonDefaults.buttonColors(containerColor = cs.surface, contentColor = cs.onSurface)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!changing) Button({ changing = true }, Modifier.height(44.dp), colors = colors) { Text("Change activity") }
                Button({ Timer.extendBreak() }, Modifier.height(44.dp), colors = colors) { Text("+10 min") }
            }
        }
    }
}

@Composable
private fun FocusMusic(selected: String) {
    val sounds by Media.sounds.collectAsState()
    val downloading by Media.downloading.collectAsState()
    val loops = sounds.filter { it.loop }
    Card(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🎵 Focus music", style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected.isEmpty(), { Timer.chooseFocus("") }, { Text("Off") })
                loops.forEach { snd -> FilterChip(selected == snd.id, { Timer.chooseFocus(snd.id) }, { Text(snd.name) }) }
            }
            if (loops.isEmpty()) {
                Text(
                    if (downloading) "Downloading sounds from GitHub…" else "Sounds not downloaded yet",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!downloading) TextButton({ Media.sync() }) { Text("Retry download") }
            }
        }
    }
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
    val ms by t.animateFloat(0f, 16_000f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart), label = "ms")
    val step = (ms / 4_000).toInt().coerceIn(0, 3)
    val f = ms % 4_000 / 4_000
    val scale = when (step) { 0 -> 0.55f + 0.4f * f; 1 -> 0.95f; 2 -> 0.95f - 0.4f * f; else -> 0.55f }
    Box(Modifier.size(260.dp).scale(scale).clip(CircleShape).background(color.copy(alpha = 0.15f)))
    Text(
        listOf("Inhale", "Hold", "Exhale", "Hold")[step],
        Modifier.padding(top = 150.dp), color = color, style = MaterialTheme.typography.titleMedium,
    )
}

// ---------- Done ----------

@Composable
private fun Done(s: Session, settings: Settings, onHistory: () -> Unit) {
    val work = s.workMs
    val rest = s.breakMs
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
                Line("Cycles", "${s.cycles} / ${settings.cycles}")
                Line("Started", clock(s.startedAt))
                Line("Finished", clock(s.finishedAt))
            }
        }
        Button({ Timer.stop() }, Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp)) { Text("Done") }
        OutlinedButton(onHistory, Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp)) { Text("View history") }
    }
}

@Composable
private fun Line(k: String, v: String) {
    Row {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.SemiBold)
    }
}
