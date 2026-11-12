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
import java.time.LocalDate

/** Rest between sets while counting. */
private const val REST_MS = 30_000

/** Home card for the running habit: day, today's sets × reps, the stage note, progress and Done. */
@Composable
fun HabitCard(onCalendar: () -> Unit) {
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val h = habits.firstOrNull { !it.ended } ?: return
    val item = items.firstOrNull { it.id == h.itemId && !it.deleted }
    val title = item?.title ?: "Habit"
    val today = LocalDate.now()
    val day = h.dayOf(today)
    val cs = MaterialTheme.colorScheme
    // "Count with me": when it started (null = not counting) and how long ago, ticking every frame.
    var countStart by remember { mutableStateOf<Long?>(null) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(countStart) {
        val start = countStart ?: return@LaunchedEffect
        while (true) withInfiniteAnimationFrameMillis { elapsed = System.currentTimeMillis() - start }
    }

    Text("Habit", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("Day ${day.coerceAtMost(h.totalDays)}/${h.totalDays}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
            val stage = h.stageOn(day)
            if (stage == null) {
                Text("Finished ${h.totalDays} days · ${h.doneCount} done")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ Habits.start(h.itemId, h.stages, h.remindMin) }) { Text("Repeat") }
                    OutlinedButton({ Habits.end(h.id) }) { Text("End") }
                }
            } else {
                val demo = h.demoOn(day, title)
                val clip = h.clipOn(day, item?.clip.orEmpty())
                val counting = countStart != null
                val c = countAt(elapsed, stage, repMs = demo?.let { DEMOS[it]?.loopMs } ?: 2000, restMs = REST_MS)
                LaunchedEffect(counting && c.finished) {
                    if (counting && c.finished) { Habits.markDone(h.id, today, true); countStart = null }
                }
                // While counting, the figure moves in step with the count (still during the rest).
                if (counting && clip.isEmpty() && demo != null) MovementDemo(demo, clockMs = if (c.resting) 0 else c.setMs)
                else ExerciseMedia(clip, demo)
                if (counting) {
                    Text(
                        if (c.resting) "Rest ${c.restLeftSec}s" else "${c.done} / ${c.of}${if (stage.holdSec > 0) " s" else ""}",
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold,
                    )
                    Text(if (c.resting) "Set ${c.set + 1} of ${c.sets} next" else "Set ${c.set} of ${c.sets}", color = cs.onSurfaceVariant)
                    OutlinedButton({ countStart = null }, Modifier.fillMaxWidth()) { Text("Stop counting") }
                    return@Column
                }
                Text(label(stage), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                // A new stage's note is bold on its first day, so the change doesn't go unnoticed.
                if (stage.note.isNotBlank()) Text(stage.note, fontWeight = if (h.isStageStart(day)) FontWeight.Bold else FontWeight.Normal)
                LinearProgressIndicator({ day.toFloat() / h.totalDays }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val doneToday = today in h.done
                    if (doneToday) FilledTonalButton({ Habits.markDone(h.id, today, false) }, Modifier.weight(1f)) { Text("Done today ✓") }
                    else Button({ Habits.markDone(h.id, today, true) }, Modifier.weight(1f)) { Text("Done") }
                    TextButton({ elapsed = 0; countStart = System.currentTimeMillis() }, Modifier.padding(start = 4.dp)) { Text("Count") }
                    TextButton(onCalendar) { Text("Calendar") }
                }
            }
        }
    }
}
