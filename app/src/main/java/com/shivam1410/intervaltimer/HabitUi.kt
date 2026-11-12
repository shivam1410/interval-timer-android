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
import java.time.LocalDate

/** Home card for the running habit: day, today's sets × reps, the stage note, progress and Done. */
@Composable
fun HabitCard() {
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val h = habits.firstOrNull { !it.ended } ?: return
    val item = items.firstOrNull { it.id == h.itemId && !it.deleted }
    val title = item?.title ?: "Habit"
    val today = LocalDate.now()
    val day = h.dayOf(today)
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }

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
                ExerciseMedia(h.clipOn(day, item?.clip.orEmpty()), h.demoOn(day, title))
                Text(label(stage), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                // A new stage's note is bold on its first day, so the change doesn't go unnoticed.
                if (stage.note.isNotBlank()) Text(stage.note, fontWeight = if (h.isStageStart(day)) FontWeight.Bold else FontWeight.Normal)
                LinearProgressIndicator({ day.toFloat() / h.totalDays }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val doneToday = today in h.done
                    if (doneToday) FilledTonalButton({ Habits.markDone(h.id, today, false) }, Modifier.weight(1f)) { Text("Done today ✓") }
                    else Button({ Habits.markDone(h.id, today, true) }, Modifier.weight(1f)) { Text("Done") }
                    TextButton({ editing = true }, Modifier.padding(start = 8.dp)) { Text("Edit") }
                }
            }
        }
    }
    if (editing) HabitEditor(title, h.stages, isNew = false, initialRemind = h.remindMin, onEnd = { Habits.end(h.id) }, onDismiss = { editing = false }) { stages, remind ->
        Habits.edit(h.id, stages, remind)
    }
}

/**
 * The program's stages: days, sets, reps (or seconds held) and a note each. [onEnd] (an existing habit
 * only) ends it; Save / Start hands back the stages.
 */
@Composable
fun HabitEditor(
    title: String, initial: List<Stage>, isNew: Boolean, initialRemind: Int? = Habits.DEFAULT_REMIND_MIN, onEnd: (() -> Unit)? = null,
    onDismiss: () -> Unit, onSave: (List<Stage>, Int?) -> Unit,
) {
    var stages by remember { mutableStateOf(initial) }
    var remind by remember { mutableStateOf(initialRemind) }
    fun set(i: Int, change: (Stage) -> Stage) { stages = stages.mapIndexed { j, s -> if (j == i) change(s) else s } }
    val replacing = isNew && Habits.active != null
    var pickingFor by remember { mutableStateOf(-1) }
    val pickClip = rememberClipPicker { name -> if (pickingFor in stages.indices) set(pickingFor) { it.copy(clip = name) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "$title: 30-day habit" else title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (replacing) Text("This replaces your current habit.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                stages.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stage ${i + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (stages.size > 1) TextButton({ stages = stages.filterIndexed { j, _ -> j != i } }) { Text("Remove") }
                    }
                    Stepper("Days", s.days, "", 1..90) { v -> set(i) { it.copy(days = v) } }
                    Stepper("Sets", s.sets, "", 1..10) { v -> set(i) { it.copy(sets = v) } }
                    if (s.holdSec > 0) Stepper("Hold", s.holdSec, "s", 1..600) { v -> set(i) { it.copy(holdSec = v) } }
                    else Stepper("Reps", s.reps, "", 1..300) { v -> set(i) { it.copy(reps = v) } }
                    TextButton({ set(i) { if (it.holdSec > 0) it.copy(holdSec = 0, reps = it.reps.coerceAtLeast(1)) else it.copy(holdSec = 30) } }) {
                        Text(if (s.holdSec > 0) "Count reps instead" else "Hold for seconds instead")
                    }
                    // ponytail: a replaced or removed stage clip stays in files/library; sweep orphans if storage matters.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (s.clip.isNotEmpty()) "🎬 Own clip" else if (i > 0) "Clip: same as the stage before" else "Clip: the exercise's",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton({ pickingFor = i; pickClip() }) { Text(if (s.clip.isNotEmpty()) "Change" else "Add clip") }
                        if (s.clip.isNotEmpty()) TextButton({ set(i) { it.copy(clip = "") } }) { Text("Remove") }
                    }
                    OutlinedTextField(
                        s.note, { v -> set(i) { it.copy(note = v.take(200)) } }, Modifier.fillMaxWidth(),
                        label = { Text("Note (optional)") }, maxLines = 3, shape = RoundedCornerShape(12.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                ReminderRow(remind) { remind = it }
                Text("${stages.sumOf { it.days }} days in all", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ stages = stages + stages.last().copy(note = "", clip = "") }) { Text("+ Add stage") }
                TextButton({ stages = TIB_RAISES }) { Text("Use the tib raise program") }
            }
        },
        confirmButton = { TextButton({ onSave(stages, remind); onDismiss() }) { Text(if (isNew) "Start" else "Save") } },
        dismissButton = {
            Row {
                if (onEnd != null) TextButton(
                    { onEnd(); onDismiss() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("End habit") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
}
