package com.shivam1410.intervaltimer

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // Work follows the wallpaper (Material You); breaks use a fixed dark-pink palette (tertiary roles)
    // so they read as "rest" regardless of wallpaper. Values are the M3 pink tonal palette.
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        dynamicDarkColorScheme(ctx).copy(
            tertiary = Color(0xFFFFB0CB), onTertiary = Color(0xFF5E1133),
            tertiaryContainer = Color(0xFF7D2A4A), onTertiaryContainer = Color(0xFFFFD9E3),
        )
    } else {
        dynamicLightColorScheme(ctx).copy(
            tertiary = Color(0xFFA0305C), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFD9E3), onTertiaryContainer = Color(0xFF3E001C),
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

private fun clock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

/** Same clock, for a time of day that has no date: minutes after midnight. */
private fun clockMin(minuteOfDay: Int): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60); set(Calendar.MINUTE, minuteOfDay % 60)
    }.time,
)

@Composable
fun Root(now: Long) {
    val session by Timer.session.collectAsState()
    val settings by Timer.settings.collectAsState()
    val update by Updater.ready.collectAsState()
    val updateError by Updater.error.collectAsState()
    val plan = remember(settings, session.quick) { session.quick?.let(::quickPlan) ?: plan(settings) }
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableStateOf("") } // "", "history", "settings", "habits", "stretches", "habit:<id>", "routine:<id>"
    var countdown by rememberSaveable { mutableStateOf<String?>(null) } // quick timer about to start
    // Minimised: whatever is running keeps running, and you get Home back (like a video in its corner).
    var minimised by rememberSaveable { mutableStateOf(false) }
    val running = session.running(plan)
    BackHandler(page.isNotEmpty()) { page = "" }
    BackHandler(countdown != null) { countdown = null }
    // Back on the running screen puts it away instead of closing the app; back on Home still leaves.
    BackHandler(running && !minimised && page.isEmpty() && countdown == null) { minimised = true }
    LaunchedEffect(running) { if (!running) minimised = false }
    val startQuick = rememberStartGuard { id -> minimised = false; page = ""; countdown = id }
    // A nudge's Sketch / Relax button: same 5-4-3-2-1 as tapping the quick timer on home.
    val nudged by Nudges.requestedQuick.collectAsState()
    LaunchedEffect(nudged) {
        nudged?.let { page = ""; countdown = it; Nudges.requestedQuick.value = null }
    }
    // A routine reminder: Open takes you to the routine, Start also runs its timer there and then.
    val routine by Routines.requested.collectAsState()
    var autoStart by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(routine) {
        routine?.let { autoStart = Routines.startRequested; page = "routine:$it"; Routines.requested.value = null }
    }

    // Power nap goes fully black, status bar included, with light status icons.
    val napping = session.running(plan) && !session.paused && plan[session.index].kind == Kind.ACTIVITY && (session.quick ?: settings.activity) == "nap"
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !napping && !dark
        }
    }

    Column(Modifier.fillMaxSize().background(if (napping) Color.Black else Color.Transparent).systemBarsPadding()) {
        // Settings › Profile has its own Install button and progress under App updates; don't repeat them on top.
        if (page != "settings") {
            update?.let { tag ->
                Banner("Update $tag downloaded", "Install") { Updater.install(ctx) }
            }
            // The launch check downloads on its own too: show it happening.
            Updater.progress.collectAsState().value?.let { (tag, p) -> UpdateProgress(tag, p, Modifier.padding(16.dp, 8.dp)) }
        }
        updateError?.let { Banner(it, "OK") { Updater.error.value = null } }
        val share by Share.pending.collectAsState()
        share?.let { LibraryDialog(null, Library.visible.map { it.tag }.filter { it.isNotBlank() }.distinct(), it.url, it.title, it.file, it.id, it.minutes) { Share.pending.value = null } }
        val shareStatus by Share.status.collectAsState()
        if (shareStatus.isNotEmpty()) Banner(shareStatus, "OK") { Share.status.value = "" }
        when {
            page == "history" -> HistoryScreen { page = "" }
            page == "settings" -> SettingsScreen(settings) { page = "" }
            page == "habits" -> SettingsScreen(settings, start = "habits") { page = "" }
            page == "stretches" -> SettingsScreen(settings, start = "stretches") { page = "" }
            page == "library" -> LibraryScreen(startQuick) { page = "" }
            page == "shape" -> ShapeScreen(settings, now) { page = "" }
            page == "today" -> DayScreen(History.today()) { page = "" }
            page.startsWith("habit:") -> HabitCalendarScreen(page.removePrefix("habit:")) { page = "" }
            page.startsWith("routine:") -> RoutineScreen(page.removePrefix("routine:"), autoStart) { page = ""; autoStart = false }
            countdown != null -> countdown?.let { id -> Countdown(id) { countdown = null } }
            session.idle || minimised -> Setup(
                settings, now, onHistory = { page = "history" }, onSettings = { page = "settings" }, onHabit = { page = "habit:$it" },
                onAddHabit = { page = "stretches" }, onRoutine = { page = "routine:$it" }, onQuick = startQuick,
                onLibrary = { page = "library" }, onShape = { page = "shape" }, onToday = { page = "today" },
                mini = session.takeIf { minimised }, onRestore = { minimised = false },
            )
            // The day's own record, not the all-time list: right after finishing, today is the question.
            session.done(plan) -> Done(session, settings) { page = "today" }
            else -> Running(plan, session, settings, now) { page = "routine:$it" }
        }
    }
}

