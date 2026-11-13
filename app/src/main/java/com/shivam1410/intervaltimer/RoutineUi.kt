package com.shivam1410.intervaltimer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate

/** Home, under Habits: one chip per routine, by time ("10:00  ⭐ 10 am ✓ 10:24" once it's done today). Tap to run it. */
@Composable
fun RoutinesRow(onOpen: (String) -> Unit) {
    val all by Routines.all.collectAsState()
    val routines = all.filterNot { it.deleted }.sortedBy { it.remindMin ?: Int.MAX_VALUE }
    if (routines.isEmpty()) return
    val today = LocalDate.now()
    Text("Routines", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        routines.forEach { r ->
            val done = doneToday(r, today)
            FilterChip(done != null, { onOpen(r.id) }, { Text((r.remindMin?.let { "${fmtMinute(it)}  " } ?: "") + "${r.emoji} ${r.name}" + (done?.let { " ✓ ${fmtMinute(it)}" } ?: "")) })
        }
    }
}

/** One step at a time, big: ✓ Done moves on, Back / Skip are small. The end records the time and says so. */
@Composable
fun RoutineScreen(id: String, onBack: () -> Unit) {
    val all by Routines.all.collectAsState()
    val r = all.firstOrNull { it.id == id && !it.deleted } ?: return onBack()
    val cs = MaterialTheme.colorScheme
    var step by remember(id) { mutableIntStateOf(0) }
    val finished = step >= r.steps.size
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val today = LocalDate.now()
    fun next() { step++; if (step >= r.steps.size) Routines.finish(r.id) }
    // A habit step's habit, when it can be done today (null: a text step, or a habit that's gone or finished).
    val current = routineSteps(r).getOrNull(step)
    val habit = (current as? HabitStep)?.let { s -> habits.firstOrNull { it.id == s.habitId && !it.ended } }
    val stage = habit?.let { it.stageOn(it.dayOf(today)) }
    LaunchedEffect(step, current, stage == null) {
        if (current is HabitStep && stage == null) next() // ended, deleted or past its 30 days: nothing to do here
    }
    val habitToDo = habit != null && stage != null && today !in habit.done

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text("${r.emoji} ${r.name}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp).weight(1f))
            if (!finished) Text("Step ${step + 1} of ${r.steps.size}", color = cs.onSurfaceVariant)
        }
        // Progress dots: done steps filled.
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            r.steps.indices.forEach { i -> Box(Modifier.size(10.dp).clip(CircleShape).background(if (i < step) cs.primary else cs.surfaceContainerHighest)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (finished) {
                val at = doneToday(r, LocalDate.now())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✓", fontSize = 64.sp, color = cs.primary)
                    Text("${r.name} done" + (at?.let { " · ${fmtMinute(it)}" } ?: ""), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                }
            } else if (habit != null && stage != null) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(habitTitle(habit, items), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    if (habitToDo) key(habit.id) { HabitDoer(habit, items, stage, habit.dayOf(today), onDone = ::next) }
                    else Text("✓ Done today", style = MaterialTheme.typography.headlineSmall, color = cs.primary)
                }
            } else if (current is TextStep) {
                Text(current.text, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        if (finished) {
            Button(onBack, Modifier.fillMaxWidth().padding(bottom = 24.dp).height(64.dp)) { Text("Close", fontSize = 20.sp) }
        } else {
            // A habit still to do has its own Completed / Start; otherwise the big ✓ moves on.
            if (!habitToDo) Button(::next, Modifier.fillMaxWidth().height(72.dp)) { Text(if (habit != null) "Next" else "✓  Done", fontSize = 22.sp) }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ step-- }, enabled = step > 0) { Text("Back") }
                TextButton(::next) { Text("Skip") }
            }
        }
    }
}

/** Name, emoji, steps (edit, add, remove, move up) and a daily reminder; Save, Delete or Cancel. */
@Composable
internal fun RoutineEditor(r: Routine, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(r.name) }
    var emoji by remember { mutableStateOf(r.emoji) }
    var steps by remember { mutableStateOf(r.steps) }
    var remind by remember { mutableStateOf(r.remindMin) }
    var picking by remember { mutableStateOf(false) }
    val clean = steps.map { it.trim() }.filter { it.isNotEmpty() }
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val all by Routines.all.collectAsState()
    // Habits you can add: running and not already in a routine (this one included).
    val taken = habitsInRoutines(all.filter { it.id != r.id }) + steps.mapNotNull { (stepOf(it) as? HabitStep)?.habitId }
    val free = habits.filter { !it.ended && it.id !in taken }
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text("Add a habit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                free.forEach { h ->
                    Text(habitTitle(h, items), Modifier.fillMaxWidth().clickable { steps = steps.filter { it.isNotBlank() } + habitStep(h.id); picking = false }.padding(vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton({ picking = false }) { Text("Cancel") } },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New routine" else "Edit routine") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.clip(2) }, Modifier.width(72.dp), singleLine = true, label = { Text("Icon") }, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(name, { name = it.clip(40) }, Modifier.weight(1f), singleLine = true, label = { Text("Name") }, shape = RoundedCornerShape(12.dp))
                }
                Text("Steps, in order", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hs = stepOf(s) as? HabitStep
                        if (hs != null) {
                            // A habit: done with its figure and count when the routine runs; not editable as text.
                            val h = habits.firstOrNull { it.id == hs.habitId }
                            Text("🏋 " + (h?.let { habitTitle(it, items) } ?: "Removed habit"), Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        } else OutlinedTextField(
                            s, { v -> steps = steps.mapIndexed { j, x -> if (j == i) v.clip(60) else x } },
                            Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                        )
                        if (i > 0) IconButton({ steps = steps.toMutableList().apply { add(i - 1, removeAt(i)) }.toList() }) { Text("↑") }
                        IconButton({ steps = steps.filterIndexed { j, _ -> j != i } }) { Text("×", style = MaterialTheme.typography.titleLarge) }
                    }
                }
                Row {
                    TextButton({ steps = steps + "" }) { Text("+ Add step") }
                    if (free.isNotEmpty()) TextButton({ picking = true }) { Text("+ Add habit") }
                }
                ReminderRow(remind) { remind = it }
            }
        },
        confirmButton = {
            TextButton({ Routines.save(r.copy(name = name.trim(), emoji = emoji.ifBlank { "⭐" }, steps = clean, remindMin = remind)); onDismiss() },
                enabled = name.isNotBlank() && clean.isNotEmpty()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton({ Routines.delete(r.id); onDismiss() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
}
