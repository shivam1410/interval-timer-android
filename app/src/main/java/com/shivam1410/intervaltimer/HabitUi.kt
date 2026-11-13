package com.shivam1410.intervaltimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import java.time.LocalDate

/** Rest between sets while counting; between the moves of a circuit it's shorter. */
private const val REST_MS = 30_000
private const val MOVE_REST_MS = 10_000

/** Home's Habit section: a deck of running habits (only the top card shows, done ones go under), then a row to add one (opens Settings › Habits). */
@Composable
fun HabitsSection(onCalendar: (String) -> Unit, onAdd: () -> Unit) {
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val running = habits.filter { !it.ended }
    val today = LocalDate.now()
    val routines by Routines.all.collectAsState()
    val deck = habitDeck(running, today, hidden = habitsInRoutines(routines))
    if (deck.isEmpty()) return // no habits of their own (none, or all in routines): no section; start one from Settings › Stretches
    Text("Habits", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    val left = deck.count { today !in it.done }
    // Next › turns through the habits still to do (or all of them once everything is done).
    var turn by remember { mutableIntStateOf(0) }
    val pool = if (left > 0) deck.take(left) else deck
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pool.isNotEmpty()) {
            val top = pool[turn % pool.size]
            val under = (deck.size - 1).coerceAtMost(2)
            // A stacked deck: the top card, with the edges of up to two more peeking out below it.
            Box(Modifier.padding(bottom = (under * 8).dp)) {
                (under downTo 1).forEach { i ->
                    Box(
                        Modifier.matchParentSize().offset(y = (i * 8).dp).padding(horizontal = (i * 12).dp)
                            .clip(RoundedCornerShape(12.dp)).background(if (i == 1) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
                key(top.id) { HabitCard(top, items) { onCalendar(top.id) } }
            }
            if (deck.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (left == 0) "✓ All ${deck.size} done today" else "$left to do · ${deck.size - left} done today",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pool.size > 1) TextButton({ turn = (turn + 1) % pool.size }) { Text("Next ›") }
            }
        }
        AddHabitRow("Add a habit", onAdd)
    }
}

/** One running habit: 📅 day (opens its calendar), today's sets × reps, the stage note, Completed and Start. */
@Composable
private fun HabitCard(h: Habit, items: List<LibraryItem>, onCalendar: () -> Unit) {
    val title = habitTitle(h, items)
    val today = LocalDate.now()
    val day = h.dayOf(today)
    val cs = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Open calendar", onClick = onCalendar).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(18.dp), tint = cs.primary)
                    Text("Day ${day.coerceAtMost(h.totalDays)}/${h.totalDays}", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                }
            }
            val stage = h.stageOn(day)
            if (stage == null) {
                Text("Finished ${h.totalDays} days · ${h.doneCount} done")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ Habits.repeat(h.id) }) { Text("Repeat") }
                    OutlinedButton({ Habits.end(h.id) }) { Text("End") }
                }
            } else HabitDoer(h, items, stage, day)
        }
    }
}

/**
 * Doing [h] today ([stage] of [day]): the figure or your clip, sets × reps and the note, Completed and Start
 * (counts along with the figure). Shared by the Home card and a routine's habit step; [onDone] (routine)
 * runs once it's marked done, and there Completed moves on instead of toggling.
 */
