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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    Section("Cues") {
        Toggle(R.drawable.ic_gong, "A short prompt at the start of the day, each break and the end", c.on) { Cues.save(c.copy(on = it)) }
        if (c.on) {
            CueListEditor("Start of the day", c.start) { Cues.save(c.copy(start = it)) }
            CueListEditor("Breaks (one each break, in turn)", c.breaks) { Cues.save(c.copy(breaks = it)) }
            CueListEditor("End of the day", c.end) { Cues.save(c.copy(end = it)) }
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
