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
import java.util.UUID

/**
 * Your habits, several at once, in shared prefs so Android's Google backup carries them. [all] keeps
 * ended habits too; [running] are the ones Home shows. Synced to Drive as habits.json (Drive.kt).
 * Side-effect boundary for their reminders: one exact alarm at the soonest reminder, re-armed on every
 * change, every firing and process start; each due habit gets its own notification.
 */
object Habits {
    private const val NOTIF_BASE = 5000
    private const val CH_HABIT = "habit"
    const val DEFAULT_REMIND_MIN = 8 * 60
    const val EXTRA_HABIT = "habit"
    val all = MutableStateFlow<List<Habit>>(emptyList())
    val running get() = all.value.filter { !it.ended }
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("habits", Context.MODE_PRIVATE)
    // When each habit was finished, by day: a store of its own, local to this phone and never synced,
    // so the habits themselves keep the exact shape older builds and Drive already agree on.
    private val timePrefs get() = app.getSharedPreferences("habitTimes", Context.MODE_PRIVATE)
    val times = MutableStateFlow<Map<LocalDate, Map<String, Int>>>(emptyMap())

    fun init(ctx: Context) {
        app = ctx.applicationContext
        all.value = decodeHabits(prefs.getString("items", null))
        times.value = timePrefs.all.mapNotNull { (k, v) ->
            runCatching { LocalDate.parse(k) }.getOrNull()?.let { it to decodeTimes(v as? String) }
        }.toMap()
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH_HABIT, "Habit reminder", NotificationManager.IMPORTANCE_HIGH))
        arm() // alarms don't survive a reboot; boot starts the process, which lands here
    }

    /** Starts a habit from today ([title], or linked to library exercise [itemId]); others keep running. */
    fun start(itemId: String, stages: List<Stage>, remindMin: Int? = DEFAULT_REMIND_MIN, title: String = ""): String {
        val h = Habit(UUID.randomUUID().toString().take(8), itemId, LocalDate.now(), stages, remindMin, emptySet(), System.currentTimeMillis(), title = title)
        replaceAll(all.value + h)
        return h.id
    }

    /** A finished habit's Repeat: the same plan again from today; the finished one is ended. */
    fun repeat(id: String) {
        val old = all.value.firstOrNull { it.id == id } ?: return
        end(id)
        start(old.itemId, old.stages, old.remindMin, old.title)
    }

    /** New stages and reminder for a habit; its start date and done days stay. */
    fun edit(id: String, stages: List<Stage>, remindMin: Int?) = update(id) { it.copy(stages = stages, remindMin = remindMin) }

    fun markDone(id: String, date: LocalDate, on: Boolean) {
        update(id) { it.copy(done = if (on) it.done + date else it.done - date) }
        // Ticked today, note the minute — a day marked off in the calendar weeks later has no minute to
        // note, and the day's record says "done" for those rather than inventing one.
        val day = (times.value[date].orEmpty() - id).let { rest ->
            if (on && date == LocalDate.now()) rest + (id to java.time.LocalTime.now().let { it.hour * 60 + it.minute }) else rest
        }
        times.value = if (day.isEmpty()) times.value - date else times.value + (date to day)
        // Untick the last habit of a day and the day goes with it, rather than leaving an empty entry
        // behind for every date ever touched.
        timePrefs.edit().apply { if (day.isEmpty()) remove(date.toString()) else putString(date.toString(), encodeTimes(day)) }.apply()
        if (on) app.getSystemService(NotificationManager::class.java).cancel(notifId(id))
    }

    /** A reminder's Completed button: today counts for that habit. */
    fun doneFromReminder(id: String?) {
        id?.let { markDone(it, LocalDate.now(), true) }
    }

    /** Alarm fired: one notification per habit due now ("Tib raises: 1 × 20 today"), skipping done or finished ones and ones in a routine. */
    fun remind() {
        val at = prefs.getLong("nextAt", 0)
        arm()
        val today = LocalDate.now()
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val inRoutine = habitsInRoutines(Routines.all.value) // their routine's reminder covers them
        dueAt(all.value, at).filterNot { it.id in inRoutine }.forEach { h ->
            val text = reminderText(h, today) ?: return@forEach
            val done = PendingIntent.getBroadcast(
                app, notifId(h.id), Intent(app, Receiver::class.java).setAction(Receiver.HABIT_DONE).putExtra(EXTRA_HABIT, h.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            app.getSystemService(NotificationManager::class.java).notify(
                notifId(h.id),
                Notification.Builder(app, CH_HABIT)
                    .setSmallIcon(R.drawable.ic_gong)
                    .setContentTitle(habitTitle(h, Library.items.value))
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .addAction(Notification.Action.Builder(null, "Completed", done).build())
                    .build(),
            )
        }
    }

    /** Each habit's own notification id (and Completed request code), stable across restarts. */
    private fun notifId(id: String) = NOTIF_BASE + (id.hashCode() and 0xFFF)

    private fun arm() {
        val pi = PendingIntent.getBroadcast(app, 5, Intent(app, Receiver::class.java).setAction(Receiver.HABIT_REMIND), PendingIntent.FLAG_IMMUTABLE)
        val am = app.getSystemService(AlarmManager::class.java)
        val at = nextReminderAt(all.value, System.currentTimeMillis())
        prefs.edit().putLong("nextAt", at ?: 0).apply()
        if (at != null) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.cancel(pi)
    }

    fun end(id: String) = update(id) { it.copy(ended = true) }

    private fun update(id: String, change: (Habit) -> Habit) =
        replaceAll(all.value.map { if (it.id == id) change(it).copy(updatedAt = System.currentTimeMillis()) else it })

    /** After a local change (then synced) or merging with the Drive copy (sync = false). */
    fun replaceAll(list: List<Habit>, sync: Boolean = true) {
        prefs.edit().putString("items", encodeHabits(list)).apply()
        all.value = list
        arm()
        if (sync) Drive.sync()
    }
}
