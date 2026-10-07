package com.shivam1410.intervaltimer

// Pure timer logic: no Android imports, so it is unit-testable on the JVM.
// The source of truth is absolute timestamps (endsAt), never a ticking counter.

const val MIN = 60_000L

data class Settings(
    val workMin: Int = 50,
    val breakMin: Int = 10,
    val prepMin: Int = 2,
    val cycles: Int = 8,
    // Picked during the session, remembered as the default for the next break / work block.
    val activity: String = "breathing",
    val focusSound: String = "", // "" = off
    val gong: Boolean = true,
    val vibrate: Boolean = true,
    val volume: Int = 70,
    val waitBeforeWork: Boolean = false,
    val custom: Boolean = false, // "Custom" preset chosen: the schedule steppers are shown
)

data class Preset(val name: String, val work: Int, val brk: Int, val cycles: Int)

val PRESETS = listOf(
    Preset("Workday", 50, 10, 8),
    Preset("Deep work", 90, 15, 4),
    Preset("Pomodoro", 25, 5, 12),
)

data class Step(val name: String, val sec: Int)

data class Activity(
    val id: String,
    val emoji: String,
    val name: String,
    val group: String,
    val hint: String,
    val sound: String? = null, // id from resources/manifest.json, looped during the activity
    val minutes: Int? = null, // the activity part of the break lasts at least this long
    val steps: List<Step> = emptyList(), // guided routine, looped for the activity's length
    val tracks: List<String> = emptyList(), // YouTube / YouTube Music links; one is picked at random on play
    val playLabel: String = "",
    val app: String? = null, // package launched by the activity's button
    val quickMin: Int? = null, // offered as a one-off quick timer on home, for this long
)

const val SKETCH_SEED = "com.shivam.sketchseed"

val ACTIVITIES = listOf(
    Activity("breathing", "🫁", "Box breathing", "Recover", "Inhale 4 · hold 4 · exhale 4 · hold 4", quickMin = 5),
    Activity("meditation", "🧘", "Meditation", "Recover", "Eyes closed. Follow the breath. Let thoughts pass.", "bowl"),
    Activity(
        "nsdr", "😌", "NSDR", "Recover", "Lie down, press play and follow the voice. 20-minute Non-Sleep Deep Rest.",
        minutes = 20,
        tracks = listOf(
            "https://music.youtube.com/watch?v=bk_lD69u204", // Yog Nidra — Sri Sri Ravi Shankar
            "https://music.youtube.com/watch?v=iRR2yCoIaYY", // 20 Minute NSDR — Dr. Andrew Huberman
        ),
        playLabel = "Play NSDR on YouTube Music",
        quickMin = 20,
    ),
    Activity("nap", "💤", "Power nap", "Recover", "Sleep. The gong will wake you.", minutes = 20, quickMin = 20),
    Activity(
        "stretch", "🧎", "Stretch", "Move", "Play a guided video, or follow the steps below.",
        minutes = 20,
        tracks = listOf(
            "https://www.youtube.com/watch?v=AF9d2Icl4fA", // Yoga Stretch — Yoga With Adriene
            "https://www.youtube.com/watch?v=sTANio_2E0Q", // 20 min Full Body Stretch for stress — MadFit
            "https://www.youtube.com/watch?v=aGcwjh4kETQ", // 20 Min Daily Yoga Stretch — Mady Morrison
            "https://www.youtube.com/watch?v=FFYQ4MEvueY", // 20 min Deep Yoga Stretch — YOGATX
        ),
        playLabel = "Play a 20-min stretch video",
        quickMin = 20,
        steps = listOf(
            Step("Neck rolls, slow circles", 40), Step("Shoulder rolls, back and down", 40),
            Step("Chest opener: hands clasped behind you", 40), Step("Side bend, left then right", 40),
            Step("Forward fold, knees soft", 40), Step("Hip flexor lunge, switch at halfway", 60),
        ),
    ),
    Activity("walk", "🚶", "Walk", "Move", "Stand up and walk until the gong. Leave the phone.", quickMin = 15),
    Activity(
        "exercise", "💪", "Exercise", "Move", "Bodyweight circuit: 40 s on, 20 s rest. Repeat until the gong.",
        quickMin = 15,
        steps = listOf(
            Step("Squats", 40), Step("Rest", 20), Step("Push-ups (knees are fine)", 40), Step("Rest", 20),
            Step("Reverse lunges, alternate legs", 40), Step("Rest", 20), Step("Plank", 40), Step("Rest", 20),
            Step("Glute bridges", 40), Step("Rest", 20), Step("Jumping jacks", 40), Step("Rest", 20),
        ),
    ),
    Activity(
        "eyes", "👀", "Eye + neck reset", "Move", "Rest your eyes and release the neck.",
        steps = listOf(
            Step("Look at something 20 ft away", 30), Step("Palming: warm palms over closed eyes", 40),
            Step("Slow eye circles, both directions", 30), Step("Near–far focus: thumb, then the wall", 30),
            Step("Chin tucks, hold 3 seconds each", 30), Step("Ear to shoulder, left then right", 40),
        ),
    ),
    Activity("ambient", "🌧", "Ambient sound", "Refresh", "Rain. Just listen.", "rain"),
    Activity("silence", "🤫", "Silence", "Refresh", "Nothing. Just be."),
    Activity("sketch", "✏️", "Sketch", "Create", "Draw today's prompt in Sketch Seed.", app = SKETCH_SEED, quickMin = 20),
)

