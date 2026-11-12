package com.shivam1410.intervaltimer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate

/** Home, under Habits: one chip per routine; "☀️ Morning ✓ 8:14" once it's done today. Tap to run it. */
@Composable
fun RoutinesRow(onOpen: (String) -> Unit) {
    val all by Routines.all.collectAsState()
    val routines = all.filterNot { it.deleted }
    if (routines.isEmpty()) return
    val today = LocalDate.now()
    Text("Routines", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        routines.forEach { r ->
            val done = doneToday(r, today)
            FilterChip(done != null, { onOpen(r.id) }, { Text("${r.emoji} ${r.name}" + (done?.let { " ✓ ${fmtMinute(it)}" } ?: "")) })
        }
    }
}

/** One step at a time, big: ✓ Done moves on, Back / Skip are small. The end records the time and says so. */
@Composable
fun RoutineScreen(id: String, onBack: () -> Unit) {
    val all by Routines.all.collectAsState()
    val r = all.firstOrNull { it.id == id && !it.deleted } ?: return onBack()
    val cs = MaterialTheme.colorScheme
    var step by remember(id) { mutableIntStateOf(0) }
    val finished = step >= r.steps.size

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text("${r.emoji} ${r.name}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp).weight(1f))
            if (!finished) Text("Step ${step + 1} of ${r.steps.size}", color = cs.onSurfaceVariant)
        }
        // Progress dots: done steps filled.
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            r.steps.indices.forEach { i -> Box(Modifier.size(10.dp).clip(CircleShape).background(if (i < step) cs.primary else cs.surfaceContainerHighest)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (finished) {
                val at = doneToday(r, LocalDate.now())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✓", fontSize = 64.sp, color = cs.primary)
                    Text("${r.name} done" + (at?.let { " · ${fmtMinute(it)}" } ?: ""), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                }
            } else {
                Text(r.steps[step], fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        if (finished) {
            Button(onBack, Modifier.fillMaxWidth().padding(bottom = 24.dp).height(64.dp)) { Text("Close", fontSize = 20.sp) }
        } else {
            Button(
                { step++; if (step >= r.steps.size) Routines.finish(r.id) },
                Modifier.fillMaxWidth().height(72.dp),
            ) { Text("✓  Done", fontSize = 22.sp) }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ step-- }, enabled = step > 0) { Text("Back") }
                TextButton({ step++; if (step >= r.steps.size) Routines.finish(r.id) }) { Text("Skip") }
            }
        }
    }
}

/** Settings › Habits & routines: each routine (tap to edit) and New routine. */
@Composable
fun RoutinesSettings() {
    val all by Routines.all.collectAsState()
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<Routine?>(null) }
    Text("Routines", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        all.filterNot { it.deleted }.forEach { r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer)
                    .clickable(onClickLabel = "Edit ${r.name}") { editing = r }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.emoji, fontSize = 22.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${r.steps.size} steps" + (r.remindMin?.let { " · reminder ${fmtMinute(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text("›", fontSize = 24.sp, color = cs.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh)
                .clickable(onClickLabel = "New routine") { editing = Routine(java.util.UUID.randomUUID().toString().take(8), "⭐", "", listOf(""), null, emptyMap(), 0) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("➕", fontSize = 20.sp)
            Text("New routine", Modifier.padding(start = 12.dp))
        }
    }
    editing?.let { r -> RoutineEditor(r, isNew = all.none { it.id == r.id }) { editing = null } }
}

/** Name, emoji, steps (edit, add, remove, move up) and a daily reminder; Save, Delete or Cancel. */
@Composable
private fun RoutineEditor(r: Routine, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(r.name) }
    var emoji by remember { mutableStateOf(r.emoji) }
    var steps by remember { mutableStateOf(r.steps) }
    var remind by remember { mutableStateOf(r.remindMin) }
    val clean = steps.map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New routine" else "Edit routine") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.clip(2) }, Modifier.width(72.dp), singleLine = true, label = { Text("Icon") }, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(name, { name = it.clip(40) }, Modifier.weight(1f), singleLine = true, label = { Text("Name") }, shape = RoundedCornerShape(12.dp))
                }
                Text("Steps, in order", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            s, { v -> steps = steps.mapIndexed { j, x -> if (j == i) v.clip(60) else x } },
                            Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                        )
                        if (i > 0) IconButton({ steps = steps.toMutableList().apply { add(i - 1, removeAt(i)) }.toList() }) { Text("↑") }
                        IconButton({ steps = steps.filterIndexed { j, _ -> j != i } }) { Text("×", style = MaterialTheme.typography.titleLarge) }
                    }
                }
                TextButton({ steps = steps + "" }) { Text("+ Add step") }
                ReminderRow(remind) { remind = it }
            }
        },
        confirmButton = {
            TextButton({ Routines.save(r.copy(name = name.trim(), emoji = emoji.ifBlank { "⭐" }, steps = clean, remindMin = remind)); onDismiss() },
                enabled = name.isNotBlank() && clean.isNotEmpty()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton({ Routines.delete(r.id); onDismiss() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
}
