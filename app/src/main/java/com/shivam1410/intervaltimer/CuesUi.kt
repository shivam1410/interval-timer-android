package com.shivam1410.intervaltimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The cue for this moment ("This break · Drink water") with a ✓ that tucks it away; nothing if [cue] is null. */
@Composable
fun CueCard(heading: String, cue: String?, key: Any) {
    cue ?: return
    var dismissed by remember(key) { mutableStateOf(false) }
    if (dismissed) return
    val cs = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(heading, style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer)
                Text(cue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSecondaryContainer)
            }
            FilledTonalButton({ dismissed = true }) { Text("✓") }
        }
    }
}

/** Settings › Notifications › Cues: on/off and the three lists, each line editable, + to add, × to remove. */
@Composable
fun CuesSection() {
    val c by Cues.lists.collectAsState()
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }
    val day = epochDay(System.currentTimeMillis())
    val breaks = c.breaks.filter { it.isNotBlank() }
    // Today's actual rotation, not the stored order: the list walks a step further every morning, and a
    // rotation you cannot see reads as a bug the first time break 1 is not the line at the top.
    val preview = buildList {
        c.start.filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }?.let { add("START" to it) }
        if (breaks.isNotEmpty()) repeat(minOf(2, breaks.size)) { i ->
            add("BREAK ${i + 1}" to breaks[(i + day).mod(breaks.size.toLong()).toInt()])
        }
        endCue(c)?.let { add("END" to it) }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
            .border(1.5.dp, if (c.on) cs.secondary else cs.outlineVariant, RoundedCornerShape(16.dp)).padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cues", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (c.on) Text(
                "ROTATING", Modifier.clip(RoundedCornerShape(999.dp)).background(cs.secondaryContainer)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = cs.onSecondaryContainer,
            )
            Switch(c.on, { Cues.save(c.copy(on = it)) }, Modifier.padding(start = 9.dp))
        }
        Text(
            "One line at the start, on each break, and at the end. It changes by itself so it keeps landing.",
            Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant, lineHeight = 17.sp,
        )
        if (c.on) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                preview.forEach { (whenLabel, text) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh)
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            whenLabel, Modifier.width(56.dp), style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = cs.onSurfaceVariant,
                        )
                        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
            TextButton({ editing = !editing }, Modifier.padding(top = 4.dp)) {
                Text(if (editing) "Done" else "Edit the list")
            }
            if (editing) {
                CueListEditor("Start of the day", c.start) { Cues.save(c.copy(start = it)) }
                CueListEditor("Breaks · one each break, in turn", c.breaks) { Cues.save(c.copy(breaks = it)) }
                CueListEditor("End of the day", c.end) { Cues.save(c.copy(end = it)) }
            }
        }
    }
}

@Composable
private fun CueListEditor(title: String, lines: List<String>, onChange: (List<String>) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { i, line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    line, { v -> onChange(lines.mapIndexed { j, l -> if (j == i) v.clip(60) else l }) },
                    Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                )
                IconButton({ onChange(lines.filterIndexed { j, _ -> j != i }) }) { Text("×", style = MaterialTheme.typography.titleLarge) }
            }
        }
        TextButton({ onChange(lines + "") }) { Text("+ Add") }
    }
}
