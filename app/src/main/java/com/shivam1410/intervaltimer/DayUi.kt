package com.shivam1410.intervaltimer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

private const val HOUR = 60 * MIN

/** One day: totals, a work/break wave over the hours, and every stretch. Today includes the running session. */
@Composable
fun DayScreen(date: LocalDate, onDate: (LocalDate) -> Unit, onBack: () -> Unit) {
    val days by History.days.collectAsState()
    val stored by History.timelines.collectAsState()
    val session by Timer.session.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val plan = Timer.plan
    val live = session.running(plan) && History.dateOf(session.startedAt) == date
    val segs = remember(stored, session, now, date) { dropBlips(joined(History.timeline(date, session, plan, now)), keepLast = live) }
    val liveTotals = if (live) credit(plan, session, now) else Session()
    val day = days.firstOrNull { it.date == date }
    val work = (day?.workMs ?: 0) + liveTotals.workMs
    val rest = (day?.breakMs ?: 0) + liveTotals.breakMs
    val cycles = (day?.cycles ?: 0) + liveTotals.cycles
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        // Arrows either side of the title, as the board has them: a day you want to compare with is the one
        // next to it, and going back to the list to pick it loses your place every time.
        ScreenBar(
            if (date == History.today()) "Today" else date.format(DateTimeFormatter.ofPattern("EEEE d MMM")),
            "What actually happened", onBack,
        ) {
            RoundIcon(R.drawable.ic_chevron_down, "Previous day", Modifier.rotate(90f)) { onDate(date.minusDays(1)) }
            if (date < History.today()) RoundIcon(R.drawable.ic_chevron_down, "Next day", Modifier.rotate(-90f)) { onDate(date.plusDays(1)) }
        }
        val routines = Routines.all.collectAsState().value.filterNot { it.deleted }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            // A day you didn't work prints three zeros under three headings if you let it; a dash says
            // the same thing without scoring it.
            val routinesDone = routines.count { date in it.done }
            EndTile("FOCUSED", if (work > 0) fmtDur(work) else "—", Modifier.weight(1f))
            EndTile("CYCLES", if (cycles > 0) "$cycles" else "—", Modifier.weight(1f))
            EndTile("ROUTINES", if (routinesDone > 0) "$routinesDone of ${routines.size}" else "—", Modifier.weight(1f))
        }

        Panel("HOW THE DAY WENT") {
            if (segs.isEmpty()) {
                // "Nothing recorded" was a lie on a day of routines and habits: this panel is the workday only.
                val msg = if (work + rest > 0) "This day only has totals: the timeline is recorded from v1.8.0 on."
                else "No workday was run on this day."
                Text(msg, color = cs.onSurfaceVariant)
            } else {
                DayWave(segs)
                Legend()
            }
        }

        if (segs.isNotEmpty() || work + rest > 0) {
            Panel("SUMMARY") {
                if (rest > 0) Line("Break", fmtDur(rest))
                val ambientMs = History.ambient.collectAsState().value[date] ?: 0
                if (ambientMs > 0) Line("🎧 Ambient sounds (not break)", fmtDur(ambientMs))
                if (segs.isNotEmpty()) {
                    Line("Started", clock(segs.first().start))
                    Line("Finished", if (live) "still going" else clock(segs.last().end))
                    val over = segs.filter { it.over }.sumOf { it.ms }
                    if (over > 0) Line("Overtime", fmtDur(over))
                }
            }
        }

        // One list, in the order it happened: the work blocks, the routines and the habits together.
        // They used to be two panels — the blocks under a heading that said "Stretches", and everything
        // else below them — so a day you had actually lived read as two unrelated days.
        val items by Library.items.collectAsState()
        val habitTimes by Habits.times.collectAsState()
        val blocks = segs.map {
            Happening(minuteOfDay(it.start), clock(it.start), kindLabel(it), fmtDur(it.ms), "ends ${clock(it.end)}", if (it.work) 0 else 1)
        }
        val routineLines = routines.mapNotNull { r ->
            r.done[date]?.let { min -> Happening(min, clockOfDay(min), "${r.emoji} ${r.name}", "", "${r.steps.size} steps", 2) }
        }
        // A habit ticked off in the calendar long afterwards has no minute to show, and goes last.
        val habitsDone = Habits.all.collectAsState().value.filter { date in it.done }.map { h ->
            habitTimes[date]?.get(h.id).let { Happening(it, it?.let(::clockOfDay) ?: "—", habitTitle(h, items), "", "Done", 3) }
        }
        val lines = (blocks + routineLines + habitsDone).sortedWith(compareBy(nullsLast()) { it.at })
        // The day as a line you read downwards, not a two-column table: a dot per thing, the thread between
        // them, and what it was in a card of its own.
        if (lines.isNotEmpty()) {
            Text(
                "WHAT ACTUALLY HAPPENED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
            )
            lines.forEachIndexed { i, h -> Happened(h, last = i == lines.lastIndex) }
        }
        Box(Modifier.height(24.dp))
    }
}


