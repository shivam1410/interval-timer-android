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
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
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
        Text(
            "PICK ONE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, color = cs.onSurfaceVariant,
        )
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
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
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
internal fun Running(plan: List<Phase>, s: Session, settings: Settings, now: Long, onMinimise: () -> Unit, onRoutine: (String) -> Unit = {}) {
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

    var more by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        val outline = MaterialTheme.colorScheme.outlineVariant
        // A timer on its own is one thing in the middle of the screen, as the board draws it; a workday
        // has a cue and cards to stack from the top. Short content centres, tall content still scrolls.
        RunBar(
            (if (isWork) "WORK · CYCLE ${p.cycle} OF ${cyclesIn(plan)}"
            else if (quick) chosen.name.uppercase() + " · ON ITS OWN"
            else "BREAK · CYCLE ${p.cycle} OF ${cyclesIn(plan)}") + if (over != null) " · OVERTIME" else "",
            onMinimise, { more = true },
        )
        BoxWithConstraints(Modifier.weight(1f)) {
        val tall = maxHeight
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = tall),
            verticalArrangement = if (quick) Arrangement.Center else Arrangement.Top,
        ) {
            if (isWork) WorkScreen(plan, s, now, over)
            else BreakScreen(plan, s, settings, now, over, chosen, onMinimise, { more = true })
            routine?.let { id ->
                TextButton({ onRoutine(id) }, Modifier.padding(top = 8.dp)) { Text("\u2039  Back to the steps") }
            }
            // What comes next, in the board's one-line card: a dot, when, and what you will choose from.
            if (!quick && isWork) NextBreakCard(plan, s, settings, now)
            if (!isWork && !quick && p.kind == Kind.PREP) TwoChoices(chosen) { more = true }
            if (!isWork && p.kind == Kind.ACTIVITY) ActivityCue(chosen, s.phaseMs - left, s.paused)
            // Pushes the break's prompt down to the buttons. The work screen does its own spacing.
            if (!quick && !isWork) Spacer(Modifier.weight(1f))
            // The board pins the break's prompt above the buttons, under a line: it is the last thing
            // you read before deciding, not a card competing with the choice itself.
            if (!isWork && !quick) {
                val cues by Cues.lists.collectAsState()
                phaseCue(p, false, cues, epochDay(s.startedAt))?.let { cue ->
                    Column(
                        Modifier.fillMaxWidth().padding(top = 14.dp)
                            .drawBehind { drawLine(outline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                            .padding(top = 14.dp),
                    ) {
                        Text(
                            "A DIFFERENT PROMPT EACH BREAK", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(cue, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
        }
        // How often this has happened lately — the dots say which days, the line says where this one lands.
        if (quick) s.quick?.let { QuickTally(it, chosen) }
        // Pause · the big one · End — the board's row, square buttons flanking what you actually press.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The work board flanks the main action with Pause and End; the break board offers only
            // +10 and the way back. What a board leaves off is in the ⋯ sheet, not gone.
            if (over == null && (isWork || quick)) SquareButton(if (s.paused) "Resume" else "Pause", accent) {
                if (s.paused) Timer.resume() else Timer.pause()
            }
            if (quick || !isWork) SquareButton("+10 min", accent) { Timer.extendBreak() }
            Button(
                { if (over != null) Timer.skip() else if (quick) Timer.stop() else Timer.skip() },
                Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Text(
                    when {
                        over != null -> "Next"
                        quick -> "Finish"
                        isWork -> "Break now"
                        else -> "Back to work"
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            if (isWork && over != null) SquareButton("+10 min", accent) { Timer.extendBreak() }
            // Ending lives on the work screen, where the board has it, and in the sheet everywhere else.
            if (isWork) SquareButton("End", MaterialTheme.colorScheme.error) { confirmEnd = true }
        }
    }

    if (more) MoreSheet(s, settings, plan, now, isWork, quick, chosen, onDiscard = { confirmEnd = true }) { more = false }

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
    // Sized and centred in its own box. It used to be a 260dp circle with the word nudged 150dp down to
    // clear the ring it sat inside; with the ring gone that left a huge disc and a word under its chin.
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().scale(scale).clip(CircleShape).background(color.copy(alpha = 0.15f)))
        Text(
            listOf("Inhale", "Hold", "Exhale", "Hold")[step],
            color = color, style = MaterialTheme.typography.titleMedium,
        )
    }
}

// ---------- Done ----------

@Composable
internal fun Done(s: Session, settings: Settings, onDay: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val q = s.quick?.let(::activity)
    val cycles = s.cycles
    val planned = settings.cycles
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            History.dateOf(s.startedAt).format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM")).uppercase(),
            Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                q?.let { "${it.name} done." } ?: "That's the day.",
                fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 39.sp,
            )
            // What you did, and nothing about what you didn't: the cycles you skipped are owed to no one.
            Text(
                q?.let { "You finished at ${clock(s.finishedAt)}." }
                    ?: ("You ended at ${clock(s.finishedAt)}, after $cycles of $planned ${if (planned == 1) "cycle" else "cycles"}." +
                        if (cycles < planned) " The ${(planned - cycles).spelled()} you didn't run aren't owed to anyone." else ""),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant, lineHeight = 20.sp,
            )
            if (q == null) Column(Modifier.padding(top = 26.dp)) {
                DayStrip(dayBlocks(Timer.plan, s, s.finishedAt), Modifier.fillMaxWidth().height(76.dp))
                Row(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                    val st = MaterialTheme.typography.labelSmall
                    Text(clock(s.startedAt), Modifier.weight(1f), style = st, color = cs.onSurfaceVariant)
                    Text("ended ${clock(s.finishedAt)}", style = st, color = cs.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (q == null) {
                    EndTile("FOCUSED", fmtDur(s.workMs), Modifier.weight(1f))
                    EndTile("CYCLES", "$cycles", Modifier.weight(1f))
                    EndTile("BREAKS", fmtDur(s.breakMs), Modifier.weight(1f))
                } else EndTile("DURATION", fmtDur(s.breakMs), Modifier.weight(1f))
            }
            if (q == null) endCue(Cues.lists.collectAsState().value)?.let { cue ->
                Column(
                    Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
                        .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        "END OF DAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp, color = cs.onSurfaceVariant,
                    )
                    Text(cue, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                }
            }
        }
        if (q == null) TextButton(onDay, Modifier.align(Alignment.CenterHorizontally)) { Text("See the whole day") }
        Button(
            { Timer.stop() }, Modifier.fillMaxWidth().padding(bottom = 20.dp).height(72.dp),
            shape = RoundedCornerShape(20.dp),
        ) { Text("Done", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

/** One of the end-of-day figures: a small label over a big number. */
@Composable
internal fun EndTile(label: String, value: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(cs.surface)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(13.dp),
    ) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp, color = cs.onSurfaceVariant,
        )
        Text(value, Modifier.padding(top = 4.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}


// ---------- The C+ "Work" and "Break" boards ----------

/** The chrome both running screens share: close it away, what this is, and more. */
@Composable
private fun RunBar(title: String, onMinimise: () -> Unit, onMore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(R.drawable.ic_chevron_down, "Back to the day", onClick = onMinimise)
        Text(
            title, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1,
        )
        RoundIcon(R.drawable.ic_more, "More", onClick = onMore)
    }
}

@Composable
internal fun RoundIcon(icon: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(34.dp).clip(CircleShape).background(cs.surface).border(1.dp, cs.outlineVariant, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), label, Modifier.size(17.dp), tint = cs.onSurfaceVariant) }
}

/**
 * The work block as a block: a tall rounded well that drains as the minutes go, the time inside it big
 * enough to read from across the room. A ring says "a timer is running"; this says how much is left.
 */
@Composable
private fun ColumnScope.WorkScreen(plan: List<Phase>, s: Session, now: Long, over: Long?) {
    val cs = MaterialTheme.colorScheme
    val p = plan[s.index]
    val left = s.left(now)
    val phaseMs = (s.phaseMs.takeIf { it > 0 } ?: p.ms).coerceAtLeast(1)
    val fill = if (over != null) 1f else (left.toFloat() / phaseMs).coerceIn(0f, 1f)
    apply {
        DayStrip(
            dayBlocks(plan, s, now), Modifier.fillMaxWidth().padding(top = 16.dp),
            from = s.startedAt, to = now + left.coerceAtLeast(0) + plan.drop(s.index + 1).sumOf { it.ms },
        )
        Spacer(Modifier.weight(1f))
        Text(
            "THIS BLOCK", Modifier.fillMaxWidth().padding(top = 10.dp), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Box(
            Modifier.fillMaxWidth().padding(top = 14.dp).height(300.dp).clip(RoundedCornerShape(24.dp))
                .background(cs.surfaceContainerHighest).border(1.5.dp, cs.outlineVariant, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(fill).background(cs.primary))
            Column(
                Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val onFill = if (fill > 0.45f) cs.onPrimary else cs.onSurface
                Text(
                    if (over != null) "+" + fmt(over) else fmt(left),
                    fontSize = 72.sp, fontWeight = FontWeight.Bold, color = onFill, letterSpacing = (-3).sp,
                )
                Text(
                    if (over != null) "time's up · counting up" else "left of ${spelledMinutes(phaseMs)}",
                    style = MaterialTheme.typography.bodyMedium, color = onFill.copy(alpha = 0.76f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/** A break is short and about choosing: a thin bar, the clock, and what you could do with it. */
@Composable
private fun BreakScreen(
    plan: List<Phase>, s: Session, settings: Settings, now: Long, over: Long?, chosen: Activity,
    onMinimise: () -> Unit, onMore: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val p = plan[s.index]
    val left = s.left(now)
    val phaseMs = (s.phaseMs.takeIf { it > 0 } ?: p.ms).coerceAtLeast(1)
    val quick = s.quick != null
    val done = if (over != null) 1f else ((phaseMs - left).toFloat() / phaseMs).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().padding(top = 20.dp).height(16.dp).clip(RoundedCornerShape(5.dp))
                .background(cs.surfaceContainerHighest),
        ) { Box(Modifier.fillMaxWidth(done).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(cs.tertiary)) }
        Text(
            if (over != null) "+" + fmt(over) else fmt(left),
            Modifier.fillMaxWidth().padding(top = if (quick) 24.dp else 14.dp),
            fontSize = if (quick) 76.sp else 52.sp, fontWeight = FontWeight.Bold,
            letterSpacing = if (quick) (-3.5).sp else (-2).sp, textAlign = TextAlign.Center,
        )
        val back = untilWork(plan, s.index, left)
        Text(
            when {
                over != null -> "Time's up · tap Next when you're done"
                back != null -> "Back to work at ${clock(now + back)}"
                // The clock counts down; without this it never says down from what.
                quick -> "left of ${spelledMinutes(phaseMs.toLong())} · ends at ${clock(now + left)}"
                else -> "Ends at ${clock(now + left)}"
            },
            Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        // Box breathing lived inside the old ring, and the activity's own Play / Open button inside the
        // card the board replaces. Neither belongs to a ring — they belong to the activity.
        if (p.kind == Kind.ACTIVITY && chosen.id == "breathing" && !s.paused) Box(
            Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center,
        ) { Breathing(cs.tertiary) }
        if (p.kind == Kind.ACTIVITY && hasPrimaryAction(chosen)) PrimaryAction(
            chosen, Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp),
        )
        // A walk is being recorded somewhere else while this runs. The board says so, with the way back
        // into it, because nothing outside Strava can stop a recording it started.
        if (quick && Strava.sportFor(s.quick.orEmpty()) != null) {
            val ctx = LocalContext.current
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(cs.error))
                Column(Modifier.weight(1f)) {
                    Text(
                        "RECORDING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp, color = cs.onSurfaceVariant,
                    )
                    Text("Strava has the GPS", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                TextButton({ Strava.openRecording(ctx, s.quick) }) { Text("Open") }
            }
            Text(
                "When the timer ends, Strava comes to the front so Pause and Finish are one tap away.",
                Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/**
 * "9th walk in 14 days", with a dot per day. Density, not a streak: it counts what happened, and a gap
 * costs nothing. Hidden until there is something to count.
 */
@Composable
private fun QuickTally(id: String, chosen: Activity) {
    val timelines by History.timelines.collectAsState()
    val today = History.today()
    val before = timesDone(timelines, id, today)
    if (before < 1) return
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (13 downTo 0).forEach { d ->
                val on = timelines[today.minusDays(d.toLong())]?.any { it.label == id } == true
                Box(
                    Modifier.size(6.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (on) cs.tertiary else cs.surfaceContainerHighest),
                )
            }
        }
        Text(
            "  ${ordinal(before + 1)} ${chosen.name.lowercase()} in 14 days",
            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
        )
    }
}

/** A square button with a short word in it: the board flanks the main action with these. */
@Composable
private fun SquareButton(label: String, tint: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.height(54.dp).widthIn(min = 54.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1) }
}

/** "BREAK AT 2:11 · 10 MIN · Stretch, or Sketch — you choose then". */
@Composable
private fun NextBreakCard(plan: List<Phase>, s: Session, settings: Settings, now: Long) {
    val cs = MaterialTheme.colorScheme
    val at = untilWork(plan, s.index, s.left(now)) ?: return
    val breakMs = plan.drop(s.index + 1).takeWhile { it.kind != Kind.WORK }.sumOf { it.ms }
    if (breakMs <= 0) return
    val usual = activity(settings.activity)
    val fresh = staleChoice(QUICK, lastUsed(History.timelines.value), History.today(), exclude = usual.id)?.let(::activity)
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(cs.tertiary))
        Column(Modifier.weight(1f)) {
            Text(
                "BREAK AT ${clock(now + at - breakMs)} · ${fmtDur(breakMs)}",
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp, color = cs.onSurfaceVariant,
            )
            Text(
                fresh?.let { "${usual.name}, or ${it.name} — you choose then" } ?: "${usual.name} — or change it then",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Everything the board leaves off the running screen, one tap away rather than gone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    s: Session, settings: Settings, plan: List<Phase>, now: Long, isWork: Boolean, quick: Boolean,
    chosen: Activity, onDiscard: () -> Unit, onDismiss: () -> Unit,
) {
    val cues by Cues.lists.collectAsState()
    val (workDone, breakDone) = totals(plan, s, now)
    ModalBottomSheet(onDismiss) {
        // The picker is as tall as the list of activities, so without this the last thing in the sheet
        // can sit below the bottom of it, out of reach.
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
        ) {
            CueCard(if (isWork) "Start of the day" else "This break", phaseCue(plan[s.index], quick, cues, epochDay(s.startedAt)), key = s.index)
            if (isWork) FocusMusic(settings.focusSound)
            else ActivityPicker(chosen.id, settings.breakMin) { Timer.chooseActivity(it) }
            TextButton(
                { onDismiss(); onDiscard() }, Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (quick) "Throw this timer away" else "End the day") }
            if (!quick) Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Stat("Today", "${fmtDur(workDone)} work", "${fmtDur(breakDone)} breaks", Modifier.weight(1f).fillMaxHeight())
                Stat("Next", nextLabel(plan, s), "", Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** "50 minutes", not "50m": the running screen is read at a glance, not scanned for totals. */
internal fun spelledMinutes(ms: Long): String {
    val m = ((ms + 30_000) / 60_000L).toInt().coerceAtLeast(1)
    return "$m ${if (m == 1) "minute" else "minutes"}"
}