@Composable
fun HabitDoer(h: Habit, items: List<LibraryItem>, stage: Stage, day: Int, onDone: (() -> Unit)? = null) {
    val item = items.firstOrNull { it.id == h.itemId && !it.deleted }
    val title = habitTitle(h, items)
    val today = LocalDate.now()
    val cs = MaterialTheme.colorScheme
    // "Count with me": when it started (null = not counting) and how long ago, ticking every frame.
    var countStart by remember(h.id) { mutableStateOf<Long?>(null) }
    var elapsed by remember(h.id) { mutableLongStateOf(0L) }
    LaunchedEffect(countStart) {
        val start = countStart ?: return@LaunchedEffect
        while (true) withInfiniteAnimationFrameMillis { elapsed = System.currentTimeMillis() - start }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val baseDemo = h.demoOn(day, title)
        val moves = baseDemo?.let { CIRCUITS[it] }
        val clip = h.clipOn(day, item?.clip.orEmpty())
        val counting = countStart != null
        val c = countAt(elapsed, stage, repMs = baseDemo?.let { DEMOS[it]?.loopMs } ?: 2000, restMs = if (moves != null) MOVE_REST_MS else REST_MS)
        // While counting a circuit, the figure is this set's move (the next one during the rest).
        val demo = baseDemo?.let { if (countStart != null) moveDemo(it, if (c.resting) c.set + 1 else c.set) else it }
        fun moveName(set: Int) = moves?.getOrNull(set - 1)?.name
        LaunchedEffect(counting && c.finished) {
            if (counting && c.finished) { Habits.markDone(h.id, today, true); countStart = null; onDone?.invoke() }
        }
        // While counting, the figure moves in step with the count (still during the rest).
        val repHold = stage.holdSec > 0 && stage.reps > 0
        // Reps held for seconds: the figure holds its press pose during the hold, rests while relaxing.
        val clock = when { c.resting -> 0L; repHold -> if (c.holdLeftSec > 0) 1500L else 0L; else -> c.setMs }
        if (counting && clip.isEmpty() && demo != null) MovementDemo(demo, clockMs = clock)
        else ExerciseMedia(clip, demo)
        if (counting) {
            Text(
                if (c.resting) "Rest ${c.restLeftSec}s" else "${c.done} / ${c.of}${if (stage.holdSec > 0 && !repHold) " s" else ""}",
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold,
            )
            if (repHold && !c.resting) Text(if (c.holdLeftSec > 0) "Hold ${c.holdLeftSec}" else "Relax", style = MaterialTheme.typography.headlineSmall, color = cs.primary)
            if (moves != null) Text(
                if (c.resting) "Next, move ${c.set + 1} of ${c.sets}: ${moveName(c.set + 1)}" else "Move ${c.set} of ${c.sets}: ${moveName(c.set)}",
                style = MaterialTheme.typography.titleMedium,
            )
            else Text(if (c.resting) "Set ${c.set + 1} of ${c.sets} next" else "Set ${c.set} of ${c.sets}", color = cs.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button({ Habits.markDone(h.id, today, true); countStart = null; onDone?.invoke() }, Modifier.weight(1f)) { Text("Completed") }
                TextButton({ countStart = null }, Modifier.padding(start = 4.dp)) { Text("Cancel") } // started by mistake
            }
            return@Column
        }
        Text(if (moves != null) "${moves.size} moves × ${stage.reps} · ${stage.holdSec} s hold" else label(stage), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        moves?.forEachIndexed { i, m -> Text("${i + 1}. ${m.name}", style = MaterialTheme.typography.bodyMedium) }
        // A new stage's note is bold on its first day, so the change doesn't go unnoticed.
        if (stage.note.isNotBlank()) Text(stage.note, fontWeight = if (h.isStageStart(day)) FontWeight.Bold else FontWeight.Normal)
        LinearProgressIndicator({ day.toFloat() / h.totalDays }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val doneToday = today in h.done && onDone == null
            // Completed marks today (tap again to undo; in a routine it moves on); Start counts the reps with the figure.
            if (doneToday) FilledTonalButton({ Habits.markDone(h.id, today, false) }, Modifier.weight(1f)) { Text("Completed ✓", maxLines = 1) }
            else OutlinedButton({ Habits.markDone(h.id, today, true); onDone?.invoke() }, Modifier.weight(1f)) { Text("Completed") }
            Button({ elapsed = 0; countStart = System.currentTimeMillis() }, Modifier.weight(1f)) { Text("Start") }
        }
    }
}

/** The row under the habit cards: start one, or add another (the catalog, in Settings › Habits). */
@Composable
private fun AddHabitRow(label: String, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh)
            .clickable(onClickLabel = "Start a habit", onClick = onStart).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📅", fontSize = 20.sp)
        Text(label, Modifier.padding(start = 12.dp), color = cs.onSurface)
    }
}
