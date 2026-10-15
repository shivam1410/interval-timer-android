package com.shivam1410.intervaltimer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Month calendar of past days, shaded like the heatmap; tap a day to open it. */
@Composable
fun CalendarScreen(onPick: (LocalDate) -> Unit, onBack: () -> Unit) {
    val days by History.days.collectAsState()
    val byDate = days.associateBy { it.date }
    val today = History.today()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val ym = YearMonth.parse(month)
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text("Calendar", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ month = ym.minusMonths(1).toString() }) {
                Icon(painterResource(R.drawable.ic_arrow_back), "Previous month", tint = cs.onSurface)
            }
            Text(
                ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            val canNext = ym < YearMonth.from(today)
            IconButton({ month = ym.plusMonths(1).toString() }, enabled = canNext) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back), "Next month", Modifier.rotate(180f),
                    tint = if (canNext) cs.onSurface else cs.onSurface.copy(alpha = 0.3f),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        val lead = ym.atDay(1).dayOfWeek.value - 1 // Monday first
        val cells = lead + ym.lengthOfMonth()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat((cells + 6) / 7) { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(7) { col ->
                        val n = week * 7 + col - lead + 1
                        val date = if (n in 1..ym.lengthOfMonth()) ym.atDay(n) else null
                        DayCell(date, today, byDate[date]?.cycles ?: 0, Modifier.weight(1f), onPick)
                    }
                }
            }
        }

        val inMonth = days.filter { YearMonth.from(it.date) == ym }
        Panel("This month") {
            Line("Focused", fmtDur(inMonth.sumOf { it.workMs }))
            Line("Cycles completed", "${inMonth.sumOf { it.cycles }}")
            Line("Active days", "${inMonth.count { it.active }}")
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun DayCell(date: LocalDate?, today: LocalDate, cycles: Int, modifier: Modifier, onPick: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val usable = date != null && !date.isAfter(today)
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
            .background(if (usable) shade(cycles) else Color.Transparent)
            .then(if (date == today) Modifier.border(2.dp, cs.primary, RoundedCornerShape(8.dp)) else Modifier)
            .then(if (usable && date != null) Modifier.clickable { onPick(date) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Text(
                "${date.dayOfMonth}", fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                color = if (usable) onShade(cycles) else cs.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}
