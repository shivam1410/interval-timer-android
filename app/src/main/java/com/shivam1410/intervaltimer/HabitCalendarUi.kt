package com.shivam1410.intervaltimer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The running habit as a grid of day tiles (Mon–Sun rows): each shows the date and that day's target.
 * Done = filled with ✓, missed = dimmed, today = outlined. Tap today or a later day to change its target;
 * tap a past day to mark it done or not done.
 */
@Composable
fun HabitCalendarScreen(onBack: () -> Unit) {
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val h = habits.firstOrNull { !it.ended }
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<Int?>(null) }
    var ending by remember { mutableStateOf(false) }
    val title = h?.let { habitTitle(it, items) } ?: "Habit"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        if (h == null) {
            Text("No habit running. Start one from Home.", color = cs.onSurfaceVariant)
            return@Column
        }
        val today = LocalDate.now()
        val todayN = h.dayOf(today)
        Text(
            "Day ${todayN.coerceIn(1, h.totalDays)} of ${h.totalDays} · ${h.doneCount} done",
            color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        // Rows start on the Monday of the start date's week; blanks before day 1 and after the last day.
        val lead = h.start.dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = List(lead) { null } + (1..h.totalDays).toList()
        cells.chunked(7).forEach { week ->
            week.filterNotNull().firstOrNull { h.isStageStart(it) && !h.stageOn(it)?.note.isNullOrBlank() }?.let { d ->
                // Tiles show dates, so the line names the date the change starts on ("From Thu 19: …").
                val date = h.start.plusDays(d - 1L)
                val dow = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                Text(if (d == 1) "${h.stageOn(d)?.note}" else "From $dow ${date.dayOfMonth}: ${h.stageOn(d)?.note}", style = MaterialTheme.typography.bodySmall, color = cs.primary, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    if (d == null) Spacer(Modifier.weight(1f))
                    else DayTile(h, d, todayN, Modifier.weight(1f)) {
                        if (d < todayN) Habits.markDone(h.id, h.start.plusDays(d - 1L), h.start.plusDays(d - 1L) !in h.done)
                        else editing = d
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("Tap today or a later day to change it. Tap a past day to mark it done.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        ReminderRow(h.remindMin) { Habits.edit(h.id, h.stages, it) }
        TextButton({ ending = true }, colors = ButtonDefaults.textButtonColors(contentColor = cs.error), modifier = Modifier.padding(vertical = 8.dp)) { Text("End habit") }
    }

    h ?: return
    editing?.let { d -> DayEditDialog(h, d) { editing = null } }
    if (ending) AlertDialog(
        onDismissRequest = { ending = false },
        title = { Text("End $title?") },
        text = { Text("Your done days stay in this habit's record; Home stops showing it.") },
        confirmButton = { TextButton({ Habits.end(h.id); ending = false; onBack() }) { Text("End habit") } },
        dismissButton = { TextButton({ ending = false }) { Text("Keep going") } },
    )
}

@Composable
private fun DayTile(h: Habit, d: Int, todayN: Int, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val date = h.start.plusDays(d - 1L)
    val done = date in h.done
    val missed = d < todayN && !done
    val shape = RoundedCornerShape(8.dp)
    val bg = when { done -> cs.primaryContainer; else -> cs.surfaceContainer }
    val fg = when { done -> cs.onPrimaryContainer; missed -> cs.onSurfaceVariant.copy(alpha = 0.5f); else -> cs.onSurface }
    var m = modifier.height(56.dp).clip(shape).background(bg)
    if (d == todayN) m = m.border(2.dp, cs.onSurface, shape)
    Box(m.clickable(onClickLabel = if (d < todayN) "Mark day $d" else "Change day $d", onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = fg)
            Text(if (done) "✓" else h.stageOn(d)?.let(::shortLabel).orEmpty(), fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}

/** Tile-sized target: "10", "2×20", "30s". */
private fun shortLabel(s: Stage) = (if (s.sets > 1) "${s.sets}×" else "") + (if (s.holdSec > 0) "${s.holdSec}s" else "${s.reps}")

/** Change [day]'s target: sets, reps (or seconds), for this day only or from this day on. */
@Composable
private fun DayEditDialog(h: Habit, day: Int, onDismiss: () -> Unit) {
    val s = h.stageOn(day) ?: return
    val hold = s.holdSec > 0
    var sets by remember { mutableIntStateOf(s.sets) }
    var count by remember { mutableIntStateOf(if (hold) s.holdSec else s.reps) }
    var onlyThis by remember { mutableStateOf(false) }
    val date = h.start.plusDays(day - 1L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Day $day · ${date.dayOfMonth} ${date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper(if (hold) "Seconds" else "Reps", count, if (hold) "s" else "", 1..600) { count = it }
                Stepper("Sets", sets, "", 1..10) { sets = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!onlyThis, { onlyThis = false }, { Text("From this day on") })
                    FilterChip(onlyThis, { onlyThis = true }, { Text("This day only") })
                }
            }
        },
        confirmButton = {
            TextButton({
                Habits.edit(h.id, setFrom(h, day, sets, if (hold) 0 else count, if (hold) count else 0, onlyThis), h.remindMin)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
