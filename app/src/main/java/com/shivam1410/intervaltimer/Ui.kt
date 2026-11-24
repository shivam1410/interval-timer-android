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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // "Cool slate", the palette the C+ boards wear: work indigo, break teal, routine violet. Fixed rather
    // than Material You — the screens were designed against these exact colours, and a wallpaper that
    // turned work orange would undo the one thing the day's strip and blocks rely on to mean anything.
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8AA0D8), onPrimary = Color(0xFF0E1016),
            primaryContainer = Color(0xFF272B36), onPrimaryContainer = Color(0xFF8AA0D8),
            secondary = Color(0xFFB49BE6), onSecondary = Color(0xFF0E1016),
            secondaryContainer = Color(0xFF272B36), onSecondaryContainer = Color(0xFFB49BE6),
            tertiary = Color(0xFF4FB8A4), onTertiary = Color(0xFF0E1016),
            tertiaryContainer = Color(0xFF1E3B37), onTertiaryContainer = Color(0xFF4FB8A4),
            background = Color(0xFF0E1016), onBackground = Color(0xFFEEF0F6),
            surface = Color(0xFF191C24), onSurface = Color(0xFFEEF0F6),
            surfaceVariant = Color(0xFF272B36), onSurfaceVariant = Color(0xFF959BAC),
            surfaceContainer = Color(0xFF191C24), surfaceContainerLow = Color(0xFF14171E),
            surfaceContainerHigh = Color(0xFF1F232C), surfaceContainerHighest = Color(0xFF272B36),
            outline = Color(0xFF3A3F4C), outlineVariant = Color(0xFF272B36),
            error = Color(0xFFE0877C), onError = Color(0xFF0E1016),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3F5185), onPrimary = Color.White,
            primaryContainer = Color(0xFFE2E7F5), onPrimaryContainer = Color(0xFF2B3860),
            secondary = Color(0xFF6B4FA0), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDE8F6), onSecondaryContainer = Color(0xFF4E3579),
            tertiary = Color(0xFF1C7268), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFDEEBE8), onTertiaryContainer = Color(0xFF0F3F39),
            background = Color(0xFFF5F6FA), onBackground = Color(0xFF15171E),
            surface = Color.White, onSurface = Color(0xFF15171E),
            surfaceVariant = Color(0xFFE8EAF1), onSurfaceVariant = Color(0xFF5B6076),
            surfaceContainer = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAFBFD),
            surfaceContainerHigh = Color(0xFFEEF0F6), surfaceContainerHighest = Color(0xFFE8EAF1),
            outline = Color(0xFFCFD3DF), outlineVariant = Color(0xFFE2E4EC),
            error = Color(0xFFA33B30), onError = Color.White,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

