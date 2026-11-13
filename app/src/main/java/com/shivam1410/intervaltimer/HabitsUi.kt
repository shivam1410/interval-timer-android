package com.shivam1410.intervaltimer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import java.time.LocalDate

/**
 * Settings › Habits & routines: your day by time. Each time ("10:00", then Anytime) lists its routines (tap to
 * edit; their habits and steps inside) and the habits with their own reminder (tap for the calendar). Below:
 * Browse stretches, Make your own and New routine. [open] takes "habit:<id>" or "stretches".
 */
@Composable
fun HabitsPage(open: (String) -> Unit) {
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val routines by Routines.all.collectAsState()
    val cs = MaterialTheme.colorScheme
    var custom by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Routine?>(null) }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        daySlots(habits, routines).forEach { slot ->
            Text(slot.min?.let(::fmtMinute) ?: "Anytime", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 12.dp))
            slot.routines.forEach { r -> RoutineRow(r, habits, items, onEdit = { editing = r }, onHabit = { open("habit:$it") }) }
            slot.habits.forEach { h -> Column(card().clickable(onClickLabel = "Open calendar") { open("habit:${h.id}") }.padding(16.dp)) { HabitLine(h, items) } }
        }
        Text("Add", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 20.dp))
        AddRow("🤸", "Browse stretches", "${CATALOG.size} ready-made 30-day plans") { open("stretches") }
        AddRow("✏️", "Make your own habit", "Any exercise, your own numbers") { custom = true }
        AddRow("➕", "New routine", "Steps and habits you do together, at a time") {
            editing = Routine(java.util.UUID.randomUUID().toString().take(8), "⭐", "", listOf(""), null, emptyMap(), 0)
        }
    }
    editing?.let { r -> RoutineEditor(r, isNew = routines.none { it.id == r.id }) { editing = null } }
    if (custom) HabitStartSheet(null) { custom = false }
}

/** A habit's name and "Day 3/30 · Today: 1 × 10" (or "Completed today ✓"). */
@Composable
private fun HabitLine(h: Habit, items: List<LibraryItem>, small: Boolean = false) {
    val today = LocalDate.now()
    val day = h.dayOf(today)
    val todayLine = if (today in h.done) "Completed today ✓" else h.stageOn(day)?.let { "Today: ${label(it)}" } ?: "Finished"
    Text(habitTitle(h, items), style = if (small) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text("Day ${day.coerceAtMost(h.totalDays)}/${h.totalDays} · $todayLine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A routine (tap to edit) with what's in it: its habits (tap for the calendar) and its other steps. */
@Composable
private fun RoutineRow(r: Routine, habits: List<Habit>, items: List<LibraryItem>, onEdit: () -> Unit, onHabit: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val done = doneToday(r, LocalDate.now())
    Column(card()) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Edit ${r.name}", onClick = onEdit).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.emoji, fontSize = 22.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${r.steps.size} steps" + (done?.let { " · ✓ done ${fmtMinute(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text("Edit", color = cs.primary)
        }
        routineSteps(r).forEach { st ->
            when (st) {
                is HabitStep -> habits.firstOrNull { it.id == st.habitId && !it.ended }?.let { h ->
                    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Open calendar") { onHabit(h.id) }.padding(start = 50.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) { HabitLine(h, items, small = true) }
                }
                is TextStep -> Text("• ${st.text}", Modifier.padding(start = 50.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Box(Modifier.height(10.dp))
    }
}

/** A row at the bottom of the page: an icon, a title and a line under it. */
@Composable
private fun AddRow(icon: String, title: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(card().clickable(onClickLabel = title, onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 20.sp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Text("›", fontSize = 24.sp, color = cs.onSurfaceVariant)
    }
}

/** Settings › Habits & routines › Stretches: the catalog of fixed 30-day plans; tap one to see and tweak it ("plan:<id>"). */
@Composable
fun StretchesPage(open: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CATALOG.forEach { e ->
            Row(card().clickable(onClickLabel = "See the plan") { open("plan:${e.id}") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(72.dp)) { MovementDemo(e.demos.first(), 72.dp) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(e.blurb, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text("›", fontSize = 24.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** A list row's background: full width, rounded, card colour. */
@Composable
private fun card(): Modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)

/**
 * A catalog plan before you start it: pick a level, see the whole 30 days as tiles (each day's sets × reps),
 * tap a day to change it (that day or from then on), set the reminder, then Start. Nothing runs until Start.
 */
@Composable
fun PlanScreen(entryId: String, onBack: () -> Unit, onStarted: () -> Unit) {
    val e = CATALOG.firstOrNull { it.id == entryId } ?: return onBack()
    val cs = MaterialTheme.colorScheme
    var level by remember { mutableStateOf(Level.BEGINNER) }
    var stages by remember(level) { mutableStateOf(planFor(e, level)) } // a new level starts from its plan again
    var remind by remember { mutableStateOf<Int?>(Habits.DEFAULT_REMIND_MIN) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var inRoutine by remember { mutableStateOf<String?>(null) }
    val draft = Habit("draft", "", LocalDate.now(), stages, remind, emptySet(), 0, title = e.name)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text(e.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        Text(e.blurb, color = cs.onSurfaceVariant)
        MovementDemo(e.demos.first(), 160.dp)
        Text("Level", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Level.entries.forEach { lv ->
                FilterChip(level == lv, { level = lv }, { Text(lv.label) })
            }
        }
        Text(
            if (e.grow) "Week 1: ${label(stages.first())} · then one more set each week" else "Every day: ${label(stages.first())}",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Text("Your 30 days, from today", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(bottom = 4.dp))
        PlanGrid(draft, todayN = 1) { editing = it }
        Text("Tap a day to change it before you start.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        // Do it as part of a routine (its time reminds you), or on its own with its own reminder.
        val routines by Routines.all.collectAsState()
        val choices = routines.filterNot { it.deleted }
        if (choices.isNotEmpty()) {
            Text("Do it in", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(inRoutine == null, { inRoutine = null }, { Text("On its own") })
                choices.forEach { r ->
                    FilterChip(inRoutine == r.id, { inRoutine = r.id }, { Text("${r.emoji} ${r.name}" + (r.remindMin?.let { " · ${fmtMinute(it)}" } ?: "")) })
                }
            }
        }
        if (inRoutine == null) ReminderRow(remind) { remind = it }
        Button(
            {
                val id = Habits.start("", stages, if (inRoutine == null) remind else null, "${e.name} · ${level.label}")
                inRoutine?.let { Routines.addHabit(it, id) }
                onStarted()
            },
            Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp),
        ) { Text("Start") }
    }
    editing?.let { d -> DayEditDialog(draft, d, onDismiss = { editing = null }) { stages = it } }
}
