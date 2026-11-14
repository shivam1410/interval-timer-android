package com.shivam1410.intervaltimer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.mutableLongStateOf
import java.time.LocalDate

/** Home, under Habits: one chip per routine still to do today, by time ("10:00  ⭐ 10 am"). Tap to run it. */
@Composable
fun RoutinesRow(onOpen: (String) -> Unit) {
    val all by Routines.all.collectAsState()
    val habits by Habits.all.collectAsState()
    val today = LocalDate.now()
    // Done today: off Home until tomorrow (its time stays in Settings › Habits & routines and its calendar).
    // A stretch added after you finished brings it back, because there is something in it to do again.
    val routines = all.filter { !it.deleted && routineDoneToday(it, habits, today) == null }.sortedBy { it.remindMin ?: Int.MAX_VALUE }
    if (routines.isEmpty()) return
    Text("Routines", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        routines.forEach { r ->
            FilterChip(false, { onOpen(r.id) }, { Text((r.remindMin?.let { "${fmtMinute(it)}  " } ?: "") + "${r.emoji} ${r.name}") })
        }
    }
}

/** One step at a time, big: ✓ Done moves on, Back / Skip are small. The end records the time and says so. */
@Composable
fun RoutineScreen(id: String, onBack: () -> Unit) {
    val all by Routines.all.collectAsState()
    val r = all.firstOrNull { it.id == id && !it.deleted } ?: return onBack()
    val cs = MaterialTheme.colorScheme
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val today = LocalDate.now()
    // Opens where you left off: the habits you already did today are behind you, not steps to click through.
    var step by remember(id) { mutableIntStateOf(firstUndoneStep(r, habits, today)) }
    val finished = step >= r.steps.size
    val session by Timer.session.collectAsState()
    val timed = session.quick == "routine:$id" // this routine's own timer, running or just finished
    var started by remember(id) { mutableStateOf(false) }
    fun next() {
        step++
        if (step >= r.steps.size) {
            Routines.finish(r.id)
            TogglSync.onRoutine(null, 0)
            if (timed) Timer.stop() // banks the time in History; the routine is over, however long it took
            Timer.ping(Cue.DONE)
        } else Timer.ping(Cue.SOFT)
    }
    // The start card, until you start: not while its timer runs, and not when you already did a step today.
    if (!started && !timed && step == 0 && doneToday(r, today) == null) {
        return RoutineStartCard(r, onBack) { started = true }
    }
    // Toggl: a timer from opening the routine until it's finished or you leave it.
    DisposableEffect(id) {
        TogglSync.onRoutine(r, System.currentTimeMillis())
        onDispose { TogglSync.onRoutine(null, 0) }
    }
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
        if (timed) RoutineClock(session)
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

/**
 * Before a routine starts: what's in it, how long it should take, and one big Start. Picking a length
 * saves it to the routine, so tomorrow it's already right; Off means no timer, just the steps.
 */
@Composable
private fun RoutineStartCard(r: Routine, onBack: () -> Unit, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var min by remember(r.id) { mutableStateOf(r.minutes) }
    val stretches = routineSteps(r).count { it is HabitStep }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text("${r.emoji} ${r.name}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("${r.steps.size} steps" + if (stretches > 0) " \u00b7 $stretches stretches" else "", style = MaterialTheme.typography.headlineSmall)
            min?.let { Text("about $it min", color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
            Text("Timer", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(min == null, { min = null }, { Text("Off") })
                listOf(10, 15, 20, 30).forEach { m -> FilterChip(min == m, { min = m }, { Text("$m min") }) }
            }
        }
        Button(
            {
                // Saved first, so the engine reads this length when it builds the timer's single phase.
                if (min != r.minutes) Routines.save(r.copy(minutes = min))
                if (min != null) {
                    TogglSync.onRoutine(null, 0) // the session's own entry takes over from here
                    Timer.startQuick("routine:${r.id}")
                }
                onStart()
            },
            Modifier.fillMaxWidth().padding(bottom = 24.dp).height(64.dp),
        ) { Text(min?.let { "\u25b6  Start \u00b7 $it min" } ?: "Start", fontSize = 20.sp) }
    }
}

/** The routine's own timer while it runs: the time left, Pause and End; "Time's up" once it's over. */
@Composable
private fun RoutineClock(session: Session) {
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val over = session.idle || session.finishedAt > 0 || session.left(now) == 0L
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (over) "Time's up" else "\u23f1 ${fmt(session.left(now))}",
            style = MaterialTheme.typography.titleMedium, color = if (over) cs.error else cs.primary, modifier = Modifier.weight(1f),
        )
        if (!over) {
            TextButton({ if (session.paused) Timer.resume() else Timer.pause() }) { Text(if (session.paused) "Resume" else "Pause") }
            TextButton(Timer::stop) { Text("End timer") }
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
    var level by remember { mutableStateOf(Level.BEGINNER) }
    val clean = steps.map { it.trim() }.filter { it.isNotEmpty() }
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val all by Routines.all.collectAsState()
    // Habits you can add: running and not already in a routine (this one included).
    val taken = habitsInRoutines(all.filter { it.id != r.id }) + steps.mapNotNull { (stepOf(it) as? HabitStep)?.habitId }
    val free = habits.filter { !it.ended && it.id !in taken }
    // Stretches you can start here: the catalog, minus the ones already running.
    // ponytail: matched by the name a catalog habit is started with ("Supraspinatus · 6 presses · Beginner").
    val catalog = CATALOG.filterNot { e -> habits.any { !it.ended && it.title.startsWith(e.name) } }
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text("Add a stretch") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (free.isNotEmpty()) {
                    Text("Already running", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    free.forEach { h ->
                        Text(habitTitle(h, items), Modifier.fillMaxWidth().clickable { steps = steps.filter { it.isNotBlank() } + habitStep(h.id); picking = false }.padding(vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (catalog.isNotEmpty()) {
                    Text("Stretch catalog", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Level.entries.forEach { lv -> FilterChip(level == lv, { level = lv }, { Text(lv.label) }) }
                    }
                    catalog.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                // Starts it with no reminder of its own: the routine's time is when you do it.
                                val id = Habits.start("", planFor(e, level), null, "${e.name} · ${level.label}")
                                steps = steps.filter { it.isNotBlank() } + habitStep(id)
                                picking = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(56.dp)) { MovementDemo(e.demos.first(), 56.dp) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(e.name, style = MaterialTheme.typography.bodyLarge)
                                Text(e.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
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
                    TextButton({ picking = true }) { Text("+ Add a stretch") }
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

/**
 * A routine's record: the last 5 weeks, Mon–Sun, a ✓ and the time on each day you finished it, today outlined;
 * with the streak and how many of the last 30 days.
 */
@Composable
fun RoutineCalendarScreen(id: String, onBack: () -> Unit) {
    val all by Routines.all.collectAsState()
    val r = all.firstOrNull { it.id == id && !it.deleted } ?: return onBack()
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text("${r.emoji} ${r.name}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        val streak = routineStreak(r, today)
        Text(
            "${doneInLast(r, today, 30)} of the last 30 days" + if (streak > 1) " · $streak days in a row" else "",
            color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        val firstMonday = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(4)
        (0 until 5).forEach { w ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d ->
                    val date = firstMonday.plusDays(w * 7L + d)
                    val at = r.done[date]
                    val shape = RoundedCornerShape(8.dp)
                    var m = Modifier.weight(1f).height(48.dp).clip(shape).background(if (at != null) cs.primaryContainer else cs.surfaceContainer)
                    if (date == today) m = m.border(2.dp, cs.onSurface, shape)
                    Box(m, contentAlignment = Alignment.Center) {
                        val fg = when { at != null -> cs.onPrimaryContainer; date.isAfter(today) -> cs.onSurface.copy(alpha = 0.3f); else -> cs.onSurfaceVariant }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = fg)
                            if (at != null) Text(fmtMinute(at), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = fg)
                        }
                    }
                }
            }
        }
        Text("A day fills in when you finish the routine.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}