internal fun clock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

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
    val plan = remember(settings, session.quick, session.quickMin) { session.quick?.let { quickPlan(it, session.quickMin) } ?: plan(settings) }
    val ctx = LocalContext.current
    var stretchBack by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf("") } // "", "history", "settings", "habits", "stretches", "habit:<id>", "routine:<id>"
    var countdown by rememberSaveable { mutableStateOf<String?>(null) } // quick timer about to start
    // Where to land if the countdown is cancelled: changing your mind should put you back on the list you
    // were choosing from, not on Home with the Library closed behind you.
    var countdownFrom by rememberSaveable { mutableStateOf("") }
    // Minimised: whatever is running keeps running, and you get Home back (like a video in its corner).
    var minimised by rememberSaveable { mutableStateOf(false) }
    val running = session.running(plan)
    BackHandler(page.isNotEmpty()) { page = "" }
    BackHandler(countdown != null) { countdown = null; page = countdownFrom }
    // Back on the running screen puts it away instead of closing the app; back on Home still leaves.
    BackHandler(running && !minimised && page.isEmpty() && countdown == null) { minimised = true }
    LaunchedEffect(running) { if (!running) minimised = false }
    val startQuick = rememberStartGuard { id, _ -> minimised = false; countdownFrom = page; page = ""; countdown = id }
    // A nudge's Sketch / Relax button: same 5-4-3-2-1 as tapping the quick timer on home.
    val nudged by Nudges.requestedQuick.collectAsState()
    LaunchedEffect(nudged) {
        nudged?.let { countdownFrom = ""; page = ""; countdown = it; Nudges.requestedQuick.value = null }
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
            // The catalogue opens from Home and from the Library; back belongs where you came from.
            page == "stretches" -> SettingsScreen(settings, start = "stretches") { page = stretchBack }
            page == "library" -> LibraryScreen({ id -> startQuick(id, null) }, onStretches = { stretchBack = "library"; page = "stretches" }) { page = "" }
            page == "shape" -> ShapeScreen(settings, now) { page = "" }
            page == "today" -> DayScreen(History.today(), onDate = {}) { page = "" }
            page.startsWith("habitrun:") -> HabitRunScreen(page.removePrefix("habitrun:")) { page = "" }
            page.startsWith("habit:") -> HabitCalendarScreen(page.removePrefix("habit:")) { page = "" }
            page.startsWith("routine:") -> RoutineScreen(page.removePrefix("routine:"), autoStart) { page = ""; autoStart = false }
            countdown != null -> countdown?.let { id -> Countdown(id) { countdown = null; page = countdownFrom } }
            session.idle || minimised -> Setup(
                settings, now, onHistory = { page = "history" }, onSettings = { page = "settings" }, onHabit = { page = "habitrun:$it" },
                onRoutine = { page = "routine:$it" }, onQuick = { id -> startQuick(id, null) },
                onLibrary = { page = "library" }, onShape = { page = "shape" }, onToday = { page = "today" },
                mini = session.takeIf { minimised }, onRestore = { minimised = false },
            )
            // The day's own record, not the all-time list: right after finishing, today is the question.
            session.done(plan) -> Done(session, settings) { page = "today" }
            else -> Running(plan, session, settings, now, onMinimise = { minimised = true }) { page = "routine:$it" }
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
    s: Settings, now: Long, onHistory: () -> Unit, onSettings: () -> Unit, onHabit: (String) -> Unit,
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

    // A day under way is a different page from a day not yet started: what is on now, what is next,
    // and the tail of the day — the C+ "Day" board, rather than the setup screen with a bar stuck to it.
    if (mini != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(16.dp))
                DayHeader(onHistory)
                EarlierRow(onToday, Modifier.padding(top = 10.dp))
                NowCard(Timer.plan, mini, s, now, onRestore)
                NextCards(Timer.plan, mini, s, now)
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onLibrary, Modifier.fillMaxWidth().padding(vertical = 14.dp).height(72.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text("+  Start something", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
        return
    }

    // Before the day starts: the day's name, the hours as a card with the clock marked on them, the one
    // decision this screen exists for, and everything else today holds in one list.
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        History.today().format(java.time.format.DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    )
                    Text(clock(now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DayDots(onHistory)
                Spacer(Modifier.width(8.dp))
                // The board shows no way into Settings; the app needs one, so the chip stays.
                ProfileChip(onSettings)
            }
            Spacer(Modifier.height(16.dp))
            // What the day has come to so far, before what is left of it: it used to be a line you had
            // to open to see any of.
            TodaySoFar(now, onToday)
            HoursCard(s, now, onShape)
            scheduled?.let { ScheduledLine(it) }
            if (scheduled == null) StartCard(s, now, cyclesLeftToday(s, minuteOfDay(now))) { cycles -> Timer.start(cycles ?: s.cycles) }
            TodayThread(now, onRoutine, onHabit)
            Spacer(Modifier.height(16.dp))
        }
        // The board ends the page with one way to start anything else, not a summary of what is in there.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp).height(52.dp).clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Library", onClick = onLibrary),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("+", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                "  Start something else", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


/**
 * The day drawn to scale: a tall bar per work block, a short one per break, filled as you get through it.
 * The point is to read the shape of what's left from across the room, without doing arithmetic.
 */
@Composable
internal fun DayStrip(
    blocks: List<DayBlock>, modifier: Modifier = Modifier, mark: Float? = null,
    from: Long? = null, to: Long? = null, nowMs: Long = 0,
) {
    if (blocks.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val total = blocks.sumOf { it.ms }.toFloat().coerceAtLeast(1f)
    val elapsed = blocks.sumOf { (it.done * it.ms).toDouble() }.toFloat() / total
    // The gaps are fixed while the blocks share what is left, so a short rhythm (a 1:1 day is 480 blocks)
    // used to spend the whole width on spacing and draw nothing at all. Past the point where a gap costs
    // more than a block, they close up and the day reads as one proportional band.
    val gap = if (blocks.size > 48) 0.dp else 2.dp
    val at = if (elapsed > 0.001f && elapsed < 0.999f) elapsed else mark
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
            if (at != null) Row(Modifier.matchParentSize()) {
                Spacer(Modifier.weight(at))
                Box(Modifier.width(2.dp).fillMaxHeight().background(cs.secondary))
                Spacer(Modifier.weight(1f - at))
            }
        }
        // "now", under the line that marks it. The board names it because a bare rule in a bar of bars
        // reads as one more block rather than as this moment.
        at?.let { a ->
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(a.coerceIn(0.02f, 0.98f)))
                Text(
                    // The time itself, not the word: the line already says "now", and the clock it
                    // points at is the thing you were going to work out anyway.
                    if (nowMs > 0) clock(nowMs) else "now",
                    Modifier.offset(x = (-18).dp), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = cs.secondary,
                )
                Spacer(Modifier.weight(1f - a.coerceIn(0.02f, 0.98f)))
            }
        }
        // A day under way runs from when you started it, which is not the hours printed above the strip: two
        // different days on one screen unless the strip says which one it is.
        if (from != null && to != null) Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
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

/** One end of the day: a tile you tap to pick the time, the time itself big enough to read at a glance. */
@Composable
private fun HourTile(label: String, minuteOfDay: Int, modifier: Modifier, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(cs.surface)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Pick the time") {
                android.app.TimePickerDialog(
                    ctx, { _, h, m -> onPick(h * 60 + m) },
                    minuteOfDay / 60, minuteOfDay % 60, android.text.format.DateFormat.is24HourFormat(ctx),
                ).show()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp, color = cs.onSurfaceVariant,
        )
        Text(
            fmtMinute(minuteOfDay), Modifier.padding(top = 2.dp), fontSize = 24.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, maxLines = 1,
        )
    }
}

