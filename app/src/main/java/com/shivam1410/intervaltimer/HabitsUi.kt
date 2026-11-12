package com.shivam1410.intervaltimer

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
 * Settings › Habits: your running habits (tap for its calendar), the catalog of fixed 30-day plans
 * (tap to see and tweak the whole plan before starting), and "Make your own". [open] takes
 * "habit:<id>" or "plan:<catalog id>".
 */
@Composable
fun HabitsPage(open: (String) -> Unit) {
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val running = habits.filter { !it.ended }
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    var custom by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (running.isNotEmpty()) {
            Text("Your habits", style = MaterialTheme.typography.titleSmall, color = cs.primary)
            running.forEach { h ->
                val day = h.dayOf(today)
                val todayLine = when {
                    today in h.done -> "Completed today ✓"
                    else -> h.stageOn(day)?.let { "Today: ${label(it)}" } ?: "Finished"
                }
                Row(card().clickable(onClickLabel = "Open calendar") { open("habit:${h.id}") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(habitTitle(h, items), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Day ${day.coerceAtMost(h.totalDays)}/${h.totalDays} · $todayLine", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Text("›", fontSize = 24.sp, color = cs.onSurfaceVariant)
                }
            }
        }
        Text("Catalog · 30-day plans", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 8.dp))
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
        Row(card().clickable(onClickLabel = "Make your own habit") { custom = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✏️", fontSize = 20.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Make your own", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Any exercise, your own numbers", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
    if (custom) HabitStartSheet(null) { custom = false }
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
            "Week 1: ${label(stages.first())} · then one more set each week",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Text("Your 30 days, from today", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(bottom = 4.dp))
        PlanGrid(draft, todayN = 1) { editing = it }
        Text("Tap a day to change it before you start.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        ReminderRow(remind) { remind = it }
        Button(
            { Habits.start("", stages, remind, "${e.name} · ${level.label}"); onStarted() },
            Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp),
        ) { Text("Start") }
    }
    editing?.let { d -> DayEditDialog(draft, d, onDismiss = { editing = null }) { stages = it } }
}