/** "Downloading update v1.15.0… 42%" with a bar; shared by home and Settings › Profile. */
@Composable
fun UpdateProgress(tag: String, p: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Before the first bytes (connecting, following GitHub's redirect) a moving bar, then the real percentage.
        val started = p > 0f
        Text(if (started) "Downloading update $tag… ${(p * 100).toInt()}%" else "Downloading update $tag…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (started) LinearProgressIndicator({ p }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
        // Solid tertiary button: the default primary-coloured text button was unreadable on the dark banner.
        Button(
            onClick, Modifier.padding(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
        ) { Text(action) }
    }
}

// ---------- Setup ----------

@Composable
private fun Setup(
    s: Settings, now: Long, onHistory: () -> Unit, onSettings: () -> Unit, onHabit: (String) -> Unit, onAddHabit: () -> Unit,
    onRoutine: (String) -> Unit, onQuick: (String) -> Unit, onLibrary: () -> Unit, onShape: () -> Unit, onToday: () -> Unit,
    mini: Session? = null, onRestore: () -> Unit = {},
) {
    val total = plan(s).sumOf { it.ms }
    // How much of your day is still ahead. Fewer than the full count means you started late.
    val fits = cyclesLeftToday(s, minuteOfDay(now))
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val first = Drive.profile.collectAsState().value?.displayName?.substringBefore(' ').orEmpty()
    val greeting = when (hour) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" } +
        if (first.isNotEmpty()) ", $first" else ""
    fun set(f: Settings.() -> Settings) = Timer.saveSettings(s.f())
    val scheduled by Timer.scheduledAt.collectAsState()
    // "Start at" only picks a time; the big button arms it. Dropped once that hour passes or Custom is left.
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(greeting, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistoryPill(onHistory)
                Spacer(Modifier.width(8.dp))
                ProfileChip(onSettings)
            }
            // The hours are the day's shape; changing them belongs on its own screen, not interleaved
            // with what today is actually doing.
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Shape of the day", onClick = onShape).padding(vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your hours", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("Change ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "${clockMin(s.workStart)} — ${clockMin(s.workEnd)}",
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${s.cycles} ${if (s.cycles == 1) "cycle" else "cycles"} · ${s.workMin} min work · ${s.breakMin} min break" +
                        if (s.spare > 0) " · ${s.spare} min free" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DayStrip(
                dayBlocks(Timer.plan, mini ?: Session(), now), Modifier.fillMaxWidth().padding(top = 14.dp),
                mark = if (mini == null) dayGone(minuteOfDay(now), s.workStart, (total / 60_000L).toInt()) else null,
                // Idle, the strip is the hours already printed above it. Running, it is the day you started.
                from = mini?.startedAt,
                to = mini?.let { m -> now + m.left(now).coerceAtLeast(0) + Timer.plan.drop(m.index + 1).sumOf { it.ms } },
            )
            // What today already holds. Absent entirely on a morning that hasn't started — the day ahead
            // is the point of this screen, and an empty "Earlier" row would just be furniture.
            val today = History.today()
            val todayRec = History.days.collectAsState().value.firstOrNull { it.date == today }
            val liveHabits by Habits.all.collectAsState()
            // The raw fact that it finished, not routineDoneToday — that asks whether it is still on your
            // list (a stretch falling due again reopens it), which is a different question from what happened.
            val doneRoutines = Routines.all.collectAsState().value
                .filterNot { it.deleted }.filter { doneToday(it, today) != null }.map { it.name }
            val doneHabits = liveHabits.count { !it.ended && today in it.done }
            earlierToday(todayRec?.workMs ?: 0L, todayRec?.cycles ?: 0, doneRoutines, doneHabits)?.let {
                // The summary is the collapsed form; the day's own record is what it opens into. That screen
                // lists routines and habits too, so whatever is summarised here is there to be looked at.
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(onClickLabel = "How today went", onClick = onToday).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Earlier · $it",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            mini?.let { UpNext(Timer.plan, it, s, now) }
            // Starting late costs you cycles, never a scolding: say what fits and let the full day stand beside it.
            if (mini == null && fits in 1 until s.cycles && scheduled == null) Text(
                "Start now and ${fits} ${if (fits == 1) "cycle fits" else "cycles fit"} before ${clockMin(s.workEnd)}. Running all ${s.cycles} would end at ${clock(now + total)}. Both are fine.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            HabitsSection(onHabit, onAddHabit)
            RoutinesRow(onRoutine)
            // Quick timers, your files and ambient sound used to run on down this same scroll, under a Start
            // button that had nothing to do with them. They are one tap away instead.
            LibraryRow(onLibrary)
            Spacer(Modifier.height(16.dp))
        }
        // Set from the Custom schedule; shown (with Cancel) whenever a start is armed, preset picked after or not.
        scheduled?.let { ScheduledLine(it) }
        // Something already running: its bar takes the place of Start now, because only one timer runs at a time.
        if (mini != null) MiniTimer(mini, now, onRestore)
        // Started late: two equal offers, never a deficit. Fit what's left, or run the whole day anyway.
        else if (fits in 1 until s.cycles && scheduled == null) Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                { Timer.start(fits) },
                Modifier.weight(1f).height(64.dp),
                shape = RoundedCornerShape(32.dp),
            ) { Text("Fit to ${clockMin(s.workEnd)}", fontSize = 16.sp) }
            OutlinedButton(
                { Timer.start() },
                Modifier.weight(1f).height(64.dp),
                shape = RoundedCornerShape(32.dp),
            ) { Text("Run all ${s.cycles}", fontSize = 16.sp) }
        }
        else Button(
            onClick = { Timer.start() },
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(64.dp),
            shape = RoundedCornerShape(32.dp),
        ) { Text("Start now", fontSize = 20.sp) }
    }
}

/**
 * What is coming, with the clock times it will actually happen at. "Break at 2:11" is something you can
 * plan around; "in 32 minutes" is arithmetic you have to do while you are trying to work.
 */
@Composable
private fun UpNext(plan: List<Phase>, s: Session, settings: Settings, now: Long) {
    val next = upNext(plan, s, now)
    if (next.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val breakName = activity(settings.activity).name
    val endsAt = now + s.left(now) + plan.drop(s.index + 1).sumOf { it.ms }
    val after = cyclesAfter(plan, next)
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("NEXT", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, letterSpacing = 1.sp)
        next.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    clock(u.at), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    modifier = Modifier.width(72.dp),
                )
                Box(
                    Modifier.width(4.dp).height(if (u.work) 20.dp else 10.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (u.work) cs.primary else cs.tertiary),
                )
                Text(
                    if (u.work) "Cycle ${u.cycle} · work" else "Break · $breakName",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                )
                Text(fmtDur(u.ms), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        if (after > 0) Text(
            "then $after more · ends ${clock(endsAt)}",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
    }
}

/**
 * The day drawn to scale: a tall bar per work block, a short one per break, filled as you get through it.
 * The point is to read the shape of what's left from across the room, without doing arithmetic.
 */
@Composable
private fun DayStrip(blocks: List<DayBlock>, modifier: Modifier = Modifier, mark: Float? = null, from: Long? = null, to: Long? = null) {
    if (blocks.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val total = blocks.sumOf { it.ms }.toFloat().coerceAtLeast(1f)
    val elapsed = blocks.sumOf { (it.done * it.ms).toDouble() }.toFloat() / total
    // The gaps are fixed while the blocks share what is left, so a short rhythm (a 1:1 day is 480 blocks)
    // used to spend the whole width on spacing and draw nothing at all. Past the point where a gap costs
    // more than a block, they close up and the day reads as one proportional band.
    val gap = if (blocks.size > 48) 0.dp else 2.dp
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(34.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
                blocks.forEach { b ->
                    Box(
                        Modifier.weight(b.ms.toFloat().coerceAtLeast(1f))
                            .fillMaxHeight(if (b.work) 1f else 0.42f)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(cs.surfaceVariant),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (b.done > 0f) Box(
                            Modifier.fillMaxWidth().fillMaxHeight(b.done.coerceIn(0f, 1f))
                                .background(if (b.work) cs.primary else cs.tertiary),
                        )
                    }
                }
            }
            // Where you are in the day, drawn where it actually falls rather than described in words. Running,
            // that is how much you have done; idle, [mark] puts the clock there instead, so a morning you have
            // not started still shows what is already behind you.
            val at = if (elapsed > 0.001f && elapsed < 0.999f) elapsed else mark
            if (at != null) Row(Modifier.matchParentSize()) {
                Spacer(Modifier.weight(at))
                Box(Modifier.width(2.dp).fillMaxHeight().background(cs.error))
                Spacer(Modifier.weight(1f - at))
            }
        }
        // A day under way runs from when you started it, which is not the hours printed above the strip: two
        // different days on one screen unless the strip says which one it is.
        if (from != null && to != null) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(clock(from), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text(clock(to), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

/** One end of your working hours, as minutes after midnight. The cycle count follows from the pair. */
@Composable
private fun TimeRow(label: String, minuteOfDay: Int, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        FilledTonalButton({
            android.app.TimePickerDialog(
                ctx, { _, h, m -> onPick(h * 60 + m) },
                minuteOfDay / 60, minuteOfDay % 60, android.text.format.DateFormat.is24HourFormat(ctx),
            ).show()
        }) { Text(fmtMinute(minuteOfDay), fontWeight = FontWeight.SemiBold) }
    }
}

/** 5th row of the Custom schedule: start now, or once at one of the next whole hours (armed by the Start button). */
@Composable
private fun StartAt(pick: Long?, now: Long, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // Re-evaluated each minute, not every second. Keying on now / hour broke in half-hour zones (IST):
    // epoch hours tick at :30 there, so at 16:27 the list still offered 16:00.
    val hours = remember(now / MIN) { nextFullHours(now, 4) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Start at", Modifier.weight(1f))
        Box {
            TextButton({ open = true }) { Text("${pick?.let(::clock) ?: "Now"} ▾", fontWeight = FontWeight.SemiBold) }
            DropdownMenu(open, { open = false }) {
                DropdownMenuItem({ Text("Now") }, { onPick(null); open = false })
                hours.forEach { at -> DropdownMenuItem({ Text(clock(at)) }, { onPick(at); open = false }) }
            }
        }
    }
}

@Composable
private fun ScheduledLine(at: Long) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⏰ Starts at ${clock(at)}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        TextButton({ Timer.scheduleStart(null) }) { Text("Cancel") }
    }
}

/** Header shortcut to History: how many days this month the app was used, next to the profile photo. */
/** What is running, where Start now would be: tap to go back to its screen. */
@Composable
private fun MiniTimer(s: Session, now: Long, onRestore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val a = s.quick?.let(::activity)
    val left = s.left(now)
    Row(
        Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(32.dp)).background(cs.primaryContainer)
            .clickable(onClickLabel = "Back to the timer", onClick = onRestore).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(a?.emoji ?: "⏱", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(a?.name ?: "Workday", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
            Text(
                if (s.paused) "Paused" else if (left == 0L) "Time's up" else "${fmt(left)} left",
                style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer,
            )
        }
        Text("Open", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
    }
}

/**
 * Only one timer runs at a time. Returns the way to start [start]: straight away when nothing is going,
 * otherwise after asking whether to end what is running. Renders the question itself.
 */
@Composable
fun rememberStartGuard(start: (String) -> Unit): (String) -> Unit {
    val session by Timer.session.collectAsState()
    val settings by Timer.settings.collectAsState()
    val plan = session.quick?.let(::quickPlan) ?: plan(settings)
    var pending by remember { mutableStateOf<String?>(null) }
    pending?.let { id ->
        val from = session.quick?.let { activity(it).name } ?: "your workday"
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("One timer at a time") },
            text = { Text("End $from and start ${activity(id).name}?") },
            confirmButton = { TextButton({ pending = null; Timer.stop(); start(id) }) { Text("End and start") } },
            dismissButton = { TextButton({ pending = null }) { Text("Keep going") } },
        )
    }
    return { id -> if (session.running(plan)) pending = id else start(id) }
}

@Composable
private fun HistoryPill(onClick: () -> Unit) {
    val days by History.days.collectAsState()
    val dense = density(days, History.today())
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(cs.secondaryContainer).clickable(onClickLabel = "History", onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Too early to mean anything? Say nothing rather than "0 of 14" — but keep the way in to History.
        Text(
            if (dense.worthShowing) "${dense.done} of ${dense.of}" else "History",
            style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer,
        )
    }
}

@Composable
private fun RowScope.PresetChip(name: String, ratio: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, modifier = Modifier.weight(1f),
        label = {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(ratio, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        },
    )
}

/** Home's one way in to everything you can start on its own. */
@Composable
private fun LibraryRow(onLibrary: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lib = Library.items.collectAsState().value.count { !it.deleted }
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer)
            .clickable(onClickLabel = "Library") { onLibrary() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Library", style = MaterialTheme.typography.titleMedium)
            Text(
                "${QUICK.size} quick timers" + (if (lib > 0) " · $lib of your own" else "") + " · ambient sound",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = cs.onSurfaceVariant)
    }
}

