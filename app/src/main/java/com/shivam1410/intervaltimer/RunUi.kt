package com.shivam1410.intervaltimer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The screens of a timer that is running: the work and break screens, the break picker, and the
// page at the end of the day. Split out of Ui.kt, which had grown past twice the size this
// project asks files to stay under.

/**
 * Prepare offers two: your usual, and one you haven't touched in a while. Everything else is behind
 * "Something else…", because a wall of twenty tiles is the last thing a tired break needs.
 */
@Composable
private fun TwoChoices(usual: Activity, onMore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = History.today()
    val last = remember(History.timelines.value) { lastUsed(History.timelines.value) }
    val fresh = remember(last, usual.id) { staleChoice(QUICK, last, today, exclude = usual.id)?.let(::activity) }
    val breakMin = Timer.settings.value.breakMin
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pick one", style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceCard("Your usual", usual, "${usual.minutes ?: breakMin} min", Modifier.weight(1f)) { Timer.chooseActivity(usual.id) }
            fresh?.let { f ->
                val days = daysSinceUsed(last, f.id, today)
                ChoiceCard(
                    days?.let { "Not for $it days" } ?: "Never tried", f, "${f.minutes ?: breakMin} min", Modifier.weight(1f),
                ) { Timer.chooseActivity(f.id) }
            }
        }
        TextButton(onMore, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Something else…", color = cs.onTertiaryContainer)
        }
    }
}