/** The step of a guided routine at [elapsedMs] into the activity (routine loops), and seconds left in it. */
fun stepAt(steps: List<Step>, elapsedMs: Long): Pair<Int, Int>? {
    if (steps.isEmpty()) return null
    var t = ((elapsedMs / 1000) % steps.sumOf { it.sec }).toInt()
    steps.forEachIndexed { i, st ->
        if (t < st.sec) return i to st.sec - t
        t -= st.sec
    }
    return null
}

fun activity(id: String) = ACTIVITIES.firstOrNull { it.id == id } ?: ACTIVITIES[0]

enum class Kind { WORK, PREP, ACTIVITY }

data class Phase(val kind: Kind, val cycle: Int, val ms: Long)

/** Home quick timers, in the order shown. */
val QUICK = listOf("nsdr", "sketch", "stretch", "exercise", "walk", "nap", "breathing")

/** A one-off quick timer is a single activity phase. */
fun quickPlan(id: String): List<Phase> = listOf(Phase(Kind.ACTIVITY, 1, (activity(id).quickMin ?: 10) * MIN))

/** Flattens the workday into phases: WORK, then a break split into PREP + ACTIVITY, per cycle. */
fun plan(s: Settings): List<Phase> = buildList {
    for (c in 1..s.cycles) {
        add(Phase(Kind.WORK, c, s.workMin * MIN))
        val prep = s.prepMin.coerceIn(0, s.breakMin)
        if (prep > 0) add(Phase(Kind.PREP, c, prep * MIN))
        val minutes = maxOf(s.breakMin - prep, activity(s.activity).minutes ?: 0)
        if (minutes > 0) add(Phase(Kind.ACTIVITY, c, minutes * MIN))
    }
}

/**
 * index: -1 idle, 0..plan.size-1 running, plan.size done.
 * pausedLeft > 0 means paused with that many ms left in the current phase.
 * phaseMs is the current phase's real length (planned + any break extension);
 * bonus is extra time requested during PREP, applied when the activity starts.
 */
data class Session(
    val index: Int = -1,
    val endsAt: Long = 0,
    val pausedLeft: Long = 0,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val phaseMs: Long = 0,
    val bonus: Long = 0,
    // Actual time spent in finished phases (skips count only what elapsed) and work blocks run to the end.
    val workMs: Long = 0,
    val breakMs: Long = 0,
    val cycles: Int = 0,
    val quick: String? = null, // activity id when this is a one-off quick timer, not a workday
) {
    val idle get() = index < 0
    val paused get() = pausedLeft > 0
    fun done(plan: List<Phase>) = index >= plan.size
    fun running(plan: List<Phase>) = !idle && !done(plan)
    fun left(now: Long) = if (paused) pausedLeft else (endsAt - now).coerceAtLeast(0)
}

fun start(plan: List<Phase>, now: Long) = Session(0, now + plan[0].ms, 0, now, phaseMs = plan[0].ms)

fun pause(s: Session, now: Long) = s.copy(pausedLeft = (s.endsAt - now).coerceAtLeast(1))

fun resume(s: Session, now: Long) = s.copy(endsAt = now + s.pausedLeft, pausedLeft = 0)

/** Jump to the next phase immediately. */
fun skip(plan: List<Phase>, s: Session, now: Long): Session = enter(plan, s, s.index + 1, now)