/** One thing that happened: [at] is its minute of day (null = no time recorded, so it goes last). */
private data class Happening(val at: Int?, val time: String, val name: String, val len: String, val meta: String, val kind: Int)

/** One row of the day's thread: the time, the dot and its line, and the thing itself in a card. */
@Composable
private fun Happened(h: Happening, last: Boolean) {
    val cs = MaterialTheme.colorScheme
    val tint = when (h.kind) {
        0 -> cs.primary
        1 -> cs.secondary
        2 -> cs.secondary
        else -> cs.tertiary
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            h.time, Modifier.width(46.dp).padding(top = 10.dp), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant, textAlign = TextAlign.End,
        )
        Column(Modifier.width(11.dp).fillMaxHeight().padding(start = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 13.dp).size(9.dp).clip(RoundedCornerShape(5.dp)).background(tint))
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(cs.outlineVariant))
        }
        Column(
            Modifier.weight(1f).padding(start = 11.dp, top = 3.dp, bottom = 9.dp).clip(RoundedCornerShape(12.dp))
                .background(cs.surface).border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(h.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                if (h.len.isNotEmpty()) Text(
                    h.len, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Text(
                h.meta, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/** The same clock, for a time of day with no date behind it (a routine records the minute it finished). */
private fun clockOfDay(minuteOfDay: Int): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(
    java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60); set(java.util.Calendar.MINUTE, minuteOfDay % 60)
    }.time,
)

private fun kindLabel(s: Segment) = (if (s.work) "Work" else if (s.label.isNotEmpty()) activityLabel(s.label) else "Break") + if (s.over) " (overtime)" else ""

@Composable
private fun segmentColor(s: Segment): Color {
    val cs = MaterialTheme.colorScheme
    return when {
        s.over -> if (s.work) cs.tertiary else cs.tertiary.copy(alpha = 0.55f)
        s.work -> cs.primary
        else -> cs.secondary.copy(alpha = 0.55f)
    }
}

/**
 * The day as a wave: time runs left to right over whole hours; work stands tall, breaks sit low,
 * and pauses drop to the baseline. Overtime gets its own colour.
 */
@Composable
internal fun DayWave(segs: List<Segment>) {
    val cs = MaterialTheme.colorScheme
    val (from, to) = hourSpan(segs, ZoneId.systemDefault())
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val hourFmt = DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "H" else "ha")
    val colors = segs.map { segmentColor(it) }
    val grid = cs.outlineVariant
    val outline = cs.onSurface.copy(alpha = 0.55f)

    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val labelH = 20.dp.toPx()
        val base = size.height - labelH
        val tall = base - 6.dp.toPx()
        val span = (to - from).toFloat()
        fun x(ms: Long) = (ms - from) / span * size.width
        fun top(s: Segment) = base - tall * (if (s.work) 1f else 0.42f)

        val hours = ((to - from) / HOUR).toInt()
        val every = when {
            hours <= 6 -> 1
            hours <= 12 -> 2
            else -> 3
        }
        for (i in 0..hours) {
            val gx = x(from + i * HOUR)
            drawLine(grid, Offset(gx, 0f), Offset(gx, base), strokeWidth = 1f)
            if (i % every == 0) {
                val text = Instant.ofEpochMilli(from + i * HOUR).atZone(ZoneId.systemDefault()).format(hourFmt).lowercase()
                val m = measurer.measure(text, labelStyle)
                val lx = (gx - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
                drawText(m, topLeft = Offset(lx, base + 4.dp.toPx()))
            }
        }
        drawLine(grid, Offset(0f, base), Offset(size.width, base), strokeWidth = 2f)

        segs.forEachIndexed { i, s ->
            val t = top(s)
            drawRoundRect(
                colors[i], topLeft = Offset(x(s.start), t), size = Size((x(s.end) - x(s.start)).coerceAtLeast(1.5f), base - t),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }

        // The outline: one stepped line over the blocks, rounded at the corners so it reads as a wave.
        val path = Path().apply {
            moveTo(x(segs.first().start), base)
            var prevEnd = segs.first().start
            segs.forEach { s ->
                if (s.start - prevEnd > MIN) {
                    lineTo(x(prevEnd), base)
                    lineTo(x(s.start), base)
                }
                lineTo(x(s.start), top(s))
                lineTo(x(s.end), top(s))
                prevEnd = s.end
            }
            lineTo(x(prevEnd), base)
        }
        drawPath(path, outline, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(6.dp.toPx())))
    }
}

@Composable
private fun Legend() {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Work" to cs.primary, "Break" to cs.secondary.copy(alpha = 0.55f), "Overtime" to cs.tertiary).forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Text(name, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
