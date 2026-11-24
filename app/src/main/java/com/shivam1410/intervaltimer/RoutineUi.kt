package com.shivam1410.intervaltimer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.runtime.mutableLongStateOf
import java.time.LocalDate


/**
 * One step at a time, big: ✓ Done moves on, Back / Skip are small. The end records the time and says so.
 * [autoStart] (a reminder's Start) presses the start card's own button for you, timer and all.
 */
@Composable
fun RoutineScreen(id: String, autoStart: Boolean = false, onBack: () -> Unit) {
    val all by Routines.all.collectAsState()
    val r = all.firstOrNull { it.id == id && !it.deleted } ?: return onBack()
    val cs = MaterialTheme.colorScheme
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val today = LocalDate.now()
    // Opens where you left off: the habits you already did today are behind you, not steps to click through.
    var step by remember(id) { mutableIntStateOf(firstUndoneStep(r, habits, today)) }
    val finished = step >= r.steps.size
    val session by Timer.session.collectAsState()
    val timed = session.quick == "routine:$id" // this routine's own timer, running or just finished
    var started by remember(id) { mutableStateOf(false) }
    val ctx = LocalContext.current
    // The step you're on. A timer step runs a quick timer or a library item where the step is.
    val current = routineSteps(r).getOrNull(step)
    val act = (current as? ActivityStep)?.let { stepActivity(it.activityId) }
    val actRunning = act != null && session.quick == act.id && !session.idle
    // One timer at a time, the routine's own included: the guard asks before a step's timer takes over.
    val startTimer = rememberStartGuard { actId ->
        val lib = items.firstOrNull { "lib:${it.id}" == actId && !it.deleted }
        if (lib != null) startLibraryItem(ctx, lib) // opens its video or file too, as on home
        else {
            Timer.startQuick(actId)
            Strava.record(ctx, actId) // a walk also records in Strava, as it does from home
        }
    }
    // When the step on screen began, so finishing it measures how long it took. Reset on every move,
    // including Back and Skip, so the next step is timed from when you actually reached it.
    var stepAt by remember(id) { mutableLongStateOf(System.currentTimeMillis()) }
    // When this sitting began, so the end can say how long it took rather than only when it finished.
    val runFrom = remember(id) { System.currentTimeMillis() }
    var tookMs by remember(id) { mutableLongStateOf(0L) }
    fun next(finished: Boolean = true) {
        if (actRunning) Timer.stop() // leaving a step whose timer still runs banks that time in History
        // Skipped or stepped back over: not a measurement of doing it, so it teaches the estimate nothing.
        if (finished) r.steps.getOrNull(step)?.let { Routines.timeStep(it, System.currentTimeMillis() - stepAt) }
        stepAt = System.currentTimeMillis()
        step++
        if (step >= r.steps.size) {
            tookMs = System.currentTimeMillis() - runFrom
            Routines.finish(r.id)
            TogglSync.onRoutine(null, 0)
            if (timed) Timer.stop() // banks the time in History; the routine is over, however long it took
            Timer.ping(Cue.DONE)
        } else Timer.ping(Cue.SOFT)
    }
    // Every step already done elsewhere — a routine of nothing but stretches, ticked off from the Library
    // — leaves it finished without ever having been run. Record it, or the screen says "done" while the
    // day's record, the calendar and Home all go on saying it never happened.
    LaunchedEffect(finished) { if (finished && doneToday(r, today) == null) Routines.finish(r.id) }

    // The start card, until you start: not while its timer runs, and not once the routine is over. It used
    // to be skipped the moment one step had been done today, dropping you into the middle of the list with
    // no sight of what was already behind you — which is exactly when the overview is worth having.
    if (!started && !timed && !finished && doneToday(r, today) == null) {
        return RoutineStartCard(r, habits, autoStart, onBack) { fromTop -> if (fromTop) step = 0; started = true }
    }
    // Toggl: a timer from opening the routine until it's finished or you leave it.
    DisposableEffect(id) {
        TogglSync.onRoutine(r, System.currentTimeMillis())
        onDispose { TogglSync.onRoutine(null, 0) }
    }
    // A habit step's habit, when it can be done today (null: another kind of step, or a habit that's gone or finished).
    val habit = (current as? HabitStep)?.let { s -> habits.firstOrNull { it.id == s.habitId && !it.ended } }
    val stage = habit?.let { it.stageOn(it.dayOf(today)) }
    LaunchedEffect(step, current, stage == null) {
        if (current is HabitStep && stage == null) next() // ended, deleted or past its 30 days: nothing to do here
    }
    val habitToDo = habit != null && stage != null && today !in habit.done

    val steps = routineSteps(r)
    val typical by Routines.typical.collectAsState()
    val leftMin = stepsLeftMs(r.steps.drop(step), typical)?.let { ((it + 30_000L) / 60_000L).toInt().coerceAtLeast(1) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(R.drawable.ic_chevron_down, "Leave the routine", onClick = onBack)
            Text(
                "${r.emoji} ${r.name.uppercase()}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 1,
            )
            Spacer(Modifier.width(34.dp))
        }
        if (finished) {
            // The end of a routine is a record, not a trophy: what it was, when, and how often you keep it.
            val at = doneToday(r, today)
            Column(
                Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                    Text("✓", fontSize = 40.sp, color = cs.onPrimary)
                }
                Text(
                    "${r.name} done", Modifier.padding(top = 20.dp), fontSize = 32.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Text(
                    "${r.steps.size} steps" +
                        (tookMs.takeIf { it > 30_000L }?.let { " in ${spelledMinutes(it)}" } ?: "") +
                        (at?.let { " · finished ${fmtMinute(it)}" } ?: ""),
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
                if (at != null) Column(
                    Modifier.fillMaxWidth().padding(top = 26.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
                        .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        "ADDED TO TODAY'S LINE", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = cs.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtMinute(at), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
                        Box(Modifier.padding(horizontal = 11.dp).size(9.dp).clip(CircleShape).background(cs.primary))
                        Text("${r.name} · ${r.steps.size} steps", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        // The line records how long it took, as the day screen's rows do.
                        if (tookMs > 30_000L) Text(
                            fmtDur(tookMs), Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                        )
                    }
                }
                DensityDots(routineDensity(r, today), r.done.keys, long = true)
            }
            TextButton({ step = 0; started = true }, Modifier.align(Alignment.CenterHorizontally)) { Text("Run it again") }
            Button(
                onBack, Modifier.fillMaxWidth().padding(bottom = 20.dp).height(72.dp), shape = RoundedCornerShape(20.dp),
            ) { Text("Back to the day", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            return@Column
        }

        // How far through, as bars rather than a count you have to hold in your head.
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            r.steps.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i < step) cs.primary else cs.surfaceContainerHighest),
                )
            }
        }
        Text(
            "Step ${step + 1} of ${r.steps.size}" + (leftMin?.let { " · about $it min left" } ?: ""),
            Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
        if (timed) RoutineClock(session)

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            val kind = when {
                habit != null -> "STRETCH"
                current is ActivityStep -> "ON A TIMER"
                else -> "TICK IT OFF"
            }
            Text(
                kind, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp, color = cs.primary,
            )
            if (habit != null && stage != null) {
                Text(habitTitle(habit, items), Modifier.padding(top = 10.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                if (habitToDo) key(habit.id) { HabitDoer(habit, items, stage, habit.dayOf(today), onDone = ::next) }
                else Text("✓ Done today", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.headlineSmall, color = cs.primary)
            } else if (current is TextStep) {
                Text(current.text, Modifier.padding(top = 10.dp), fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
                Text("No timer. Do it, then tap.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            } else if (act != null) {
                Text(act.name, Modifier.padding(top = 10.dp), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(act.hint, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (actRunning) RoutineClock(session)
                else Button({ startTimer(act.id) }, Modifier.padding(top = 12.dp).height(56.dp)) {
                    Text("▶  Start · ${act.quickMin ?: DEFAULT_ROUTINE_MIN} min", fontSize = 18.sp)
                }
            } else if (current is ActivityStep) {
                Text("Timer removed", Modifier.padding(top = 10.dp), fontSize = 28.sp, color = cs.onSurfaceVariant)
            }
        }

        // Skip on the left, what is coming on the right: the board's quiet row above the one big button.
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ next(finished = false) }) { Text("Skip this") }
            Spacer(Modifier.weight(1f))
            stepLabel(steps.getOrNull(step + 1), habits, items, today)?.let { n ->
                Text(
                    "NEXT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp, color = cs.onSurfaceVariant,
                )
                Text(
                    n, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!habitToDo) Button(
            ::next, Modifier.fillMaxWidth().padding(bottom = 20.dp).height(72.dp), shape = RoundedCornerShape(20.dp),
        ) { Text("Done", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        else Spacer(Modifier.height(20.dp))
    }
}

/**
 * Before a routine starts: what's in it, how long it should take, and one big Start. Picking a length
 * saves it to the routine, so tomorrow it's already right; Off means no timer, just the steps.
 */
@Composable
private fun RoutineStartCard(r: Routine, habits: List<Habit>, autoStart: Boolean, onBack: () -> Unit, onStart: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var min by remember(r.id) { mutableStateOf(r.minutes) }
    // Asks first when a workday or another timer runs; the steps open only once the routine's timer is away.
    val start = rememberStartGuard { id -> Timer.startQuick(id); onStart(false) }
    fun go() {
        // Saved first, so the engine reads this length when it builds the timer's single phase.
        if (min != r.minutes) Routines.save(r.copy(minutes = min))
        if (min == null) return onStart(false)
        TogglSync.onRoutine(null, 0) // the session's own entry takes over from here
        start("routine:${r.id}")
    }
    // A reminder's Start: the same press, so a workday already running still gets asked about first.
    LaunchedEffect(Unit) { if (autoStart) go() }
    val today = History.today()
    val steps = routineSteps(r)
    val stretches = steps.count { it is HabitStep }
    val items by Library.items.collectAsState()
    val typical by Routines.typical.collectAsState()
    fun stepDone(i: Int) = (steps.getOrNull(i) as? HabitStep)?.habitId
        ?.let { id -> habits.any { it.id == id && !it.ended && today in it.done } } == true
    val alreadyDone = steps.indices.count(::stepDone)
    val leftMin = stepsLeftMs(r.steps.filterIndexed { i, _ -> !stepDone(i) }, typical)
        ?.let { ((it + 30_000L) / 60_000L).toInt().coerceAtLeast(1) }
    var editing by remember(r.id) { mutableStateOf(false) }
    var picking by remember(r.id) { mutableStateOf(false) }
    // A routine of one step is a quick timer that remembers: no progress to draw through a list of one,
    // so the board gives it the step itself, the reminder, and how often you have kept it.
    val lone = steps.singleOrNull()
    val loneAct = (lone as? ActivityStep)?.let { stepActivity(it.activityId) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundIcon(R.drawable.ic_chevron_down, "Back to the day", onClick = onBack)
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(r.emoji, fontSize = 17.sp) }
            Column(Modifier.weight(1f)) {
                Text(r.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (lone != null) "One step" + ((min ?: loneAct?.quickMin)?.let { " · $it minutes" } ?: "")
                    else r.remindMin?.let { "Reminds at ${fmtMinute(it)} · nudges at ${fmtMinute(it + NUDGE_MIN)}" }
                        ?: routineSummary(r, min),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1,
                )
            }
            RoundIcon(R.drawable.ic_edit, "Edit this routine") { editing = true }
        }

        // One bar per step says how far through you are; through a list of one it says nothing.
        if (lone == null) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                steps.indices.forEach { i ->
                    Box(
                        Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (stepDone(i)) cs.primary else cs.surfaceContainerHighest),
                    )
                }
            }
            Text(
                // "0 of 6 done" is the printed zero the rest of the app refuses: nothing done is a fact
                // about today, not a score. Once something is done the count says which.
                (if (alreadyDone == 0) "None done today" else "$alreadyDone of ${steps.size} done") +
                    (leftMin?.let { if (alreadyDone == 0) " · about $it minutes" else " · about $it minutes left" } ?: ""),
                Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        } else {
            // Why this exists at all, when the same timer is one tap away in the Library.
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp))
                    .background(cs.secondaryContainer.copy(alpha = 0.35f))
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(13.dp),
            ) {
                Text(
                    "A routine with one step is a quick timer that remembers",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                )
                Text(
                    "Same engine, same timer. What the routine adds is" +
                        (r.remindMin?.let { " the ${fmtMinute(it)} reminder and" } ?: "") +
                        " the record of whether you did it.",
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (lone != null) Text(
                "THE ONE STEP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, color = cs.onSurfaceVariant,
            )
            steps.forEachIndexed { i, st -> StepPreview(i + 1, st, habits, items, today, number = lone == null) }
            if (lone != null) Column(
                Modifier.fillMaxWidth().padding(top = 3.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.remindMin?.let { "Reminds you at ${fmtMinute(it)}" } ?: "No reminder",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Change",
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Edit this routine") { editing = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = cs.secondary,
                    )
                }
                DensityDots(routineDensity(r, today), r.done.keys, long = true)
            }
        }

        // The timer is a line about this run, not a row of chips competing with Start. A routine of one
        // step already has that step's timer, so a second one over the top of it is nothing but confusing.
        if (lone == null) Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface)
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(onClickLabel = "Change the timer") { picking = true }
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                min?.let { "Whole routine on a $it-minute timer" } ?: "No timer — just the steps",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Change", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = cs.secondary,
            )
        }
        if (picking && lone == null) Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(min == null, { min = null; picking = false }, { Text("Off") })
            listOf(10, 15, 20, 30).forEach { m -> FilterChip(min == m, { min = m; picking = false }, { Text("$m min") }) }
        }

        if (alreadyDone > 0) TextButton(
            { onStart(true) }, Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Start again from the top") }
        Button(
            { go() }, Modifier.fillMaxWidth().padding(bottom = 20.dp).height(72.dp), shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                (if (alreadyDone > 0) "Carry on" else loneAct?.let { "Start the ${it.name.lowercase()}" } ?: "Start") +
                    ((min ?: loneAct?.quickMin.takeIf { lone != null })?.let { " · $it min" } ?: ""),
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
    if (editing) RoutineEditor(r, isNew = false) { editing = false }
}

/** One step as it looks before you begin: what it is, and what today asks of it. */
@Composable
private fun StepPreview(
    n: Int, st: RoutineStep, habits: List<Habit>, items: List<LibraryItem>, today: java.time.LocalDate,
    number: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val habit = (st as? HabitStep)?.let { s -> habits.firstOrNull { it.id == s.habitId && !it.ended } }
    val stage = habit?.let { it.stageOn(it.dayOf(today)) }
    val act = (st as? ActivityStep)?.let { stepActivity(it.activityId) }
    val done = habit != null && today in habit.done
    val name = when (st) {
        is TextStep -> st.text
        is HabitStep -> habit?.let { habitTitle(it, items) } ?: "Stretch (gone)"
        is ActivityStep -> act?.name ?: "Timer (gone)"
    }
    val meta = when (st) {
        is TextStep -> "Tick it off"
        is HabitStep -> habit?.let { "Habit \u00b7 day ${it.dayOf(today)} of ${it.totalDays}" } ?: "No longer running"
        is ActivityStep -> "Quick timer" + (act?.id?.let { if (Strava.sportFor(it) != null) " · records in Strava" else "" } ?: "")
    }
    val right = when (st) {
        is TextStep -> ""
        is HabitStep -> stage?.let(::label).orEmpty()
        is ActivityStep -> act?.quickMin?.let { "$it min" }.orEmpty()
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (done) cs.surfaceContainerLow else cs.surfaceContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The number says where in the list this is; a list of one has nowhere else to be.
        if (number || done) Text(
            if (done) "\u2713" else "$n",
            style = MaterialTheme.typography.labelLarge,
            color = if (done) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                name, style = MaterialTheme.typography.titleSmall,
                color = if (done) cs.onSurfaceVariant else cs.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (right.isNotEmpty()) Text(right, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    }
}

/** The routine's own timer while it runs: the time left, Pause and End; "Time's up" once it's over. */
@Composable
private fun RoutineClock(session: Session) {
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val over = session.idle || session.finishedAt > 0 || session.left(now) == 0L
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (over) "Time's up" else "\u23f1 ${fmt(session.left(now))}",
            style = MaterialTheme.typography.titleMedium, color = if (over) cs.error else cs.primary, modifier = Modifier.weight(1f),
        )
        if (!over) {
            TextButton({ if (session.paused) Timer.resume() else Timer.pause() }) { Text(if (session.paused) "Resume" else "Pause") }
            TextButton(Timer::stop) { Text("End timer") }
        }
    }
}

/** Name, emoji, steps (edit, add, remove, move up) and a daily reminder; Save, Delete or Cancel. */
@Composable
internal fun RoutineEditor(r: Routine, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(r.name) }
    var emoji by remember { mutableStateOf(r.emoji) }
    var steps by remember { mutableStateOf(r.steps) }
    var remind by remember { mutableStateOf(r.remindMin) }
    var picking by remember { mutableStateOf(false) }
    var pickingTimer by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(Level.BEGINNER) }
    val clean = steps.map { it.trim() }.filter { it.isNotEmpty() }
    val habits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val all by Routines.all.collectAsState()
    // Habits you can add: running and not already in a routine (this one included).
    val taken = habitsInRoutines(all.filter { it.id != r.id }) + steps.mapNotNull { (stepOf(it) as? HabitStep)?.habitId }
    val free = habits.filter { !it.ended && it.id !in taken }
    // Stretches you can start here: the catalog, minus the ones already running.
    // ponytail: matched by the name a catalog habit is started with ("Supraspinatus · 6 presses · Beginner").
    val catalog = CATALOG.filterNot { e -> habits.any { !it.ended && it.title.startsWith(e.name) } }
    // Timers you can add: the built-in quick timers and your own library items, both run by the same engine.
    if (pickingTimer) AlertDialog(
        onDismissRequest = { pickingTimer = false },
        title = { Text("Add a timer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val timers = ACTIVITIES.filter { it.quickMin != null } + items.filterNot { it.deleted }.map { it.toActivity() }
                timers.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            steps = steps.filter { it.isNotBlank() } + actStep(a.id)
                            pickingTimer = false
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.emoji, Modifier.width(40.dp), style = MaterialTheme.typography.titleLarge)
                        Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("${a.quickMin ?: DEFAULT_ROUTINE_MIN} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton({ pickingTimer = false }) { Text("Cancel") } },
    )
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text("Add a stretch") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (free.isNotEmpty()) {
                    Text("Already running", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    free.forEach { h ->
                        Text(habitTitle(h, items), Modifier.fillMaxWidth().clickable { steps = steps.filter { it.isNotBlank() } + habitStep(h.id); picking = false }.padding(vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (catalog.isNotEmpty()) {
                    Text("Stretch catalog", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Level.entries.forEach { lv -> FilterChip(level == lv, { level = lv }, { Text(lv.label) }) }
                    }
                    catalog.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                // Starts it with no reminder of its own: the routine's time is when you do it.
                                val id = Habits.start("", planFor(e, level), null, "${e.name} · ${level.label}")
                                steps = steps.filter { it.isNotBlank() } + habitStep(id)
                                picking = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(56.dp)) { MovementDemo(e.demos.first(), 56.dp) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(e.name, style = MaterialTheme.typography.bodyLarge)
                                Text(e.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ picking = false }) { Text("Cancel") } },
    )
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
                        val parsed = stepOf(s)
                        val hs = parsed as? HabitStep
                        val asStep = parsed as? ActivityStep
                        if (asStep != null) {
                            // A timer: started where the step is, with its own clock; not editable as text.
                            val a = stepActivity(asStep.activityId)
                            Text(
                                "⏱ " + (a?.let { "${it.name} · ${it.quickMin ?: DEFAULT_ROUTINE_MIN} min" } ?: "Removed timer"),
                                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyLarge,
                            )
                        } else if (hs != null) {
                            // A habit: done with its figure and count when the routine runs; not editable as text.
                            val h = habits.firstOrNull { it.id == hs.habitId }
                            Text("🏋 " + (h?.let { habitTitle(it, items) } ?: "Removed habit"), Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        } else OutlinedTextField(
                            s, { v -> steps = steps.mapIndexed { j, x -> if (j == i) v.clip(60) else x } },
                            Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                        )
                        // Up only meant sending the first step to the bottom of six took five taps the
                        // wrong way round. The arrow you need is the one pointing where you are going.
                        if (i > 0) IconButton({ steps = steps.toMutableList().apply { add(i - 1, removeAt(i)) }.toList() }) { Text("↑") }
                        if (i < steps.lastIndex) IconButton({ steps = steps.toMutableList().apply { add(i + 1, removeAt(i)) }.toList() }) { Text("↓") }
                        IconButton({ steps = steps.filterIndexed { j, _ -> j != i } }) { Text("×", style = MaterialTheme.typography.titleLarge) }
                    }
                }
                // Short labels: three "+ Add a …" buttons wrapped onto three lines in the dialog.
                Row {
                    TextButton({ steps = steps + "" }) { Text("+ Step") }
                    TextButton({ picking = true }) { Text("+ Stretch") }
                    TextButton({ pickingTimer = true }) { Text("+ Timer") }
                }
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

/**
 * A routine's record: the last 5 weeks, Mon–Sun, a ✓ and the time on each day you finished it, today outlined;
 * with the streak and how many of the last 30 days.
 */
@Composable
fun RoutineCalendarScreen(id: String, onBack: () -> Unit) {
    val all by Routines.all.collectAsState()
    val r = all.firstOrNull { it.id == id && !it.deleted } ?: return onBack()
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenBar("${r.emoji} ${r.name}", onBack = onBack)
        // How often, not how unbroken: a run of days is a number one missed Tuesday destroys, and a
        // number like that earns avoidance rather than effort. The count stays; the streak goes.
        Text(
            "${doneInLast(r, today, 30)} of the last 30 days",
            color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        val firstMonday = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(4)
        (0 until 5).forEach { w ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d ->
                    val date = firstMonday.plusDays(w * 7L + d)
                    val at = r.done[date]
                    val shape = RoundedCornerShape(8.dp)
                    var m = Modifier.weight(1f).height(48.dp).clip(shape).background(if (at != null) cs.primaryContainer else cs.surfaceContainer)
                    if (date == today) m = m.border(2.dp, cs.onSurface, shape)
                    Box(m, contentAlignment = Alignment.Center) {
                        val fg = when { at != null -> cs.onPrimaryContainer; date.isAfter(today) -> cs.onSurface.copy(alpha = 0.3f); else -> cs.onSurfaceVariant }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = fg)
                            if (at != null) Text(fmtMinute(at), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = fg)
                        }
                    }
                }
            }
        }
        Text("A day fills in when you finish the routine.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}


/**
 * "3 of the last 4 days" with a cell per day, oldest first — or nothing at all, which is the point: under
 * three finishes there is no honest number to print, only a near-zero that reads as a telling-off.
 */
@Composable
private fun DensityDots(d: Density, done: Set<LocalDate>, long: Boolean = false) {
    if (!d.worthShowing) return
    val today = LocalDate.now()
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        (d.of - 1 downTo 0).forEach { i ->
            Box(
                Modifier.size(6.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (today.minusDays(i.toLong()) in done) cs.primary else cs.surfaceContainerHighest),
            )
        }
        Text(
            if (long) "${d.done} of the last ${d.of} days" else "${d.done} of ${d.of}",
            Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
        )
    }
}

/** One line naming a step, for the "NEXT" preview. */
private fun stepLabel(st: RoutineStep?, habits: List<Habit>, items: List<LibraryItem>, today: java.time.LocalDate): String? = when (st) {
    null -> null
    is TextStep -> st.text
    is ActivityStep -> stepActivity(st.activityId)?.name
    is HabitStep -> habits.firstOrNull { it.id == st.habitId && !it.ended }?.let { h ->
        habitTitle(h, items) + (h.stageOn(h.dayOf(today))?.let { " · ${label(it)}" } ?: "")
    }
}
