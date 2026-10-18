package com.shivam1410.intervaltimer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    var day by rememberSaveable { mutableStateOf<String?>(null) } // ISO date of the open day
    var calendar by rememberSaveable { mutableStateOf(false) }
    BackHandler(day != null || calendar) { if (day != null) day = null else calendar = false }
    val open = day
    when {
        open != null -> DayScreen(LocalDate.parse(open)) { day = null }
        calendar -> CalendarScreen(onPick = { day = it.toString() }) { calendar = false }
        else -> Overview(onBack, onDay = { day = it.toString() }, onCalendar = { calendar = true })
    }
}

@Composable
private fun Overview(onBack: () -> Unit, onDay: (LocalDate) -> Unit, onCalendar: () -> Unit) {
    val days by History.days.collectAsState()
    val today = History.today()
    val byDate = days.associateBy { it.date }
    val (current, best) = streaks(days, today)
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp).weight(1f))
            IconButton(onCalendar, Modifier.offset(x = 24.dp)) { Icon(painterResource(R.drawable.ic_calendar), "Calendar", tint = cs.onSurface) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Big("🔥 Current streak", "$current ${if (current == 1) "day" else "days"}", Modifier.weight(1f).fillMaxHeight(), cs.primaryContainer, cs.onPrimaryContainer)
            Big("🏆 Best streak", "$best ${if (best == 1) "day" else "days"}", Modifier.weight(1f).fillMaxHeight(), cs.tertiaryContainer, cs.onTertiaryContainer)
        }
        Text(
            "A day counts when at least one full work block is finished.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
        )

        val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
        Panel("Last 7 days · ${fmtDur(week.sumOf { byDate[it]?.workMs ?: 0 })} focused") { WeekBars(week, byDate) }
        Panel("Consistency · last 12 weeks · tap a day") { Heatmap(today, byDate, onDay) }

        val active = days.filter { it.active }
        val total = days.sumOf { it.workMs }
        Panel("All time") {
            Line("Focused", fmtDur(total))
            Line("Cycles completed", "${days.sumOf { it.cycles }}")
            Line("Active days", "${active.size}")
            Line("Average per active day", if (active.isEmpty()) "—" else fmtDur(active.sumOf { it.workMs } / active.size))
        }

        // Break activities (from the timelines) and home ambient listening, last 30 days.
        val timelines by History.timelines.collectAsState()
        val ambient by History.ambient.collectAsState()
        val since = today.minusDays(29)
        val perActivity = activityTotals(timelines.filterKeys { !it.isBefore(since) }.values.flatten()).entries.sortedByDescending { it.value }
        val ambientMs = ambient.filterKeys { !it.isBefore(since) }.values.sum()
        Panel("Activities · last 30 days") {
            if (perActivity.isEmpty() && ambientMs == 0L) Text("Breaks and quick timers show here by activity.", color = cs.onSurfaceVariant)
            perActivity.forEach { (id, ms) -> Line(activityLabel(id), fmtDur(ms)) }
            if (ambientMs > 0) Line("🎧 Ambient sounds (not break)", fmtDur(ambientMs))
        }

        Panel("Recent days") {
            if (days.isEmpty()) {
                Text("No workdays recorded yet. Finish or end a workday and it shows up here.", color = cs.onSurfaceVariant)
            }
            val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
            days.sortedByDescending { it.date }.take(10).forEach { d ->
                Line(
                    if (d.date == today) "Today" else d.date.format(fmt), "${fmtDur(d.workMs)} · ${d.cycles} ${if (d.cycles == 1) "cycle" else "cycles"}",
                    Modifier.clickable { onDay(d.date) },
                )
            }
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
internal fun Big(label: String, value: String, modifier: Modifier, bg: Color, fg: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun Panel(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
internal fun Line(k: String, v: String, modifier: Modifier = Modifier) {
    Row(modifier) {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.SemiBold)
    }
}

/** Focus time for the last 7 days; a full 8-hour day of work (6h40m) fills the bar. */
@Composable
private fun WeekBars(week: List<LocalDate>, byDate: Map<LocalDate, Day>) {
    val cs = MaterialTheme.colorScheme
    val max = maxOf(400 * MIN, week.maxOf { byDate[it]?.workMs ?: 0 })
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        week.forEach { date ->
            val ms = byDate[date]?.workMs ?: 0
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (ms > 0) "%.1fh".format(ms / 3_600_000f) else "", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Box(
                    Modifier.padding(vertical = 4.dp).fillMaxWidth().height((100f * ms / max).coerceAtLeast(4f).dp)
                        .clip(RoundedCornerShape(6.dp)).background(if (ms > 0) cs.primary else cs.surfaceContainerHighest)
                )
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (date == History.today()) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Heatmap / calendar colour for a day with [cycles] finished. */
@Composable
internal fun shade(cycles: Int): Color {
    val cs = MaterialTheme.colorScheme
    return if (cycles == 0) cs.surfaceContainerHighest else cs.primary.copy(alpha = 0.3f + 0.7f * (cycles.coerceAtMost(8) / 8f))
}

/** Text colour on a [shade]d cell. */
@Composable
internal fun onShade(cycles: Int): Color = if (cycles >= 5) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

/** GitHub-style grid: one column per week (Mon at top), shade by cycles finished; tap opens the day. */
@Composable
private fun Heatmap(today: LocalDate, byDate: Map<LocalDate, Day>, onDay: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val start = today.with(DayOfWeek.MONDAY).minusWeeks(11)
    // One row per weekday (Mon at top), a label for every day, 12 square cells sharing the width.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { d, label ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(14.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                repeat(12) { w ->
                    val date = start.plusDays((w * 7 + d).toLong())
                    val cycles = byDate[date]?.cycles ?: 0
                    val future = date.isAfter(today)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp))
                            .background(if (future) Color.Transparent else shade(cycles))
                            .then(if (future) Modifier else Modifier.clickable { onDay(date) }),
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        listOf(0, 2, 4, 6, 8).forEach { Box(Modifier.size(12.dp).aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(shade(it))) }
        Text("More", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.End)
    }
}
