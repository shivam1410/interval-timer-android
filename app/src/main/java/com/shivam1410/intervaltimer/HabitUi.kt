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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
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
            // The count is the whole screen while it runs: which set, the number big enough to glance at
            // from the floor, and a cell per rep so you can see it filling without reading the number.
            Text(
                if (moves != null) "MOVE ${c.set.coerceAtMost(c.sets)} OF ${c.sets}" else "SET ${c.set.coerceAtMost(c.sets)} OF ${c.sets}",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp, color = cs.primary,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (c.resting) "Rest" else "${c.done}",
                    fontSize = 92.sp, fontWeight = FontWeight.Bold, letterSpacing = (-4).sp, lineHeight = 96.sp,
                )
                Text(
                    if (c.resting) " ${c.restLeftSec}s" else " / ${c.of}${if (stage.holdSec > 0 && !repHold) " s" else ""}",
                    Modifier.padding(bottom = 10.dp), fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                    color = cs.onSurfaceVariant,
                )
            }
            if (repHold && !c.resting) Text(
                if (c.holdLeftSec > 0) "Hold ${c.holdLeftSec}" else "Relax",
                style = MaterialTheme.typography.headlineSmall, color = cs.primary,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(c.of.coerceIn(1, 40)) { i ->
                    Box(
                        Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (!c.resting && i < c.done) cs.primary else cs.surfaceContainerHighest),
                    )
                }
            }
            if (moves != null) Text(
                if (c.resting) "Next: ${moveName(c.set + 1).orEmpty()}" else moveName(c.set).orEmpty(),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "It counts for you — no tapping per rep", Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            Button(
                { Habits.markDone(h.id, today, true); countStart = null; onDone?.invoke() },
                Modifier.fillMaxWidth().padding(top = 14.dp).height(64.dp), shape = RoundedCornerShape(32.dp),
            ) { Text("✓  Completed", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            TextButton({ countStart = null }, Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
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
internal fun AddHabitRow(label: String, onStart: () -> Unit) {
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

/**
 * Doing one stretch, on its own page (the C+ "Habit run" board). It used to be a card wedged into Home,
 * which is why Home could only ever show one of them at a time.
 */
@Composable
fun HabitRunScreen(id: String, onBack: () -> Unit) {
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val cs = MaterialTheme.colorScheme
    val h = habits.firstOrNull { it.id == id } ?: return onBack()
    val today = LocalDate.now()
    val day = h.dayOf(today)
    val stage = h.stageOn(day)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(R.drawable.ic_chevron_down, "Back to the day", onClick = onBack)
            Text(
                "${habitTitle(h, items).uppercase()} · DAY ${day.coerceAtMost(h.totalDays)} OF ${h.totalDays}",
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2,
            )
            Spacer(Modifier.width(34.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (stage == null) Text("This plan has finished.", style = MaterialTheme.typography.titleMedium)
            else key(h.id) { HabitDoer(h, items, stage, day, onDone = onBack) }
        }
        Spacer(Modifier.height(16.dp))
    }
}
