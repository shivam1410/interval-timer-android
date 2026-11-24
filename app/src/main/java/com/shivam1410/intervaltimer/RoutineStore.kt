package com.shivam1410.intervaltimer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.LocalTime

/**
 * Your routines in shared prefs (Google backup carries them). First run seeds Morning, Leaving the house
 * and Evening. [all] keeps deletes as tombstones so a sync can't bring them back; the UI shows [visible].
 * Synced to Drive as routines.json. Side-effect boundary for their reminders: one exact alarm at the
 * soonest, a notification per due routine whose Start runs it and whose Open opens it ([requested], read by Home).
 */
object Routines {
    private const val CH = "routine"
    private const val NOTIF_BASE = 6000
    const val EXTRA_ROUTINE = "routine"
    private const val EXTRA_START = "start"
    /** A routine to open, set by a reminder's Start (MainActivity) and cleared by Home once shown. */
    val requested = MutableStateFlow<String?>(null)
    /** True when [requested] came from Start rather than Open: the routine's timer runs straight away. */
    var startRequested = false
        private set
    val all = MutableStateFlow<List<Routine>>(emptyList())
    val visible get() = all.value.filterNot { it.deleted }
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("routines", Context.MODE_PRIVATE)
    // How long each step usually takes, measured by running them. Kept on this phone and never synced:
    // it is this phone's reading of this body on these mornings, and nothing else depends on it.
    private val stepPrefs get() = app.getSharedPreferences("stepTimes", Context.MODE_PRIVATE)
    val typical = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun init(ctx: Context) {
        app = ctx.applicationContext
        val stored = prefs.getString("items", null)
        all.value = if (stored == null) DEFAULT_ROUTINES else decodeRoutines(stored)
        routineActivities = all.value.filterNot { it.deleted }.map { it.toActivity() }
        typical.value = stepPrefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "Routine reminder", NotificationManager.IMPORTANCE_HIGH))
        arm() // boot and updates start the process, which lands here
    }

    /** A step just finished in [ms]: fold it into what that step usually takes. Skips are not finishes. */
    fun timeStep(step: String, ms: Long) {
        val next = blendStep(typical.value[step], ms) ?: return
        typical.value = typical.value + (step to next)
        stepPrefs.edit().putLong(step, next).apply()
    }

    /** From MainActivity: a reminder's Start was tapped. */
    fun handle(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_ROUTINE) ?: return
        startRequested = intent.getBooleanExtra(EXTRA_START, false)
        intent.removeExtra(EXTRA_ROUTINE)
        app.getSystemService(NotificationManager::class.java).cancel(notifId(id))
        requested.value = id
    }

    /**
     * Alarm fired: "☀️ Morning routine · 4 steps, first: Take meds" with Start and Open, for each routine
     * due and not done today; 30 min later, "Still to do" if it isn't done. Start runs the routine's timer
     * (and asks first when something else is running), Open just takes you to its first undone step.
     */
    fun remind() {
        val at = prefs.getLong("nextAt", 0)
        arm()
        val nudges = routineNudgeDue(all.value, at).map { it.id }.toSet()
        (routineDue(all.value, at) + routineNudgeDue(all.value, at)).forEach { r ->
            fun go(start: Boolean) = PendingIntent.getActivity(
                app, notifId(r.id) + if (start) 0x1000 else 0, // different request codes, or the two share one intent
                Intent(app, MainActivity::class.java).putExtra(EXTRA_ROUTINE, r.id).putExtra(EXTRA_START, start)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val open = go(false)
            val first = when (val f = routineSteps(r).firstOrNull()) {
                is HabitStep -> Habits.all.value.firstOrNull { it.id == f.habitId }?.title?.ifBlank { null } ?: "a habit"
                is TextStep -> f.text
                is ActivityStep -> activity(f.activityId).name
                null -> ""
            }
            val text = "${r.steps.size} steps · first: $first"
            app.getSystemService(NotificationManager::class.java).notify(
                notifId(r.id),
                Notification.Builder(app, CH).setSmallIcon(R.drawable.ic_gong).setContentTitle(if (r.id in nudges) "Still to do: ${r.emoji} ${r.name}" else "${r.emoji} ${r.name} routine")
                    .setContentText(text).setContentIntent(open).setAutoCancel(true)
                    .addAction(Notification.Action.Builder(null, "Start", go(true)).build())
                    .addAction(Notification.Action.Builder(null, "Open", open).build()).build(),
            )
        }
    }

    private fun notifId(id: String) = NOTIF_BASE + (id.hashCode() and 0xFFF)

    private fun arm() {
        val pi = PendingIntent.getBroadcast(app, 7, Intent(app, Receiver::class.java).setAction(Receiver.ROUTINE_REMIND), PendingIntent.FLAG_IMMUTABLE)
        val at = nextRoutineAt(all.value, System.currentTimeMillis())
        prefs.edit().putLong("nextAt", at ?: 0).apply()
        val am = app.getSystemService(AlarmManager::class.java)
        if (at != null) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.cancel(pi)
    }

    /** Finished just now: today gets this minute (tells you later that you already did it); its reminder goes away. */
    fun finish(id: String) {
        val now = LocalTime.now()
        update(id) { it.copy(done = it.done + (LocalDate.now() to now.hour * 60 + now.minute)) }
        app.getSystemService(NotificationManager::class.java).cancel(notifId(id))
    }

    /** New routine when [r]'s id is unknown, otherwise an edit of it. */
    fun save(r: Routine) = replaceAll(mergeRoutines(all.value, listOf(r.copy(updatedAt = System.currentTimeMillis()))))

    /** Puts habit [habitId] at the end of routine [id]; the routine's time is now when you do it. */
    fun addHabit(id: String, habitId: String) = update(id) { it.copy(steps = it.steps + habitStep(habitId)) }

    /** Takes habit [habitId] out of routine [id] (the habit is ended separately). */
    fun removeHabit(id: String, habitId: String) = update(id) { r -> r.copy(steps = r.steps.filterNot { it == habitStep(habitId) }) }

    fun delete(id: String) = update(id) { it.copy(deleted = true) }

    private fun update(id: String, change: (Routine) -> Routine) =
        replaceAll(all.value.map { if (it.id == id) change(it).copy(updatedAt = System.currentTimeMillis()) else it })

    /** After a local change (then synced), or merging with the Drive copy (sync = false). */
    fun replaceAll(list: List<Routine>, sync: Boolean = true) {
        prefs.edit().putString("items", encodeRoutines(list)).apply()
        all.value = list
        // Each routine is also an activity, so its timer runs on the quick-timer engine and History labels it.
        routineActivities = list.filterNot { it.deleted }.map { it.toActivity() }
        arm()
        if (sync) Drive.sync()
    }
}