/** Banks the time spent in the current phase, as if it ended at [at]. */
fun credit(plan: List<Phase>, s: Session, at: Long): Session {
    if (!s.running(plan)) return s
    val left = if (s.paused) s.pausedLeft else (s.endsAt - at).coerceAtLeast(0)
    val spent = (s.phaseMs - left).coerceAtLeast(0)
    return if (plan[s.index].kind == Kind.WORK) s.copy(workMs = s.workMs + spent, cycles = s.cycles + if (left == 0L) 1 else 0)
    else s.copy(breakMs = s.breakMs + spent)
}

private fun enter(plan: List<Phase>, prev: Session, i: Int, startAt: Long): Session {
    val s = credit(plan, prev, startAt)
    if (i >= plan.size) return s.copy(index = plan.size, endsAt = startAt, pausedLeft = 0, finishedAt = startAt, bonus = 0)
    val ms = plan[i].ms + if (plan[i].kind == Kind.ACTIVITY) s.bonus else 0
    return s.copy(index = i, endsAt = startAt + ms, pausedLeft = 0, phaseMs = ms, bonus = if (plan[i].kind == Kind.WORK) 0 else s.bonus)
}

/** Lengthens the current break; everything after it shifts later. No effect during work. */
fun extend(plan: List<Phase>, s: Session, ms: Long): Session {
    if (!s.running(plan)) return s
    return when (plan[s.index].kind) {
        Kind.WORK -> s
        Kind.PREP -> s.copy(bonus = s.bonus + ms)
        Kind.ACTIVITY -> s.copy(
            endsAt = s.endsAt + if (s.paused) 0 else ms,
            pausedLeft = if (s.paused) s.pausedLeft + ms else 0,
            phaseMs = s.phaseMs + ms,
            bonus = s.bonus + ms,
        )
    }
}

/** Grows or shrinks the phase in progress by [deltaMs] (activity switched to/from a fixed-length one). */
fun resize(s: Session, deltaMs: Long): Session = s.copy(
    phaseMs = s.phaseMs + deltaMs,
    endsAt = if (s.paused) s.endsAt else s.endsAt + deltaMs,
    pausedLeft = if (s.paused) (s.pausedLeft + deltaMs).coerceAtLeast(1) else 0,
)

/** Minutes this break has been lengthened by (shown on the break screen). */
fun extraMin(s: Session) = s.bonus / MIN

/**
 * Advances through every phase boundary that has passed. Restart-proof: after a reboot
 * or a missed alarm this reconstructs the correct phase from timestamps alone.
 * With [waitBeforeWork], stops (paused, full duration) at the start of the next WORK phase.
 */
fun catchUp(plan: List<Phase>, s: Session, now: Long, waitBeforeWork: Boolean = false): Session {
    var cur = s
    while (cur.running(plan) && !cur.paused && now >= cur.endsAt) {
        cur = enter(plan, cur, cur.index + 1, cur.endsAt)
        if (waitBeforeWork && cur.running(plan) && plan[cur.index].kind == Kind.WORK) {
            return cur.copy(pausedLeft = cur.phaseMs)
        }
    }
    return cur
}

/** Work and break time actually spent so far, including the phase in progress (ms). */
fun totals(plan: List<Phase>, s: Session, now: Long): Pair<Long, Long> =
    credit(plan, s, now).let { it.workMs to it.breakMs }

/** "1.2.10" > "1.2.9"; tolerates a leading "v". */
fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
    val a = parts(remote)
    val b = parts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}

fun fmt(ms: Long): String {
    val t = (ms + 999) / 1000
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%02d:%02d".format(t / 60, t % 60)
}

fun fmtDur(ms: Long): String {
    val m = ms / MIN
    return if (m >= 60) "${m / 60}h %02dm".format(m % 60) else "${m}m"
}

// ponytail: phone mics and rooms vary; -45 dBFS is "quiet" on a Pixel in a still room. Tune here if needed.
const val QUIET_DB = -45f

/** Loudness of a mic buffer in dBFS (0 = full scale, about -90 = digital silence). */
fun dbfs(buf: ShortArray, n: Int): Float {
    if (n <= 0) return -90f
    var sum = 0.0
    for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
    val rms = kotlin.math.sqrt(sum / n) / 32768.0
    return if (rms <= 0) -90f else (20 * kotlin.math.log10(rms)).toFloat().coerceAtLeast(-90f)
}

/** Fraction of measured moments at or below the quiet line. */
fun quietShare(levels: List<Float>, threshold: Float = QUIET_DB): Float =
    if (levels.isEmpty()) 1f else levels.count { it <= threshold }.toFloat() / levels.size
