package com.shivam1410.intervaltimer

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val days by History.days.collectAsState()
    val today = History.today()
    val byDate = days.associateBy { it.date }
    val (current, best) = streaks(days, today)
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onBack) { Text("←", fontSize = 24.sp) }
            Text("History", style = MaterialTheme.typography.headlineSmall)
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
        Panel("Consistency · last 12 weeks") { Heatmap(today, byDate) }

        val active = days.filter { it.active }
        val total = days.sumOf { it.workMs }
        Panel("All time") {
            Line("Focused", fmtDur(total))
            Line("Cycles completed", "${days.sumOf { it.cycles }}")
            Line("Active days", "${active.size}")
            Line("Average per active day", if (active.isEmpty()) "—" else fmtDur(active.sumOf { it.workMs } / active.size))
        }

        Panel("Recent days") {
            if (days.isEmpty()) {
                Text("No workdays recorded yet. Finish or end a workday and it shows up here.", color = cs.onSurfaceVariant)
            }
            val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
            days.sortedByDescending { it.date }.take(14).forEach { d ->
                Line(if (d.date == today) "Today" else d.date.format(fmt), "${fmtDur(d.workMs)} · ${d.cycles} ${if (d.cycles == 1) "cycle" else "cycles"}")
            }
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun Big(label: String, value: String, modifier: Modifier, bg: Color, fg: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Line(k: String, v: String) {
    Row {
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

/** GitHub-style grid: one column per week (Mon at top), shade by cycles finished. */
@Composable
private fun Heatmap(today: LocalDate, byDate: Map<LocalDate, Day>) {
    val cs = MaterialTheme.colorScheme
    val start = today.with(DayOfWeek.MONDAY).minusWeeks(11)
    fun shade(cycles: Int): Color =
        if (cycles == 0) cs.surfaceContainerHighest else cs.primary.copy(alpha = 0.3f + 0.7f * (cycles.coerceAtMost(8) / 8f))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "", "W", "", "F", "", "S").forEach {
                Box(Modifier.size(width = 14.dp, height = 18.dp), contentAlignment = Alignment.Center) {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
        repeat(12) { w ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { d ->
                    val date = start.plusDays((w * 7 + d).toLong())
                    val color = if (date.isAfter(today)) Color.Transparent else shade(byDate[date]?.cycles ?: 0)
                    Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)).background(color))
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
