package com.shivam1410.intervaltimer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        open != null -> DayScreen(LocalDate.parse(open), onDate = { day = it.toString() }) { day = null }
        calendar -> CalendarScreen(onPick = { day = it.toString() }) { calendar = false }
        else -> Overview(onBack, onDay = { day = it.toString() }, onCalendar = { calendar = true })
    }
}

@Composable
private fun Overview(onBack: () -> Unit, onDay: (LocalDate) -> Unit, onCalendar: () -> Unit) {
    val days by History.days.collectAsState()
    val today = History.today()
    val byDate = days.associateBy { it.date }
    val dense = density(days, today)
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenBar("Progress", onBack = onBack) {
            RoundIcon(R.drawable.ic_calendar, "Calendar", onClick = onCalendar)
        }

        // Density, not a streak: a number that can be destroyed by one bad Tuesday earns avoidance, not effort.
        // The board gives it a card of its own, so the measure the screen is built around reads as the answer
        // and not as a heading over the panels below it.
        Column(
            Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surface)
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp)).padding(18.dp),
        ) {
            Text(
                if (dense.worthShowing) "${dense.done} of the last ${dense.of} days" else "Nothing here yet",
                fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-1.4).sp, fontWeight = FontWeight.Bold,
            )
            Text(
                if (dense.worthShowing) "You showed up on ${dense.done}. Missing one costs nothing — there is no streak to break."
                else "Finish a work block and it lands here. A day counts once one full block is done.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp),
            )
        }

        val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
        Panel("LAST 7 DAYS · ${fmtDur(week.sumOf { byDate[it]?.workMs ?: 0 })} focused") { WeekBars(week, byDate) }
        Panel("LAST 12 WEEKS · tap a day") { Heatmap(today, byDate, onDay) }

        // Per habit, the same forgiving measure as the day's: how often, out of how long it has existed.
        // A habit with fewer than three finishes is left out rather than shown as a near-zero.
        val items by Library.items.collectAsState()
        val habitRows = Habits.all.collectAsState().value
            .filterNot { it.ended }.map { it to habitDensity(it, today) }.filter { it.second.worthShowing }
        // A card each, as the board has them: one habit's fortnight shouldn't have to be picked out of a list.
        if (habitRows.isNotEmpty()) {
            Text(
                "BY HABIT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            habitRows.forEach { (h, d) ->
                Column(
                    Modifier.padding(bottom = 9.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(cs.surface).border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            habitTitle(h, items), Modifier.weight(1f), fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${d.done} of ${d.of}", Modifier.padding(start = 10.dp), fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = cs.secondary,
                        )
                    }
                    // The count says how often, the strip says which days — the gaps are the useful part.
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                        habitDays(h, today).forEach { done ->
                            Box(
                                Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(2.5.dp))
                                    .background(if (done) cs.primary else cs.surfaceContainerHighest),
                            )
                        }
                    }
                }
            }
        }

        val active = days.filter { it.active }
        val total = days.sumOf { it.workMs }
        // Four zeros under a headline that already says "Nothing here yet" is the printed zero the rest
        // of this screen is careful to avoid. The panel arrives with the first day worth counting.
        if (days.isNotEmpty()) Panel("ALL TIME") {
            Line("Active days", "${active.size}")
            Line("Average per active day", if (active.isEmpty()) "—" else fmtDur(active.sumOf { it.workMs } / active.size))
        }

        // Break activities (from the timelines) and home ambient listening, last 30 days.
        val timelines by History.timelines.collectAsState()
        val ambient by History.ambient.collectAsState()
        val since = today.minusDays(29)
        // An activity you haven't done in a month is a zero printed under a heading — the one thing
        // the rest of this screen refuses to do. It comes back the first minute it's used again.
        val perActivity = activityTotals(timelines.filterKeys { !it.isBefore(since) }.values.flatten())
            .entries.filter { it.value > 0 }.sortedByDescending { it.value }
        val ambientMs = ambient.filterKeys { !it.isBefore(since) }.values.sum()
        Panel("ACTIVITIES · last 30 days") {
            if (perActivity.isEmpty() && ambientMs == 0L) Text("Breaks and quick timers show here by activity.", color = cs.onSurfaceVariant)
            perActivity.forEach { (id, ms) -> Line(activityLabel(id), fmtDur(ms)) }
            if (ambientMs > 0) Line("🎧 Ambient sounds (not break)", fmtDur(ambientMs))
        }

        // Its own "nothing yet" said the headline's line over again, in smaller type.
        if (days.isNotEmpty()) Panel("RECENT DAYS") {
            val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
            days.sortedByDescending { it.date }.take(10).forEach { d ->
                Line(
                    if (d.date == today) "Today" else d.date.format(fmt), "${fmtDur(d.workMs)} · ${d.cycles} ${if (d.cycles == 1) "cycle" else "cycles"}",
                    Modifier.clickable { onDay(d.date) },
                )
            }
        }
        // The board closes on the three numbers worth carrying away, under a rule.
        if (days.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(top = 24.dp), color = cs.outlineVariant)
            val routineRuns = Routines.all.collectAsState().value.sumOf { it.done.size }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                EndTile("FOCUSED", fmtDur(total), Modifier.weight(1f))
                EndTile("CYCLES", "${days.sumOf { it.cycles }}", Modifier.weight(1f))
                EndTile("ROUTINES", "$routineRuns", Modifier.weight(1f))
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
    // The boards label a section in small caps with air between the letters, not a coloured heading.
    Text(
        title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
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
