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
fun DayScreen(date: LocalDate, onBack: () -> Unit) {
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            val title = if (date == History.today()) "Today" else date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Big("🎯 Focused", fmtDur(work), Modifier.weight(1f).fillMaxHeight(), cs.primaryContainer, cs.onPrimaryContainer)
            Big("🌿 Break", fmtDur(rest), Modifier.weight(1f).fillMaxHeight(), cs.secondaryContainer, cs.onSecondaryContainer)
        }

        Panel("How the day went") {
            if (segs.isEmpty()) {
                val msg = if (work + rest > 0) "This day only has totals: the timeline is recorded from v1.8.0 on." else "Nothing recorded on this day."
                Text(msg, color = cs.onSurfaceVariant)
            } else {
                DayWave(segs)
                Legend()
            }
        }

        if (segs.isNotEmpty() || work + rest > 0) {
            Panel("Summary") {
                Line("Cycles completed", "$cycles")
                if (segs.isNotEmpty()) {
                    Line("Started", clock(segs.first().start))
                    Line("Finished", if (live) "still going" else clock(segs.last().end))
                    val over = segs.filter { it.over }.sumOf { it.ms }
                    if (over > 0) Line("Overtime", fmtDur(over))
                }
            }
        }

        if (segs.isNotEmpty()) {
            Panel("Stretches") {
                segs.forEach { s -> Line("${clock(s.start)} – ${clock(s.end)}", "${kindLabel(s)} · ${fmtDur(s.ms)}") }
            }
        }
        Box(Modifier.height(24.dp))
    }
}

private fun clock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

private fun kindLabel(s: Segment) = (if (s.work) "Work" else "Break") + if (s.over) " (overtime)" else ""

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
private fun DayWave(segs: List<Segment>) {
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
