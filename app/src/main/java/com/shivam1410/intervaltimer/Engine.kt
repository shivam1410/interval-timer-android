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
)

data class Preset(val name: String, val work: Int, val brk: Int, val cycles: Int)

val PRESETS = listOf(
    Preset("Workday", 50, 10, 8),
    Preset("Deep work", 90, 15, 4),
    Preset("Pomodoro", 25, 5, 12),
)

data class Activity(
    val id: String,
    val emoji: String,
    val name: String,
    val group: String,
    val hint: String,
    val sound: String? = null, // id from resources/manifest.json, looped during the activity
)

val ACTIVITIES = listOf(
    Activity("breathing", "🫁", "Box breathing", "Recover", "Inhale 4 · hold 4 · exhale 4 · hold 4"),
    Activity("meditation", "🧘", "Meditation", "Recover", "Eyes closed. Follow the breath. Let thoughts pass.", "bowl"),
    Activity("nsdr", "😌", "NSDR / Yoga Nidra", "Recover", "Lie still. Slowly scan the body from toes to head.", "brown_noise"),
    Activity("nap", "💤", "Power nap", "Recover", "Sleep. The gong will wake you."),
    Activity("stretch", "🧎", "Stretch", "Move", "Neck rolls, shoulder shrugs, forward fold, hip openers."),
    Activity("walk", "🚶", "Walk", "Move", "Stand up and walk. Leave the phone."),
    Activity("eyes", "👀", "Eye + neck reset", "Move", "Look far away. Palm your eyes. Roll your neck."),
    Activity("hydrate", "💧", "Hydrate", "Refresh", "Drink a full glass of water."),
    Activity("ambient", "🌧", "Ambient sound", "Refresh", "Rain. Just listen.", "rain"),
    Activity("silence", "🤫", "Silence", "Refresh", "Nothing. Just be."),
)

fun activity(id: String) = ACTIVITIES.firstOrNull { it.id == id } ?: ACTIVITIES[0]

enum class Kind { WORK, PREP, ACTIVITY }

data class Phase(val kind: Kind, val cycle: Int, val ms: Long)

/** Flattens the workday into phases: WORK, then a break split into PREP + ACTIVITY, per cycle. */
fun plan(s: Settings): List<Phase> = buildList {
    for (c in 1..s.cycles) {
        add(Phase(Kind.WORK, c, s.workMin * MIN))
        val prep = s.prepMin.coerceIn(0, s.breakMin)
        if (prep > 0) add(Phase(Kind.PREP, c, prep * MIN))
        if (s.breakMin > prep) add(Phase(Kind.ACTIVITY, c, (s.breakMin - prep) * MIN))
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

private fun enter(plan: List<Phase>, s: Session, i: Int, startAt: Long): Session {
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

/** Planned work and break time already behind us (ms). */
fun totals(plan: List<Phase>, s: Session, now: Long): Pair<Long, Long> {
    var work = 0L
    var rest = 0L
    plan.forEachIndexed { i, p ->
        val spent = when {
            i < s.index -> p.ms
            i == s.index && s.running(plan) -> s.phaseMs - s.left(now)
            else -> 0
        }
        if (p.kind == Kind.WORK) work += spent else rest += spent
    }
    return work to rest
}

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
