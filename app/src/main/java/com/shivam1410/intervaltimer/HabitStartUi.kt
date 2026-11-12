package com.shivam1410.intervaltimer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.collectAsState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class Template { PROGRAM, SAME, GROW }

/**
 * Start a habit: on its own ([item] null: you name it) or for a library exercise. Pick one of three templates, change at most a number or two, pick a length and a
 * reminder, Start. No stage list: the template builds the stages.
 */
@Composable
fun HabitStartSheet(item: LibraryItem?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.title.orEmpty()) }
    // A chip picked from My library links the habit to that exercise (its clip shows); typing unlinks it.
    var linked by remember { mutableStateOf(item) }
    val library = Library.items.collectAsState().value.filter { !it.deleted && it.title.isNotBlank() }
    val hasProgram = demoFor(name) == "tib-raise"
    // Until you pick one, a name that has a built-in program ("Tib raises") selects it.
    var picked by remember { mutableStateOf<Template?>(null) }
    val template = picked?.takeIf { it != Template.PROGRAM || hasProgram } ?: if (hasProgram) Template.PROGRAM else Template.SAME
    var days by remember { mutableIntStateOf(30) }
    var sets by remember { mutableIntStateOf(1) }
    var count by remember { mutableIntStateOf(10) } // reps, or seconds when [hold]
    var add by remember { mutableIntStateOf(10) }
    var hold by remember { mutableStateOf(false) }
    var remind by remember { mutableStateOf<Int?>(Habits.DEFAULT_REMIND_MIN) }
    val unit = if (hold) "s" else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "New habit" else "${item.title}: habit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item == null) {
                    // One tap instead of typing: the built-in program, then your library exercises.
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(name == "Tib raises" && linked == null, { name = "Tib raises"; linked = null }, { Text("Tib raises") })
                        library.filter { it.title != "Tib raises" }.forEach { ex ->
                            FilterChip(linked?.id == ex.id, { name = ex.title; linked = ex }, { Text(ex.title, maxLines = 1) })
                        }
                    }
                    androidx.compose.material3.OutlinedTextField(
                        name, { name = it.take(60); linked = null }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                        label = { Text("Or type a name") }, placeholder = { Text("Stairs, shoulder push…") },
                    )
                }
                if (Habits.active != null) Text("This replaces your current habit.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (hasProgram) TemplateCard("Tib raises program", "10 → 20 → lean forward → 2 × 20, over 30 days", template == Template.PROGRAM) {
                    picked = Template.PROGRAM
                }
                TemplateCard("Same every day", "$sets × $count$unit".trim(), template == Template.SAME) { picked = Template.SAME }
                if (template == Template.SAME) {
                    Stepper(if (hold) "Seconds" else "Reps", count, unit, 1..300) { count = it }
                    Stepper("Sets", sets, "", 1..10) { sets = it }
                }
                TemplateCard("Grow each week", "start $count$unit · +$add$unit a week", template == Template.GROW) { picked = Template.GROW }
                if (template == Template.GROW) {
                    Stepper(if (hold) "Start (s)" else "Start reps", count, unit, 1..300) { count = it }
                    Stepper("Add each week", add, unit, 1..100) { add = it }
                }
                if (template != Template.PROGRAM) TextButton({ hold = !hold; count = if (hold) 30 else 10 }) {
                    Text(if (hold) "Count reps instead" else "Hold for seconds instead")
                }
                if (template != Template.PROGRAM) {
                    Text("Length", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(14, 30, 60).forEach { d -> FilterChip(days == d, { days = d }, { Text("$d days") }) }
                    }
                }
                ReminderRow(remind) { remind = it }
            }
        },
        confirmButton = {
            TextButton({
                val stages = when (template) {
                    Template.PROGRAM -> TIB_RAISES
                    Template.SAME -> sameEveryDay(days, sets, if (hold) 0 else count, if (hold) count else 0)
                    Template.GROW -> growWeekly(days, 1, count, add, hold)
                }
                // A linked habit keeps no name of its own, so renaming the exercise renames it too.
                Habits.start(linked?.id.orEmpty(), stages, remind, if (linked == null) name.trim() else "")
                onDismiss()
            }, enabled = name.isNotBlank()) { Text("Start") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TemplateCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant), shape)
            .clickable(onClickLabel = "Choose $title", onClick = onClick).padding(12.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/** Daily reminder: a switch, and when on, the time (tap to change). */
@Composable
fun ReminderRow(remind: Int?, onChange: (Int?) -> Unit) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Remind me", Modifier.weight(1f))
        Switch(remind != null, { on -> onChange(if (on) Habits.DEFAULT_REMIND_MIN else null) })
    }
    remind?.let { m ->
        FilledTonalButton({
            android.app.TimePickerDialog(ctx, { _, h, mm -> onChange(h * 60 + mm) }, m / 60, m % 60, android.text.format.DateFormat.is24HourFormat(ctx)).show()
        }) { Text("Every day at ${LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}") }
    }
}