/**
 * The day's shape: the hours you keep and the rhythm you work in. Deciding this is a different act from
 * running the day, so it gets its own screen instead of sitting between the strip and your habits.
 */
@Composable
private fun ShapeScreen(s: Settings, now: Long, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scheduled by Timer.scheduledAt.collectAsState()
    fun set(f: Settings.() -> Settings) = Timer.saveSettings(s.f())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text("Shape of the day", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }

        Section("Your hours") {
            TimeRow("Day starts", s.workStart) { v -> set { copy(workStart = v) } }
            TimeRow("Day ends", s.workEnd) { v -> set { copy(workEnd = v) } }
            // The cycle count is worked out from the hours, never typed in.
            Text(
                "${s.cycles} ${if (s.cycles == 1) "cycle" else "cycles"} fit" +
                    if (s.spare > 0) " · ${s.spare} min stays free at the end" else "",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Text("Rhythm", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRESETS.forEach { p ->
                PresetChip(p.name.substringBefore(' '), "${p.work}:${p.brk}", !s.custom && s.workMin == p.work && s.breakMin == p.brk) {
                    set { copy(workMin = p.work, breakMin = p.brk, custom = false) }
                }
            }
            PresetChip("Custom", if (s.custom) "${s.workMin}:${s.breakMin}" else "···", s.custom) { set { copy(custom = true) } }
        }
        Section("") {
            Stepper("Work", s.workMin, "min", 1..180) { v -> set { copy(workMin = v, custom = true) } }
            Stepper("Break", s.breakMin, "min", 1..60) { v -> set { copy(breakMin = v, custom = true) } }
            Stepper("Prepare (in break)", s.prepMin, "min", 0..10) { v -> set { copy(prepMin = v) } }
        }

        // Hours in, cycles out — and this is what the cycles actually buy. The count alone never said
        // whether eight cycles meant a long day or a short one.
        Section("That makes") {
            Line("Focus", fmtDur(s.cycles * s.workMin * MIN))
            Line("Breaks", fmtDur(s.cycles * s.breakMin * MIN))
            Line("Spare at the end", if (s.spare > 0) fmtDur(s.spare * MIN) else "none")
        }

        Section("Start it later") {
            // Picking an hour arms it there and then, rather than leaving it for a button on another screen.
            StartAt(scheduled, now) { at ->
                Timer.scheduleStart(at)
                Toast.makeText(ctx, at?.let { "Scheduled for ${clock(it)}" } ?: "Scheduled start cleared", Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Everything startable on its own, off Home: one-off timers, your own files, and sound to work to. */
@Composable
private fun LibraryScreen(onQuick: (String) -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ambient by Timer.ambient.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = cs.onSurface) }
            Text("Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        Text("Tap any of these to start it now.", color = cs.onSurfaceVariant)
        QuickTimers(onQuick)
        MyLibrary(onQuick)
        PlansShelf()
        Text(
            "Sound while you work", style = MaterialTheme.typography.titleSmall, color = cs.primary,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            "Not something you start — it plays under whatever else is going.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
        )
        // Tap to play, tap again to stop; starting a timer stops it.
        SoundTiles(ambient.orEmpty(), off = false, cs.surfaceContainer, "Ambient") { Timer.playAmbient(it.takeIf { it != ambient }) }
        Spacer(Modifier.height(24.dp))
    }
}

/** One-off activity timers: tap to start a single NSDR / stretch / walk / … session now. */
@Composable
private fun QuickTimers(onQuick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text("Quick timers", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    TileGrid(QUICK.map(::activity)) { a ->
        ActivityTile(a, "${a.quickMin} min", cs.surfaceContainer, cs.onSurface, cs.onSurfaceVariant, "Start ${a.name}") { onQuick(a.id) }
    }
}

/** 4-column grid of equal-height tiles; every item visible, no horizontal scroll. */
@Composable
internal fun <T> TileGrid(items: List<T>, tile: @Composable RowScope.(T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile(it) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Emoji, name and a small caption in a rounded tile — shared by quick timers and the break picker. */
@Composable
private fun RowScope.ActivityTile(a: Activity, caption: String, bg: Color, fg: Color, sub: Color, label: String, onClick: () -> Unit) =
    Tile(a.emoji, a.name, caption, bg, fg, sub, label, onClick = onClick)

@Composable
internal fun RowScope.Tile(
    emoji: String, name: String, caption: String, bg: Color, fg: Color, sub: Color, label: String,
    compact: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(if (compact) 16.dp else 20.dp)).background(bg)
            .combinedClickable(onClickLabel = label, onLongClickLabel = onLongClick?.let { "Edit" }, onLongClick = onLongClick, onClick = onClick).padding(vertical = if (compact) 10.dp else 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = if (compact) 22.sp else 28.sp)
        Text(name, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (caption.isNotEmpty()) Text(caption, style = MaterialTheme.typography.labelSmall, color = sub)
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Type a value instead of tapping −/+ many times; rejects anything outside [range]. */
@Composable
private fun NumberDialog(label: String, value: Int, unit: String, range: IntRange, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    // Opens focused with the whole value selected, so typing replaces it.
    var field by remember { mutableStateOf(TextFieldValue(value.toString(), TextRange(0, value.toString().length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val text = field.text
    val n = text.toIntOrNull()
    val ok = n != null && n in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            OutlinedTextField(
                field, { field = it.copy(text = it.text.filter(Char::isDigit).take(3)) },
                modifier = Modifier.focusRequester(focus),
                singleLine = true,
                suffix = { if (unit.isNotEmpty()) Text(unit) },
                supportingText = { Text("${range.first}–${range.last}") },
                isError = !ok,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ok) n?.let(onSet) }),
            )
        },
        confirmButton = { TextButton({ n?.let(onSet) }, enabled = ok) { Text("Set") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun Stepper(label: String, value: Int, unit: String, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton({ onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−", fontSize = 22.sp) }
        // Tap the number to type it; −/+ stay for small nudges.
        var editing by remember { mutableStateOf(false) }
        Text(
            "$value $unit".trim(),
            Modifier.width(72.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Edit $label") { editing = true }.padding(vertical = 8.dp),
            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
        )
        if (editing) NumberDialog(label, value, unit, range, onDismiss = { editing = false }) { onChange(it); editing = false }
        IconButton({ onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+", fontSize = 22.sp) }
    }
}

@Composable
fun Toggle(icon: Int, label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f).padding(start = 14.dp))
        Switch(value, onChange)
    }
}

/**
 * Prepare offers two: your usual, and one you haven't touched in a while. Everything else is behind
 * "Something else…", because a wall of twenty tiles is the last thing a tired break needs.
 */
@Composable
private fun TwoChoices(usual: Activity, onMore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = History.today()
    val last = remember(History.timelines.value) { lastUsed(History.timelines.value) }
    val fresh = remember(last, usual.id) { staleChoice(QUICK, last, today, exclude = usual.id)?.let(::activity) }
    val breakMin = Timer.settings.value.breakMin
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pick one", style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceCard("Your usual", usual, "${usual.minutes ?: breakMin} min", Modifier.weight(1f)) { Timer.chooseActivity(usual.id) }
            fresh?.let { f ->
                val days = daysSinceUsed(last, f.id, today)
                ChoiceCard(
                    days?.let { "Not for $it days" } ?: "Never tried", f, "${f.minutes ?: breakMin} min", Modifier.weight(1f),
                ) { Timer.chooseActivity(f.id) }
            }
        }
        TextButton(onMore, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Something else…", color = cs.onTertiaryContainer)
        }
    }
}

@Composable
private fun ChoiceCard(flag: String, a: Activity, length: String, modifier: Modifier, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(cs.surface)
            .clickable(onClickLabel = "Choose ${a.name}", onClick = onPick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(a.emoji, fontSize = 22.sp)
        Text(flag, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Text(length, style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Break picker: same tiles as the quick timers; solid surface tiles on the tinted card, chosen one filled. */
@Composable
private fun ActivityPicker(selected: String, breakMin: Int, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    ACTIVITIES.groupBy { it.group }.forEach { (group, items) ->
        Text(group, style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer, modifier = Modifier.padding(top = 4.dp))
        TileGrid(items) { a ->
            val on = selected == a.id
            ActivityTile(
                a, "${a.minutes ?: breakMin} min", // fixed 20-min activities, else the regular break length
                if (on) cs.tertiary else cs.surface, if (on) cs.onTertiary else cs.onSurface,
                if (on) cs.onTertiary else cs.onSurfaceVariant, "Choose ${a.name}",
            ) { onPick(a.id) }
        }
    }
    // Your library, folded away by default (open when one of its items is the current pick).
    val lib = Library.items.collectAsState().value.filterNot { it.deleted }.map { it.toActivity() }
    if (lib.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(selected.startsWith("lib:")) }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = if (open) "Hide my library" else "Show my library") { open = !open }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("My library · ${lib.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer)
        Text(if (open) "▴" else "▾", color = cs.onTertiaryContainer)
    }
    if (open) TileGrid(lib) { a ->
        val on = selected == a.id
        ActivityTile(
            a, "$breakMin min", if (on) cs.tertiary else cs.surface, if (on) cs.onTertiary else cs.onSurface,
            if (on) cs.onTertiary else cs.onSurfaceVariant, "Choose ${a.name}",
        ) { onPick(a.id) }
    }
}

// ---------- Running ----------

@Composable
private fun Running(plan: List<Phase>, s: Session, settings: Settings, now: Long, onRoutine: (String) -> Unit = {}) {
    val p = plan[s.index]
    val left = s.left(now)
    val quick = s.quick != null
    // A routine's own timer: its steps are a page of their own, so offer the way back into them.
    val routine = s.quick?.takeIf { it.startsWith("routine:") }?.removePrefix("routine:")
    val chosen = activity(s.quick ?: settings.activity)
    val over = overtimeMs(plan, s, now) // past 0 on work / a break activity: counting up
    if (p.kind == Kind.ACTIVITY && chosen.id == "nap" && !s.paused && over == null) return NapScreen(s)
    val isWork = p.kind == Kind.WORK
    val accent = if (isWork) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    var confirmEnd by remember { mutableStateOf(false) }
    val (workDone, breakDone) = totals(plan, s, now)
    val phaseMs = s.phaseMs.takeIf { it > 0 } ?: p.ms
    val brk = if (s.bonus > 0) "LONG BREAK · +${extraMin(s)} MIN" else "BREAK"
    val label = when {
        quick -> chosen.name.uppercase() + if (s.bonus > 0) " · +${extraMin(s)} MIN" else ""
        p.kind == Kind.WORK -> "WORK"
        p.kind == Kind.PREP -> "$brk · PREPARE"
        else -> brk
    } + if (over != null) " · OVERTIME" else ""

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = accent, letterSpacing = 3.sp, modifier = Modifier.padding(top = 16.dp))
        routine?.let { id -> TextButton({ onRoutine(id) }) { Text("\u2039  Back to the steps") } }
        Box(Modifier.padding(vertical = 24.dp).size(300.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(14.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = stroke)
                // Countdown: the ring empties. Overtime: it fills once a minute and starts over.
                val sweep = if (over != null) 360f * (over % MIN) / MIN else 360f * left / phaseMs
                drawArc(accent, -90f, sweep, false, style = stroke)
            }
            if (p.kind == Kind.ACTIVITY && chosen.id == "breathing" && !s.paused) Breathing(accent)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (over != null) "+" + fmt(over) else fmt(left), fontSize = 64.sp, fontWeight = FontWeight.Light)
                Text(
                    when {
                        over != null -> "Time's up · counting up"
                        routine != null -> "Routine"
                        quick -> "Quick timer"
                        // The plan that is actually running, not the full day: a late start plans fewer.
                        // On a break the cycle alone answers nothing useful — when you are back at it does,
                        // and without it the only way to know is to add the prepare and the activity up.
                        else -> "Cycle ${p.cycle} / ${cyclesIn(Timer.plan)}" +
                            (untilWork(plan, s.index, left).takeIf { !isWork }?.let { " · back at ${clock(now + it)}" } ?: "")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The day as it is, not eight identical dots: work and break in proportion, filled as far as you
        // have got, with the clock you started at and the one you are heading for. The same strip Home
        // draws before you begin, so a day running looks like the day you planned.
        if (!quick) DayStrip(
            dayBlocks(plan, s, now), Modifier.fillMaxWidth(),
            from = s.startedAt, to = now + left.coerceAtLeast(0) + plan.drop(s.index + 1).sumOf { it.ms },
        )

        // Overtime: no pause; add 10 more minutes (countdown again) or move on. Right under the ring, where the eye already is.
        if (over != null) Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ Timer.extendBreak() }, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text("+10 min", fontSize = 18.sp)
            }
            FilledTonalButton(
                { Timer.skip() }, Modifier.weight(1f).height(56.dp),
                colors = if (isWork) ButtonDefaults.filledTonalButtonColors()
                else ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) { Text("Next", fontSize = 18.sp) }
        }

        // Stopping a timer should never need a scroll: these used to sit under the cue, the sound grid
        // and the day's totals, which on a phone put them a screen and a half below the clock.
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (over == null) Button(
                { if (s.paused) Timer.resume() else Timer.pause() },
                Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) { Text(if (s.paused) (if (s.pausedLeft == phaseMs) "Start" else "Resume") else "Pause", fontSize = 18.sp) }
            if (!quick && over == null) FilledTonalButton(
                { Timer.skip() }, Modifier.weight(1f).height(56.dp),
                colors = if (isWork) ButtonDefaults.filledTonalButtonColors()
                else ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) { Text("Skip", fontSize = 18.sp) }
            // End sits in the same row; it asks for confirmation.
            OutlinedButton(
                { confirmEnd = true }, Modifier.weight(if (quick) 1f else 0.8f).height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            ) { Text("End", fontSize = 18.sp) }
        }

        val cues by Cues.lists.collectAsState()
        CueCard(if (isWork) "Start of the day" else "This break", phaseCue(p, quick, cues, epochDay(s.startedAt)), key = s.index)
        if (isWork) FocusMusic(settings.focusSound) else BreakChoices(p, s, chosen, s.phaseMs - left)

        if (!quick) Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stat("Today", "${fmtDur(workDone)} work", "${fmtDur(breakDone)} breaks", Modifier.weight(1f).fillMaxHeight())
            Stat("Next", Timer.subtitle(s, now).removePrefix("Next: ").takeIf { isWork && over == null } ?: nextLabel(plan, s, at = over == null), "", Modifier.weight(1f).fillMaxHeight())
        }

    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(if (quick) "End ${chosen.name}?" else "End workday?") },
            text = { Text("End and Save keeps what you've done so far in History. Discard throws this session away.") },
            // One row: Discard (red) left, Keep going middle, End and Save as the main, filled button right.
            confirmButton = {
                val tight = PaddingValues(horizontal = 10.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        { confirmEnd = false; Timer.discard() }, contentPadding = tight,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Discard", maxLines = 1) }
                    TextButton({ confirmEnd = false }, contentPadding = tight) { Text("Keep going", maxLines = 1) }
                    Button({ confirmEnd = false; Timer.stop() }, shape = RoundedCornerShape(12.dp), contentPadding = tight) {
                        Text("End and Save", maxLines = 1)
                    }
                }
            },
        )
    }
}

/** [at]: append the start time; off in overtime, where the next phase starts whenever you tap Next. */
private fun nextLabel(plan: List<Phase>, s: Session, at: Boolean = true): String {
    val n = plan.getOrNull(s.index + 1) ?: return "Day complete"
    return when (n.kind) {
        Kind.WORK -> "Work · ${fmtDur(n.ms)}"
        Kind.PREP -> "Break · ${fmtDur(plan.drop(s.index + 1).takeWhile { it.kind != Kind.WORK }.sumOf { it.ms })}"
        Kind.ACTIVITY -> activity(Timer.settings.value.activity).let { "${it.emoji} ${it.name} · ${fmtDur(n.ms + s.bonus)}" }
    } + if (at) " at ${clock(s.endsAt)}" else ""
}

/** Chosen during the break itself: what to do, and whether to make it a long one. */
@Composable
private fun BreakChoices(p: Phase, s: Session, chosen: Activity, elapsedMs: Long) {
    val quick = s.quick != null
    // The catalogue used to open itself at the start of every break — twenty choices at the moment you
    // have least appetite for choosing. Prepare now offers two, and the rest is one tap away.
    var changing by remember(s.index) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.kind == Kind.PREP) {
                Text("Prepare", style = MaterialTheme.typography.titleLarge)
                Text("Lie down.\nPut your phone aside.\nClose your eyes.", style = MaterialTheme.typography.bodyLarge)
                Text("Then: ${chosen.emoji} ${chosen.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("${chosen.emoji}  ${chosen.name}", style = MaterialTheme.typography.titleLarge)
                Text(chosen.hint, style = MaterialTheme.typography.bodyLarge)
            }
            // One row of actions: the activity's own button (play / open) + Change + +10 min, equal height.
            val cs = MaterialTheme.colorScheme
            val colors = ButtonDefaults.buttonColors(containerColor = cs.surface, contentColor = cs.onSurface)
            val pad = PaddingValues(horizontal = 10.dp)
            val primary = p.kind == Kind.ACTIVITY && hasPrimaryAction(chosen)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primary) PrimaryAction(chosen, Modifier.weight(1.4f).height(44.dp))
                // In Prepare the two-choice card carries "Something else…", so this would be a second door
                // to the same room; it comes back as "Done" once the full picker is open.
                if (!quick && (p.kind != Kind.PREP || changing)) Button({ changing = !changing }, Modifier.weight(1f).height(44.dp), colors = colors, contentPadding = pad) {
                    // Toggles the tile picker; "Done" closes it so the row never ends up with a lone button.
                    Text(if (changing) "Done" else if (primary) "Change" else "Change activity", maxLines = 1)
                }
                Button({ Timer.extendBreak() }, Modifier.weight(1f).height(44.dp), colors = colors, contentPadding = pad) { Text("+10 min", maxLines = 1) }
            }
            if (p.kind == Kind.ACTIVITY) ActivityCue(chosen, elapsedMs, s.paused)
            if (p.kind == Kind.PREP && !changing && !quick) TwoChoices(chosen) { changing = true }
            if (changing) ActivityPicker(chosen.id, Timer.settings.value.breakMin) { Timer.chooseActivity(it) }
        }
    }
}

@Composable
private fun FocusMusic(selected: String) {
    Card(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🎵 Focus music", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            SoundTiles(selected, off = true, MaterialTheme.colorScheme.surface, "Focus music") { Timer.chooseFocus(it) }
        }
    }
}

/** Looped sounds as compact tiles (focus music, home ambient); emoji comes from resources/manifest.json. */
@Composable
private fun SoundTiles(selected: String, off: Boolean, bg: Color, label: String, onPick: (String) -> Unit) {
    val sounds by Media.sounds.collectAsState()
    val downloading by Media.downloading.collectAsState()
    val loops = sounds.filter { it.loop }
    val cs = MaterialTheme.colorScheme
    TileGrid((if (off) listOf(Media.Sound("", "Off", "", true, "🔇")) else emptyList()) + loops) { snd ->
        val on = selected == snd.id
        Tile(
            snd.emoji, snd.name, "", if (on) cs.primary else bg, if (on) cs.onPrimary else cs.onSurface,
            cs.onSurfaceVariant, "$label ${snd.name}", compact = true,
        ) { onPick(snd.id) }
    }
    if (loops.isEmpty()) {
        Text(
            if (downloading) "Downloading sounds from GitHub…" else "Sounds not downloaded yet",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
        if (!downloading) TextButton({ Media.sync() }) { Text("Retry download") }
    }
}

@Composable
private fun Stat(title: String, line1: String, line2: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(line1, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            if (line2.isNotEmpty()) Text(line2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** Box breathing: inhale 4s, hold 4s, exhale 4s, hold 4s. */
@Composable
private fun Breathing(color: Color) {
    val t = rememberInfiniteTransition(label = "breath")
    val ms by t.animateFloat(0f, 16_000f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart), label = "ms")
    val step = (ms / 4_000).toInt().coerceIn(0, 3)
    val f = ms % 4_000 / 4_000
    val scale = when (step) { 0 -> 0.55f + 0.4f * f; 1 -> 0.95f; 2 -> 0.95f - 0.4f * f; else -> 0.55f }
    Box(Modifier.size(260.dp).scale(scale).clip(CircleShape).background(color.copy(alpha = 0.15f)))
    Text(
        listOf("Inhale", "Hold", "Exhale", "Hold")[step],
        Modifier.padding(top = 150.dp), color = color, style = MaterialTheme.typography.titleMedium,
    )
}

// ---------- Done ----------

@Composable
private fun Done(s: Session, settings: Settings, onDay: () -> Unit) {
    val work = s.workMs
    val rest = s.breakMs
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val q = s.quick?.let(::activity)
        Text(q?.emoji ?: "🎉", fontSize = 64.sp)
        Text(q?.let { "${it.name} complete" } ?: "Workday complete", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
        Card(Modifier.fillMaxWidth().padding(top = 24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (q != null) {
                    Line("Duration", fmtDur(rest))
                } else {
                    Line("Focused", fmtDur(work))
                    Line("Breaks", fmtDur(rest))
                    Line("Cycles", "${s.cycles} / ${settings.cycles}")
                }
                Line("Started", clock(s.startedAt))
                Line("Finished", clock(s.finishedAt))
            }
        }
        if (q == null) CueCard("Before you stop", endCue(Cues.lists.collectAsState().value), key = s.startedAt)
        Button({ Timer.stop() }, Modifier.fillMaxWidth().padding(top = 24.dp).height(64.dp), shape = RoundedCornerShape(32.dp)) { Text("Done") }
        if (s.quick == null) OutlinedButton(onDay, Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp), shape = RoundedCornerShape(32.dp)) { Text("See the whole day") }
    }
}

@Composable
private fun Line(k: String, v: String) {
    Row {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.SemiBold)
    }
}