/** A [Stepper] in a card of its own. */
@Composable
private fun StepperCard(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surface)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = 13.dp, vertical = 4.dp),
    ) { content() }
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

/**
 * Only one timer runs at a time. Returns the way to start [start]: straight away when nothing is going,
 * otherwise after asking whether to end what is running. Renders the question itself.
 */
/** Starting something: the id, and the length a routine step asked for (null = the activity's own). */
fun interface StartGuard {
    operator fun invoke(id: String, minutes: Int?)
}

/** Most things have only their own length to run for. */
operator fun StartGuard.invoke(id: String) = invoke(id, null)

@Composable
fun rememberStartGuard(start: StartGuard): StartGuard {
    val session by Timer.session.collectAsState()
    val settings by Timer.settings.collectAsState()
    val plan = session.quick?.let { quickPlan(it, session.quickMin) } ?: plan(settings)
    var pending by remember { mutableStateOf<Pair<String, Int?>?>(null) }
    pending?.let { (id, min) ->
        val from = session.quick?.let { activity(it).name } ?: "your workday"
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("One timer at a time") },
            text = { Text("End $from and start ${activity(id).name}?") },
            confirmButton = { TextButton({ pending = null; Timer.stop(); start(id, min) }) { Text("End and start") } },
            dismissButton = { TextButton({ pending = null }) { Text("Keep going") } },
        )
    }
    return StartGuard { id, min -> if (session.running(plan)) pending = id to min else start(id, min) }
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
        ScreenBar("Shape of the day", "Hours in, cycles out", onBack)

        // The two ends of the day, side by side with the span between them: a pair of times you set, not
        // two rows of a form. The cycle count they make is worked out below, never typed in.
        Text(
            "YOUR HOURS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 9.dp),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HourTile("START", s.workStart, Modifier.weight(1f)) { v -> set { copy(workStart = v) } }
            Box(Modifier.padding(horizontal = 10.dp).width(14.dp).height(2.dp).background(cs.outlineVariant))
            HourTile("END", s.workEnd, Modifier.weight(1f)) { v -> set { copy(workEnd = v) } }
        }

        Text("RHYTHM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRESETS.forEach { p ->
                PresetChip(p.name.substringBefore(' '), "${p.work}:${p.brk}", !s.custom && s.workMin == p.work && s.breakMin == p.brk) {
                    set { copy(workMin = p.work, breakMin = p.brk, custom = false) }
                }
            }
            PresetChip("Custom", if (s.custom) "${s.workMin}:${s.breakMin}" else "···", s.custom) { set { copy(custom = true) } }
        }
        // Hours in, cycles out, and what the cycles buy — the count alone never said whether eight
        // cycles meant a long day or a short one. Worked out, never typed in.
        Column(
            Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(cs.surface)
                .border(1.5.dp, cs.primary, RoundedCornerShape(20.dp)).padding(15.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "THAT MAKES", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = cs.primary,
                )
                Text(
                    "worked out for you", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text("${s.cycles}", fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp)
                Text(
                    if (s.cycles == 1) "  cycle fits" else "  cycles fit", Modifier.padding(bottom = 4.dp),
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            // The shape itself, so the number above it has something to be the length of.
            DayStrip(dayBlocks(plan(s), Session(), now), Modifier.fillMaxWidth().padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Figure(fmtDur(s.cycles * s.workMin * MIN), "focus")
                Figure(fmtDur(s.cycles * s.breakMin * MIN), "breaks")
                Figure(if (s.spare > 0) fmtDur(s.spare * MIN) else "none", "spare")
            }
            if (s.spare > 0) Text(
                "The ${s.spare} minutes that don't make another cycle stay free at the end — they aren't padded out.",
                Modifier.padding(top = 7.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
            )
        }

        // One card per number, as the board has them: three rows sharing a card read as a form.
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepperCard { Stepper("Work", s.workMin, "min", 1..180, "One block of focus") { v -> set { copy(workMin = v, custom = true) } } }
            StepperCard { Stepper("Break", s.breakMin, "min", 1..60, "Between blocks") { v -> set { copy(breakMin = v, custom = true) } } }
            StepperCard { Stepper("Prepare (in break)", s.prepMin, "min", 0..10, "Lie down before the activity") { v -> set { copy(prepMin = v) } } }
        }

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "The daily nudge follows your start time", Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            Text(clockMin(s.nudgeMin), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        Button(
            onBack, Modifier.fillMaxWidth().padding(top = 12.dp).height(72.dp), shape = RoundedCornerShape(20.dp),
        ) { Text("Use this shape", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }

        Section("START IT LATER") {
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
private fun LibraryScreen(onQuick: (String) -> Unit, onStretches: () -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ambient by Timer.ambient.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenBar("Your things", "Tap any of them to start it now", onBack)
        QuickTimers(onQuick)
        MyLibrary(onQuick)
        PlansShelf(onStretches)
        Text(
            "SOUND WHILE YOU WORK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = cs.onSurfaceVariant,
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
    Text("BUILT IN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
    // The board draws the line between these and the plans below: one-off against a dose that grows.
    // It says "no record kept", which isn't true here — the time does land in the day — so this says
    // the true half: there is nothing to keep up with.
    Text(
        "One-off. The time lands in today's line; there is nothing to keep up with.",
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    // A row each, as the board has them: a grid of four tiles has room for a name and a number, and the
    // one thing you want before starting something is what it is.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QUICK.map(::activity).forEach { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surface)
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Start ${a.name}") { onQuick(a.id) }
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Text(a.emoji, fontSize = 18.sp) }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(a.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    // The hint is written as a sentence or two; the row has space for the first.
                    Text(
                        a.hint.substringBefore(". ").trimEnd('.'), Modifier.padding(top = 1.dp),
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${a.quickMin} min", Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                )
            }
        }
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
internal fun RowScope.ActivityTile(a: Activity, caption: String, bg: Color, fg: Color, sub: Color, label: String, onClick: () -> Unit) =
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
internal fun Stepper(label: String, value: Int, unit: String, range: IntRange, note: String? = null, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            // The board names what each number is for: "work" alone never said which minutes it meant.
            note?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        StepButton("−", "Less", value > range.first) { onChange((value - 1).coerceIn(range)) }
        // Tap the number to type it; −/+ stay for small nudges.
        var editing by remember { mutableStateOf(false) }
        Text(
            "$value $unit".trim(),
            Modifier.width(64.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Edit $label") { editing = true }.padding(vertical = 8.dp),
            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
        )
        if (editing) NumberDialog(label, value, unit, range, onDismiss = { editing = false }) { onChange(it); editing = false }
        StepButton("+", "More", value < range.last) { onChange((value + 1).coerceIn(range)) }
    }
}

/** The −/+ of a [Stepper]: a rounded square, as the boards draw them, not a bare glyph. */
@Composable
private fun StepButton(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (enabled) cs.onSurface else cs.outlineVariant)
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

// ---------- Home while a workday runs (C+ "Day" board) ----------

/** The day's name and how often you showed up, as dots: the board's header. */
@Composable
private fun DayHeader(onHistory: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val days by History.days.collectAsState()
    val today = History.today()
    val d = density(days, today)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE")),
            Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        )
        DayDots(onHistory)
    }
}

/** How often you showed up, as dots — or the plain History pill while there is too little to judge. */
@Composable
private fun DayDots(onHistory: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val days by History.days.collectAsState()
    val today = History.today()
    val d = density(days, today)
    if (!d.worthShowing) return HistoryPill(onHistory)
    Row(
        Modifier.clip(CircleShape).background(cs.surface).border(1.dp, cs.outlineVariant, CircleShape)
            .clickable(onClickLabel = "History", onClick = onHistory).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (d.of - 1 downTo 0).forEach { i ->
                val on = days.any { it.date == today.minusDays(i.toLong()) && it.active }
                Box(
                    Modifier.size(5.dp).clip(RoundedCornerShape(1.5.dp))
                        .background(if (on) cs.primary else cs.surfaceContainerHighest),
                )
            }
        }
        Text(
            "${d.done} of ${d.of}", Modifier.padding(start = 7.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** What is on right now: the phase, its clock, and how far through the block you are. */
@Composable
private fun NowCard(plan: List<Phase>, s: Session, settings: Settings, now: Long, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val p = plan.getOrNull(s.index) ?: return
    val left = s.left(now)
    val phaseMs = (s.phaseMs.takeIf { it > 0 } ?: p.ms).coerceAtLeast(1)
    val work = p.kind == Kind.WORK
    val done = ((phaseMs - left).toFloat() / phaseMs).coerceIn(0f, 1f)
    Card(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Back to the timer", onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.5.dp, cs.primary),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "NOW · ${clock(now)}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = cs.primary,
                )
                Text(
                    "CYCLE ${p.cycle} OF ${cyclesIn(plan)}", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = cs.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    if (work) "Work" else "Break · ${activity(s.quick ?: settings.activity).name}",
                    Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(fmt(left), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 11.dp).height(18.dp)
                    .clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHighest),
            ) {
                Box(
                    Modifier.fillMaxWidth(done).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                        .background(if (work) cs.primary else cs.tertiary),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                val style = MaterialTheme.typography.labelSmall
                // "0m in" for the first minute of a block is the printed zero again: it has just begun.
                val gone = phaseMs - left
                Text(
                    if (gone >= MIN) "${fmtDur(gone)} in" else "just started",
                    Modifier.weight(1f), style = style, color = cs.onSurfaceVariant,
                )
                Text("${fmtDur(phaseMs)} block", style = style, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** What comes after this block: a card each, the bar beside it as long as the block it stands for. */
@Composable
private fun NextCards(plan: List<Phase>, s: Session, settings: Settings, now: Long) {
    val cs = MaterialTheme.colorScheme
    val next = upNext(plan, s, now)
    if (next.isEmpty()) return
    val breakName = activity(settings.activity).name
    val longest = next.maxOf { it.ms }.coerceAtLeast(1)
    Text(
        "NEXT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 7.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        next.forEach { u ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surface)
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp)).padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                // Proportional, so a 10-minute break really is a fifth of a 50-minute block.
                Box(
                    Modifier.width(5.dp).height((14 + 46 * (u.ms.toFloat() / longest)).dp)
                        .clip(RoundedCornerShape(3.dp)).background(if (u.work) cs.primary else cs.tertiary),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (u.work) "Cycle ${u.cycle} · work" else "Break · $breakName",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                    // The board spells it out — "10 minutes", not "10m" — because this list is read at a
                    // glance to decide whether a thing fits, not scanned for totals.
                    val mins = ((u.ms + 30_000) / 60_000L).toInt().coerceAtLeast(1)
                    Text(
                        "$mins ${if (mins == 1) "minute" else "minutes"}",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
                Text(
                    clock(u.at), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant,
                )
            }
        }
        TailCard(plan, s, now, next)
    }
}

/** The rest of the day in one dashed line, so the list does not run on past what you can act on. */
@Composable
private fun TailCard(plan: List<Phase>, s: Session, now: Long, shown: List<Upcoming>) {
    val cs = MaterialTheme.colorScheme
    val after = cyclesAfter(plan, shown)
    if (after <= 0) return
    val endsAt = now + s.left(now).coerceAtLeast(0) + plan.drop(s.index + 1).sumOf { it.ms }
    val nextRoutine = Routines.all.collectAsState().value
        .filterNot { it.deleted }.mapNotNull { r -> r.remindMin?.let { it to r } }
        .filter { it.first > minuteOfDay(now) }.minByOrNull { it.first }
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawRoundRect(
                cs.outlineVariant, style = Stroke(1.dp.toPx(), pathEffect = dash),
                cornerRadius = CornerRadius(12.dp.toPx()),
            )
        }.padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(Modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            repeat(10) { i ->
                Box(
                    Modifier.width(4.dp).height(if (i % 2 == 0) 22.dp else 8.dp)
                        .clip(RoundedCornerShape(2.dp)).background(cs.outlineVariant),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                "$after more ${if (after == 1) "cycle" else "cycles"} after that",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Runs to ${clock(endsAt)}" + (nextRoutine?.let { " · ${it.second.name} at ${fmtMinute(it.first)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What today already holds, as one line that opens the day's own record. Absent entirely on a morning
 * that hasn't started — the day ahead is the point of this screen, and an empty "Earlier" row would
 * just be furniture.
 */
@Composable
private fun EarlierRow(onToday: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val today = History.today()
    val todayRec = History.days.collectAsState().value.firstOrNull { it.date == today }
    val liveHabits by Habits.all.collectAsState()
    val doneRoutines = Routines.all.collectAsState().value
        .filterNot { it.deleted }.filter { doneToday(it, today) != null }.map { it.name }
    val doneHabits = liveHabits.count { !it.ended && today in it.done }
    val line = earlierToday(todayRec?.workMs ?: 0L, todayRec?.cycles ?: 0, doneRoutines, doneHabits) ?: return
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "How today went", onClick = onToday).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_down), null,
            Modifier.padding(end = 9.dp).size(18.dp), tint = cs.onSurfaceVariant,
        )
        Text("Earlier · $line", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    }
}

/**
 * The day's own record at the top of the idle screen: what it has come to in three numbers, and the
 * shape of it when a workday has run. It was a line saying "Earlier · Morning done" that you had to
 * open to see any of.
 */
@Composable
private fun TodaySoFar(now: Long, onToday: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = History.today()
    val todayRec = History.days.collectAsState().value.firstOrNull { it.date == today }
    val liveHabits by Habits.all.collectAsState()
    // The raw fact that it finished, not routineDoneToday — that asks whether it is still on your list
    // (a stretch falling due again reopens it), which is a different question from what happened.
    val routines = Routines.all.collectAsState().value.filterNot { it.deleted }
    val doneRoutines = routines.filter { doneToday(it, today) != null }
    val doneHabits = liveHabits.count { !it.ended && today in it.done }
    // Nothing yet is not a day to summarise: no empty wave, no row of zeros.
    earlierToday(todayRec?.workMs ?: 0L, todayRec?.cycles ?: 0, doneRoutines.map { it.name }, doneHabits) ?: return
    val stored by History.timelines.collectAsState()
    val session by Timer.session.collectAsState()
    val segs = remember(stored, session, now) {
        dropBlips(joined(History.timeline(today, session, Timer.plan, now)), keepLast = false)
    }
    val work = todayRec?.workMs ?: 0
    val cycles = todayRec?.cycles ?: 0
    // No clip on the way round: the block has no background of its own, and a 16dp corner at the top
    // left was taking a bite out of the T.
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp)
            .clickable(onClickLabel = "How today went", onClick = onToday),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TODAY SO FAR", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = cs.onSurfaceVariant,
            )
            Text(
                "See it all ›", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = cs.secondary,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            EndTile("FOCUSED", if (work > 0) fmtDur(work) else "—", Modifier.weight(1f))
            EndTile("CYCLES", if (cycles > 0) "$cycles" else "—", Modifier.weight(1f))
            EndTile(
                "ROUTINES",
                if (doneRoutines.isEmpty()) "—" else "${doneRoutines.size} of ${routines.size}",
                Modifier.weight(1f),
            )
        }
        // The wave only has something to draw once a workday has run; the tiles carry the rest.
        if (segs.isNotEmpty()) Column(
            Modifier.fillMaxWidth().padding(top = 9.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(12.dp),
        ) { DayWave(segs) }
    }
}

// ---------- Home before the day starts (C+ "Day idle" board) ----------

/** Your hours as a card: the shape of the day with the clock marked on it. */
@Composable
private fun HoursCard(s: Settings, now: Long, onShape: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val total = plan(s).sumOf { it.ms }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surface)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Shape of the day", onClick = onShape).padding(14.dp),
    ) {
        Text(
            "YOUR HOURS", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = cs.onSurfaceVariant,
        )
        DayStrip(
            dayBlocks(plan(s), Session(), now), Modifier.fillMaxWidth().padding(top = 12.dp),
            mark = dayGone(minuteOfDay(now), s.workStart, (total / 60_000L).toInt()), nowMs = now,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val style = MaterialTheme.typography.labelMedium
            Text(clockMin(s.workStart), Modifier.weight(1f), style = style, color = cs.onSurfaceVariant)
            Text(clockMin(s.workEnd), style = style, color = cs.onSurfaceVariant)
        }
    }
}

/** The one decision this screen exists for: start the day, and on a late morning how much of it. */
@Composable
private fun StartCard(s: Settings, now: Long, fits: Int, onStart: (Int?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val total = plan(s).sumOf { it.ms }
    val late = fits in 1 until s.cycles
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(cs.surface)
            .border(1.5.dp, cs.primary, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text(
            // Spelled, and a sentence starts with a capital: "eight cycles, ending at 4:42 AM." read as a
            // fragment someone had cut the front off.
            if (late) "${fits.spelled().replaceFirstChar { it.uppercase() }} ${if (fits == 1) "cycle fits" else "cycles fit"} before ${clockMin(s.workEnd)}."
            else "${s.cycles.spelled().replaceFirstChar { it.uppercase() }} ${if (s.cycles == 1) "cycle" else "cycles"}, ending at ${clock(now + total)}.",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (late) "Or run the full ${s.cycles.spelled()} and finish at ${clock(now + total)}. Both are fine."
            else "Start when you like — the hours above are only the shape.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (late) {
                TwoLineButton("Fit to ${clockMin(s.workEnd)}", "$fits ${if (fits == 1) "cycle" else "cycles"}", true, Modifier.weight(1f)) { onStart(fits) }
                TwoLineButton("Run all ${s.cycles.spelled()}", "ends ${clock(now + total)}", false, Modifier.weight(1f)) { onStart(null) }
            } else {
                TwoLineButton("Start now", "ends ${clock(now + total)}", true, Modifier.weight(1f)) { onStart(null) }
            }
        }
    }
}

/** The board's two-line button: what it does, and what that costs, in smaller type beneath. */
@Composable
private fun TwoLineButton(title: String, sub: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.height(56.dp).clip(shape)
            .then(if (filled) Modifier.background(cs.primary) else Modifier.border(1.dp, cs.outline, shape))
            .clickable(onClickLabel = title, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val fg = if (filled) cs.onPrimary else cs.primary
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.78f), maxLines = 1)
    }
}

internal fun Int.spelled() = listOf(
    "no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
).getOrNull(this) ?: toString()

/** One thing in the day's thread: what it is, when, and whether it is behind you. */
private data class DayRow(
    val at: Int?,            // minute of day; no hour of its own sorts to the end
    val glyph: String,
    val name: String,
    val meta: String,
    val right: String,
    val done: Boolean,
    val route: String = "",  // empty: a record, not something to open
)

/**
 * The whole day as one thread: what you have done, ticked and struck through, and what is still to
 * come, in the order the clock puts them. It used to be two lists on two screens — what happened was
 * a page away, and what was left sat on Home with no sight of the rest.
 */
@Composable
private fun TodayThread(now: Long, onRoutine: (String) -> Unit, onHabit: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = History.today()
    val routines by Routines.all.collectAsState()
    val allHabits by Habits.all.collectAsState()
    val items by Library.items.collectAsState()
    val habitTimes by Habits.times.collectAsState()
    val stored by History.timelines.collectAsState()
    val session by Timer.session.collectAsState()
    val dayRec = History.days.collectAsState().value.firstOrNull { it.date == today }
    val segs = remember(stored, session, now) {
        dropBlips(joined(History.timeline(today, session, Timer.plan, now)), keepLast = false)
    }
    val live = routines.filterNot { it.deleted }
    val rows = buildList {
        // The workday as one entry: the wave above already draws every block of it.
        val plain = segs.filter { it.label.isEmpty() }
        if (plain.isNotEmpty()) {
            val n = dayRec?.cycles ?: 0
            add(
                DayRow(
                    minuteOfDay(plain.first().start), "🎯",
                    if (n > 1) "Cycles 1–$n" else if (n == 1) "One cycle" else "Work",
                    workdayLine(plain).orEmpty(), fmtDur(plain.last().end - plain.first().start), true,
                )
            )
        }
        // Each stretch you chose an activity for, on its own. A routine's own timer is left to the
        // routine's row, which says the same thing with its name on it.
        segs.filter { it.label.isNotEmpty() && !it.label.startsWith("routine:") }.forEach { seg ->
            val a = activity(seg.label)
            add(DayRow(minuteOfDay(seg.start), a.emoji, a.name, "", fmtDur(seg.ms), true))
        }
        live.forEach { r ->
            val at = doneToday(r, today)
            add(
                if (at != null) DayRow(at, r.emoji, r.name, "${r.steps.size} steps", "", true, "routine:${r.id}")
                else DayRow(r.remindMin, r.emoji, r.name, routineSummary(r, r.minutes), "", false, "routine:${r.id}")
            )
        }
        val inRoutines = habitsInRoutines(live)
        allHabits.filterNot { it.ended || it.id in inRoutines }.forEach { h ->
            val day = h.dayOf(today)
            val dose = h.stageOn(day)?.let(::label).orEmpty()
            add(
                if (today in h.done) DayRow(
                    habitTimes[today]?.get(h.id), habitGlyph(h, items), habitTitle(h, items),
                    "Day $day of ${h.totalDays}", dose, true, "habit:${h.id}",
                ) else DayRow(
                    h.remindMin, habitGlyph(h, items), habitTitle(h, items),
                    "Day $day of ${h.totalDays}", dose, false, "habit:${h.id}",
                )
            )
        }
    }.sortedWith(compareBy(nullsLast()) { it.at })
    if (rows.isEmpty()) return

    Text(
        "THE DAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, r ->
            val last = i == rows.lastIndex
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Text(
                    r.at?.let(::fmtMinute) ?: "Any", Modifier.width(52.dp).padding(top = 12.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = cs.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1,
                )
                Column(
                    Modifier.width(13.dp).fillMaxHeight().padding(start = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Done is a filled dot, still to come an outline. A tick drawn this small is a
                    // smudge; the line through the name is what says it is behind you.
                    Box(
                        Modifier.padding(top = 14.dp).size(if (r.done) 11.dp else 9.dp).clip(CircleShape)
                            .background(if (r.done) cs.primary else cs.surface)
                            .then(if (r.done) Modifier else Modifier.border(2.dp, cs.outlineVariant, CircleShape)),
                    )
                    if (!last) Box(Modifier.width(2.dp).weight(1f).background(cs.outlineVariant))
                }
                Row(
                    Modifier.weight(1f).padding(start = 11.dp, top = 4.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (r.done) cs.surfaceContainerHigh else cs.surface)
                        .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
                        .then(
                            if (r.route.isEmpty()) Modifier
                            else Modifier.clickable(onClickLabel = "Open ${r.name}") {
                                if (r.route.startsWith("routine:")) onRoutine(r.route.removePrefix("routine:"))
                                else onHabit(r.route.removePrefix("habit:"))
                            }
                        )
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) { Text(r.glyph, fontSize = 15.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (r.done) cs.onSurfaceVariant else cs.onSurface,
                            textDecoration = if (r.done) TextDecoration.LineThrough else null,
                        )
                        if (r.meta.isNotEmpty()) Text(
                            r.meta, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (r.right.isNotEmpty()) Text(
                        r.right, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The header every secondary screen wears on the boards: a round chevron that closes the screen, the
 * title beside it, and a quiet line under it. Not a back arrow with a headline — the boards never
 * draw one, and the chevron says "this came up over the day" rather than "you have gone somewhere".
 */
@Composable
internal fun ScreenBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundIcon(R.drawable.ic_chevron_down, "Back", onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        trailing?.invoke()
    }
}

/** One of the "that makes" figures: the number in full strength, what it is in grey beside it. */
@Composable
private fun Figure(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(" $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