@Composable
private fun ChoiceCard(flag: String, a: Activity, length: String, modifier: Modifier, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(cs.surface)
            .clickable(onClickLabel = "Choose ${a.name}", onClick = onPick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(a.emoji, fontSize = 22.sp)
        Text(flag, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Text(length, style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Break picker: same tiles as the quick timers; solid surface tiles on the tinted card, chosen one filled. */
@Composable
private fun ActivityPicker(selected: String, breakMin: Int, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    ACTIVITIES.groupBy { it.group }.forEach { (group, items) ->
        Text(group, style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer, modifier = Modifier.padding(top = 4.dp))
        TileGrid(items) { a ->
            val on = selected == a.id
            ActivityTile(
                a, "${a.minutes ?: breakMin} min", // fixed 20-min activities, else the regular break length
                if (on) cs.tertiary else cs.surface, if (on) cs.onTertiary else cs.onSurface,
                if (on) cs.onTertiary else cs.onSurfaceVariant, "Choose ${a.name}",
            ) { onPick(a.id) }
        }
    }
    // Your library, folded away by default (open when one of its items is the current pick).
    val lib = Library.items.collectAsState().value.filterNot { it.deleted }.map { it.toActivity() }
    if (lib.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(selected.startsWith("lib:")) }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = if (open) "Hide my library" else "Show my library") { open = !open }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("My library · ${lib.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer)
        Text(if (open) "▴" else "▾", color = cs.onTertiaryContainer)
    }
    if (open) TileGrid(lib) { a ->
        val on = selected == a.id
        ActivityTile(
            a, "$breakMin min", if (on) cs.tertiary else cs.surface, if (on) cs.onTertiary else cs.onSurface,
            if (on) cs.onTertiary else cs.onSurfaceVariant, "Choose ${a.name}",
        ) { onPick(a.id) }
    }
}

// ---------- Running ----------

@Composable
internal fun Running(plan: List<Phase>, s: Session, settings: Settings, now: Long, onRoutine: (String) -> Unit = {}) {
    val p = plan[s.index]
    val left = s.left(now)
    val quick = s.quick != null
    // A routine's own timer: its steps are a page of their own, so offer the way back into them.
    val routine = s.quick?.takeIf { it.startsWith("routine:") }?.removePrefix("routine:")
    val chosen = activity(s.quick ?: settings.activity)
    val over = overtimeMs(plan, s, now) // past 0 on work / a break activity: counting up
    if (p.kind == Kind.ACTIVITY && chosen.id == "nap" && !s.paused && over == null) return NapScreen(s)
    val isWork = p.kind == Kind.WORK
    val accent = if (isWork) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    var confirmEnd by remember { mutableStateOf(false) }
    val (workDone, breakDone) = totals(plan, s, now)
    val phaseMs = s.phaseMs.takeIf { it > 0 } ?: p.ms
    val brk = if (s.bonus > 0) "LONG BREAK · +${extraMin(s)} MIN" else "BREAK"
    val label = when {
        quick -> chosen.name.uppercase() + if (s.bonus > 0) " · +${extraMin(s)} MIN" else ""
        p.kind == Kind.WORK -> "WORK"
        p.kind == Kind.PREP -> "$brk · PREPARE"
        else -> brk
    } + if (over != null) " · OVERTIME" else ""

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = accent, letterSpacing = 3.sp, modifier = Modifier.padding(top = 16.dp))
        routine?.let { id -> TextButton({ onRoutine(id) }) { Text("\u2039  Back to the steps") } }
        Box(Modifier.padding(vertical = 24.dp).size(300.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(14.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = stroke)
                // Countdown: the ring empties. Overtime: it fills once a minute and starts over.
                val sweep = if (over != null) 360f * (over % MIN) / MIN else 360f * left / phaseMs
                drawArc(accent, -90f, sweep, false, style = stroke)
            }
            if (p.kind == Kind.ACTIVITY && chosen.id == "breathing" && !s.paused) Breathing(accent)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (over != null) "+" + fmt(over) else fmt(left), fontSize = 64.sp, fontWeight = FontWeight.Light)
                Text(
                    when {
                        over != null -> "Time's up · counting up"
                        // A timer on its own has an end too, and "15 minutes" in your head is not the
                        // same as knowing it lands at 3:55.
                        routine != null -> "Routine · ends ${clock(now + left)}"
                        quick -> "Quick timer · ends ${clock(now + left)}"
                        // The plan that is actually running, not the full day: a late start plans fewer.
                        // On a break the cycle alone answers nothing useful — when you are back at it does,
                        // and without it the only way to know is to add the prepare and the activity up.
                        else -> "Cycle ${p.cycle} / ${cyclesIn(Timer.plan)}" +
                            (untilWork(plan, s.index, left).takeIf { !isWork }?.let { " · back at ${clock(now + it)}" } ?: "")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The day as it is, not eight identical dots: work and break in proportion, filled as far as you
        // have got, with the clock you started at and the one you are heading for. The same strip Home
        // draws before you begin, so a day running looks like the day you planned.
        if (!quick) DayStrip(
            dayBlocks(plan, s, now), Modifier.fillMaxWidth(),
            from = s.startedAt, to = now + left.coerceAtLeast(0) + plan.drop(s.index + 1).sumOf { it.ms },
        )

        // Overtime: no pause; add 10 more minutes (countdown again) or move on. Right under the ring, where the eye already is.
        if (over != null) Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ Timer.extendBreak() }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text("+10 min", fontSize = 18.sp)
            }
            FilledTonalButton(
                { Timer.skip() }, Modifier.weight(1f).height(56.dp),
                colors = if (isWork) ButtonDefaults.filledTonalButtonColors()
                else ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) { Text("Next", fontSize = 18.sp) }
        }

        // Stopping a timer should never need a scroll: these used to sit under the cue, the sound grid
        // and the day's totals, which on a phone put them a screen and a half below the clock.
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (over == null) Button(
                { if (s.paused) Timer.resume() else Timer.pause() },
                Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) { Text(if (s.paused) (if (s.pausedLeft == phaseMs) "Start" else "Resume") else "Pause", fontSize = 18.sp) }
            if (!quick && over == null) FilledTonalButton(
                { Timer.skip() }, Modifier.weight(1f).height(56.dp),
                colors = if (isWork) ButtonDefaults.filledTonalButtonColors()
                else ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) { Text("Skip", fontSize = 18.sp) }
            // End sits in the same row; it asks for confirmation.
            OutlinedButton(
                { confirmEnd = true }, Modifier.weight(if (quick) 1f else 0.8f).height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            ) { Text("End", fontSize = 18.sp) }
        }

        val cues by Cues.lists.collectAsState()
        CueCard(if (isWork) "Start of the day" else "This break", phaseCue(p, quick, cues, epochDay(s.startedAt)), key = s.index)
        if (isWork) FocusMusic(settings.focusSound) else BreakChoices(p, s, chosen, s.phaseMs - left)

        if (!quick) Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stat("Today", "${fmtDur(workDone)} work", "${fmtDur(breakDone)} breaks", Modifier.weight(1f).fillMaxHeight())
            Stat("Next", Timer.subtitle(s, now).removePrefix("Next: ").takeIf { isWork && over == null } ?: nextLabel(plan, s, at = over == null), "", Modifier.weight(1f).fillMaxHeight())
        }

    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(if (quick) "End ${chosen.name}?" else "End workday?") },
            text = { Text("End and Save keeps what you've done so far in History. Discard throws this session away.") },
            // One row: Discard (red) left, Keep going middle, End and Save as the main, filled button right.
            confirmButton = {
                val tight = PaddingValues(horizontal = 10.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        { confirmEnd = false; Timer.discard() }, contentPadding = tight,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Discard", maxLines = 1) }
                    TextButton({ confirmEnd = false }, contentPadding = tight) { Text("Keep going", maxLines = 1) }
                    Button({ confirmEnd = false; Timer.stop() }, shape = RoundedCornerShape(12.dp), contentPadding = tight) {
                        Text("End and Save", maxLines = 1)
                    }
                }
            },
        )
    }
}

/** [at]: append the start time; off in overtime, where the next phase starts whenever you tap Next. */
private fun nextLabel(plan: List<Phase>, s: Session, at: Boolean = true): String {
    val n = plan.getOrNull(s.index + 1) ?: return "Day complete"
    return when (n.kind) {
        Kind.WORK -> "Work · ${fmtDur(n.ms)}"
        Kind.PREP -> "Break · ${fmtDur(plan.drop(s.index + 1).takeWhile { it.kind != Kind.WORK }.sumOf { it.ms })}"
        Kind.ACTIVITY -> activity(Timer.settings.value.activity).let { "${it.emoji} ${it.name} · ${fmtDur(n.ms + s.bonus)}" }
    } + if (at) " at ${clock(s.endsAt)}" else ""
}

