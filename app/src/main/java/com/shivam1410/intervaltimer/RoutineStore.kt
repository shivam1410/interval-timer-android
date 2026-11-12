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
 * soonest, a notification per due routine whose Start opens it ([requested], read by Home).
 */
object Routines {
    private const val CH = "routine"
    private const val NOTIF_BASE = 6000
    const val EXTRA_ROUTINE = "routine"
    /** A routine to open, set by a reminder's Start (MainActivity) and cleared by Home once shown. */
    val requested = MutableStateFlow<String?>(null)
    val all = MutableStateFlow<List<Routine>>(emptyList())
    val visible get() = all.value.filterNot { it.deleted }
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("routines", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        val stored = prefs.getString("items", null)
        all.value = if (stored == null) DEFAULT_ROUTINES else decodeRoutines(stored)
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "Routine reminder", NotificationManager.IMPORTANCE_HIGH))
        arm() // boot and updates start the process, which lands here
    }

    /** From MainActivity: a reminder's Start was tapped. */
    fun handle(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_ROUTINE) ?: return
        intent.removeExtra(EXTRA_ROUTINE)
        app.getSystemService(NotificationManager::class.java).cancel(notifId(id))
        requested.value = id
    }

    /** Alarm fired: "☀️ Morning routine · 4 steps, first: Take meds" with Start, for each routine due and not done today. */
    fun remind() {
        val at = prefs.getLong("nextAt", 0)
        arm()
        routineDue(all.value, at).forEach { r ->
            val open = PendingIntent.getActivity(
                app, notifId(r.id), Intent(app, MainActivity::class.java).putExtra(EXTRA_ROUTINE, r.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = "${r.steps.size} steps · first: ${r.steps.firstOrNull().orEmpty()}"
            app.getSystemService(NotificationManager::class.java).notify(
                notifId(r.id),
                Notification.Builder(app, CH).setSmallIcon(R.drawable.ic_gong).setContentTitle("${r.emoji} ${r.name} routine")
                    .setContentText(text).setContentIntent(open).setAutoCancel(true)
                    .addAction(Notification.Action.Builder(null, "Start", open).build()).build(),
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

    fun delete(id: String) = update(id) { it.copy(deleted = true) }

    private fun update(id: String, change: (Routine) -> Routine) =
        replaceAll(all.value.map { if (it.id == id) change(it).copy(updatedAt = System.currentTimeMillis()) else it })

    /** After a local change (then synced), or merging with the Drive copy (sync = false). */
    fun replaceAll(list: List<Routine>, sync: Boolean = true) {
        prefs.edit().putString("items", encodeRoutines(list)).apply()
        all.value = list
        arm()
        if (sync) Drive.sync()
    }
}
