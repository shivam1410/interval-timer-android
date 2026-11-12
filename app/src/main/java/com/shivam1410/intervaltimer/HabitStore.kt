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
 * Your habit (one at a time), in shared prefs so Android's Google backup carries it. [all] keeps ended
 * habits too; [active] is the one Home shows. Drive sync comes later (habits.json, see the habits spec).
 * Side-effect boundary for its daily reminder: one exact alarm, re-armed on every change and process start.
 */
object Habits {
    private const val NOTIF_ID = 5
    private const val CH_HABIT = "habit"
    const val DEFAULT_REMIND_MIN = 8 * 60
    val all = MutableStateFlow<List<Habit>>(emptyList())
    val active get() = all.value.firstOrNull { !it.ended }
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("habits", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        all.value = decodeHabits(prefs.getString("items", null))
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH_HABIT, "Habit reminder", NotificationManager.IMPORTANCE_HIGH))
        arm() // alarms don't survive a reboot; boot starts the process, which lands here
    }

    /** Starts [itemId] as the habit from today, ending any other one (one habit at a time). */
    fun start(itemId: String, stages: List<Stage>, remindMin: Int? = DEFAULT_REMIND_MIN) {
        val now = System.currentTimeMillis()
        val ended = all.value.map { if (it.ended) it else it.copy(ended = true, updatedAt = now) }
        replaceAll(ended + Habit(UUID.randomUUID().toString().take(8), itemId, LocalDate.now(), stages, remindMin, emptySet(), now))
    }

    /** New stages for the running habit; its start date and done days stay. */
    fun edit(id: String, stages: List<Stage>, remindMin: Int?) = update(id) { it.copy(stages = stages, remindMin = remindMin) }

    fun markDone(id: String, date: LocalDate, on: Boolean) {
        update(id) { it.copy(done = if (on) it.done + date else it.done - date) }
        if (on) app.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    /** The reminder's Done button: today counts for the running habit. */
    fun doneFromReminder() {
        active?.let { markDone(it.id, LocalDate.now(), true) }
    }

    /** Alarm fired: post "Tib raises: 1 × 20 today" with a Done button, unless today is done or the habit is over. */
    fun remind() {
        arm()
        val h = active ?: return
        val text = reminderText(h, LocalDate.now()) ?: return
        val title = Library.items.value.firstOrNull { it.id == h.itemId }?.title ?: "Your habit"
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val done = PendingIntent.getBroadcast(app, 6, Intent(app, Receiver::class.java).setAction(Receiver.HABIT_DONE), PendingIntent.FLAG_IMMUTABLE)
        app.getSystemService(NotificationManager::class.java).notify(
            NOTIF_ID,
            Notification.Builder(app, CH_HABIT)
                .setSmallIcon(R.drawable.ic_gong)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(null, "Done", done).build())
                .build(),
        )
    }

    private fun arm() {
        val pi = PendingIntent.getBroadcast(app, 5, Intent(app, Receiver::class.java).setAction(Receiver.HABIT_REMIND), PendingIntent.FLAG_IMMUTABLE)
        val am = app.getSystemService(AlarmManager::class.java)
        val min = active?.remindMin
        if (min != null) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextDaily(System.currentTimeMillis(), min), pi) else am.cancel(pi)
    }

    fun end(id: String) = update(id) { it.copy(ended = true) }

    private fun update(id: String, change: (Habit) -> Habit) =
        replaceAll(all.value.map { if (it.id == id) change(it).copy(updatedAt = System.currentTimeMillis()) else it })

    fun replaceAll(list: List<Habit>) {
        prefs.edit().putString("items", encodeHabits(list)).apply()
        all.value = list
        arm()
    }
}