/** Chosen during the break itself: what to do, and whether to make it a long one. */
@Composable
private fun BreakChoices(p: Phase, s: Session, chosen: Activity, elapsedMs: Long) {
    val quick = s.quick != null
    // The catalogue used to open itself at the start of every break — twenty choices at the moment you
    // have least appetite for choosing. Prepare now offers two, and the rest is one tap away.
    var changing by remember(s.index) { mutableStateOf(false) }
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
                // Started on its own, this is the one you chose: say how often it has come round lately.
                // Not on the first — "1st walk in 14 days" when you have never walked says nothing.
                if (quick) {
                    val lines by History.timelines.collectAsState()
                    val nth = timesDone(lines, chosen.id, History.today()) + 1
                    if (nth >= 2) Text(
                        "${ordinal(nth)} in the last 14 days",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            // One row of actions: the activity's own button (play / open) + Change + +10 min, equal height.
            val cs = MaterialTheme.colorScheme
            val colors = ButtonDefaults.buttonColors(containerColor = cs.surface, contentColor = cs.onSurface)
            val pad = PaddingValues(horizontal = 10.dp)
            val primary = p.kind == Kind.ACTIVITY && hasPrimaryAction(chosen)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primary) PrimaryAction(chosen, Modifier.weight(1.4f).height(44.dp))
                // In Prepare the two-choice card carries "Something else…", so this would be a second door
                // to the same room; it comes back as "Done" once the full picker is open.
                if (!quick && (p.kind != Kind.PREP || changing)) Button({ changing = !changing }, Modifier.weight(1f).height(44.dp), colors = colors, contentPadding = pad) {
                    // Toggles the tile picker; "Done" closes it so the row never ends up with a lone button.
                    Text(if (changing) "Done" else if (primary) "Change" else "Change activity", maxLines = 1)
                }
                Button({ Timer.extendBreak() }, Modifier.weight(1f).height(44.dp), colors = colors, contentPadding = pad) { Text("+10 min", maxLines = 1) }
            }
            if (p.kind == Kind.ACTIVITY) ActivityCue(chosen, elapsedMs, s.paused)
            if (p.kind == Kind.PREP && !changing && !quick) TwoChoices(chosen) { changing = true }
            if (changing) ActivityPicker(chosen.id, Timer.settings.value.breakMin) { Timer.chooseActivity(it) }
        }
    }
}

@Composable
private fun FocusMusic(selected: String) {
    Card(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🎵 Focus music", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            SoundTiles(selected, off = true, MaterialTheme.colorScheme.surface, "Focus music") { Timer.chooseFocus(it) }
        }
    }
}

/** Looped sounds as compact tiles (focus music, home ambient); emoji comes from resources/manifest.json. */
@Composable
internal fun SoundTiles(selected: String, off: Boolean, bg: Color, label: String, onPick: (String) -> Unit) {
    val sounds by Media.sounds.collectAsState()
    val downloading by Media.downloading.collectAsState()
    val loops = sounds.filter { it.loop }
    val cs = MaterialTheme.colorScheme
    TileGrid((if (off) listOf(Media.Sound("", "Off", "", true, "🔇")) else emptyList()) + loops) { snd ->
        val on = selected == snd.id
        Tile(
            snd.emoji, snd.name, "", if (on) cs.primary else bg, if (on) cs.onPrimary else cs.onSurface,
            cs.onSurfaceVariant, "$label ${snd.name}", compact = true,
        ) { onPick(snd.id) }
    }
    if (loops.isEmpty()) {
        Text(
            if (downloading) "Downloading sounds from GitHub…" else "Sounds not downloaded yet",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
        if (!downloading) TextButton({ Media.sync() }) { Text("Retry download") }
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
internal fun Done(s: Session, settings: Settings, onDay: () -> Unit) {
    val work = s.workMs
    val rest = s.breakMs
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val q = s.quick?.let(::activity)
        Text(q?.emoji ?: "🎉", fontSize = 64.sp)
        Text(q?.let { "${it.name} complete" } ?: "Workday complete", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
        Card(Modifier.fillMaxWidth().padding(top = 24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (q != null) {
                    Line("Duration", fmtDur(rest))
                } else {
                    Line("Focused", fmtDur(work))
                    Line("Breaks", fmtDur(rest))
                    Line("Cycles", "${s.cycles} / ${settings.cycles}")
                }
                Line("Started", clock(s.startedAt))
                Line("Finished", clock(s.finishedAt))
            }
        }
        if (q == null) CueCard("Before you stop", endCue(Cues.lists.collectAsState().value), key = s.startedAt)
        Button({ Timer.stop() }, Modifier.fillMaxWidth().padding(top = 24.dp).height(64.dp), shape = RoundedCornerShape(32.dp)) { Text("Done") }
        if (s.quick == null) OutlinedButton(onDay, Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp), shape = RoundedCornerShape(32.dp)) { Text("See the whole day") }
    }
}

