package com.shivam1410.intervaltimer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.DateFormat
import java.util.Date

enum class Cue(val sound: String) { BREAK("gong"), WORK("gong_double"), DONE("gong_long"), SOFT("bell") }

/**
 * Side-effect boundary: persists the session, arms the next AlarmManager wake-up,
 * posts the notification and drives the sound service. Nothing ticks in the background.
 */
object Timer {
    private const val TAG = "Timer"
    const val NOTIF_ID = 1
    private const val CH_SESSION = "session"
    private const val CH_TRANSITION = "transition"

    val session = MutableStateFlow(Session())
    val settings = MutableStateFlow(Settings())
    val plan get() = plan(settings.value)

    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("state", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        settings.value = loadSettings()
        session.value = prefs.run {
            Session(getInt("index", -1), getLong("endsAt", 0), getLong("pausedLeft", 0), getLong("startedAt", 0), getLong("finishedAt", 0))
        }
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SESSION, "Current phase", NotificationManager.IMPORTANCE_LOW))
        // Sound is the gong we play ourselves; the channel only makes the transition pop up.
        nm.createNotificationChannel(
            NotificationChannel(CH_TRANSITION, "Phase changes", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun saveSettings(s: Settings) {
        settings.value = s
        prefs.edit().apply {
            putInt("workMin", s.workMin); putInt("breakMin", s.breakMin); putInt("prepMin", s.prepMin)
            putInt("cycles", s.cycles); putString("activity", s.activity); putInt("longEvery", s.longEvery)
            putInt("longMin", s.longMin); putString("longActivity", s.longActivity); putString("focusSound", s.focusSound)
            putBoolean("gong", s.gong); putBoolean("vibrate", s.vibrate); putInt("volume", s.volume)
            putBoolean("waitBeforeWork", s.waitBeforeWork)
        }.apply()
    }

    private fun loadSettings() = Settings().let { d ->
        prefs.run {
            Settings(
                getInt("workMin", d.workMin), getInt("breakMin", d.breakMin), getInt("prepMin", d.prepMin),
                getInt("cycles", d.cycles), (getString("activity", d.activity) ?: d.activity), getInt("longEvery", d.longEvery),
                getInt("longMin", d.longMin), (getString("longActivity", d.longActivity) ?: d.longActivity), (getString("focusSound", d.focusSound) ?: d.focusSound),
                getBoolean("gong", d.gong), getBoolean("vibrate", d.vibrate), getInt("volume", d.volume),
                getBoolean("waitBeforeWork", d.waitBeforeWork),
            )
        }
    }

    private fun now() = System.currentTimeMillis()

    fun start() = commit(start(plan, now()), Cue.WORK)
    fun pause() = commit(pause(session.value, now()), null)
    fun resume() = commit(resume(session.value, now()), null)
    fun skip() {
        val s = session.value
        val next = skip(plan, s, now())
        commit(next, cueFor(s, next))
    }
    fun stop() = commit(Session(), null)

    /** Alarm fired, device booted, or app opened: settle into whatever phase "now" is. */
    fun sync(playCue: Boolean) {
        val before = session.value
        if (!before.running(plan)) return
        val after = catchUp(plan, before, now(), settings.value.waitBeforeWork)
        commit(after, if (playCue) cueFor(before, after) else null, restartSound = playCue)
    }

    private fun cueFor(before: Session, after: Session): Cue? = when {
        after.index == before.index -> null
        after.done(plan) -> Cue.DONE
        plan[after.index].kind == Kind.WORK -> Cue.WORK
        plan[after.index].kind == Kind.PREP || plan[before.index].kind == Kind.WORK -> Cue.BREAK
        else -> Cue.SOFT // prep -> activity
    }

    private fun commit(s: Session, cue: Cue?, restartSound: Boolean = true) {
        session.value = s
        prefs.edit().putInt("index", s.index).putLong("endsAt", s.endsAt).putLong("pausedLeft", s.pausedLeft)
            .putLong("startedAt", s.startedAt).putLong("finishedAt", s.finishedAt).apply()

        val am = app.getSystemService(AlarmManager::class.java)
        val alarm = PendingIntent.getBroadcast(app, 0, Intent(app, Receiver::class.java).setAction(Receiver.ALARM), PendingIntent.FLAG_IMMUTABLE)
        am.cancel(alarm)
        if (s.running(plan) && !s.paused) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endsAt, alarm)

        val nm = app.getSystemService(NotificationManager::class.java)
        if (s.idle) nm.cancel(NOTIF_ID) else nm.notify(NOTIF_ID, notification(cue != null))

        if (!restartSound) return
        val music = musicFor(s)
        try {
            if (cue != null || music != null) {
                app.startForegroundService(
                    Intent(app, SoundService::class.java).putExtra("cue", cue?.sound).putExtra("music", music)
                )
            } else {
                app.stopService(Intent(app, SoundService::class.java))
            }
        } catch (e: IllegalStateException) {
            // e.g. BOOT_COMPLETED may not start a media FGS on Android 15+; the notification still updated.
            Log.w(TAG, "Sound service not started", e)
        }
    }

    fun musicFor(s: Session): String? {
        if (!s.running(plan) || s.paused) return null
        val p = plan[s.index]
        return when (p.kind) {
            Kind.WORK -> settings.value.focusSound.ifEmpty { null }
            Kind.ACTIVITY -> p.activity?.sound
            Kind.PREP -> null
        }
    }

    fun title(s: Session): String {
        val pl = plan
        if (s.done(pl)) return "Workday complete"
        val p = pl[s.index]
        val head = when (p.kind) {
            Kind.WORK -> "Work · cycle ${p.cycle}/${settings.value.cycles}"
            Kind.PREP -> if (p.long) "Long break · prepare" else "Break · prepare"
            Kind.ACTIVITY -> p.activity?.let { "${it.emoji} ${it.name}" } ?: "Break"
        }
        return if (s.paused) "Paused · $head" else head
    }

    fun subtitle(s: Session, now: Long): String {
        val pl = plan
        if (s.done(pl)) return "${fmtDur(totals(pl, s, now).first)} focused · ${settings.value.cycles} cycles"
        val p = pl[s.index]
        return when (p.kind) {
            Kind.PREP -> "Lie down. Put your phone aside. Close your eyes."
            Kind.ACTIVITY -> p.activity?.hint.orEmpty()
            Kind.WORK -> {
                val next = pl.getOrNull(s.index + 1) ?: return "Last block of the day"
                val breakMs = pl.drop(s.index + 1).takeWhile { it.kind != Kind.WORK }.sumOf { it.ms }
                val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.endsAt))
                "Next: ${if (next.long) "long break" else "break"} · ${fmtDur(breakMs)}" + if (s.paused) "" else " at $at"
            }
        }
    }

    fun notification(alert: Boolean): Notification {
        val s = session.value
        val pl = plan
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(app, if (alert) CH_TRANSITION else CH_SESSION)
            .setSmallIcon(R.drawable.ic_bowl)
            .setContentTitle(title(s))
            .setContentText(subtitle(s, now()))
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (s.running(pl)) {
            b.setOngoing(true)
            if (!s.paused) b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(s.endsAt).setShowWhen(true)
            b.addAction(action(if (s.paused) Receiver.RESUME else Receiver.PAUSE, if (s.paused) "Resume" else "Pause"))
            b.addAction(action(Receiver.SKIP, "Skip"))
        } else {
            b.setAutoCancel(true)
        }
        return b.build()
    }

    private fun action(act: String, label: String) = Notification.Action.Builder(
        null, label,
        PendingIntent.getBroadcast(app, act.hashCode(), Intent(app, Receiver::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE)
    ).build()
}
