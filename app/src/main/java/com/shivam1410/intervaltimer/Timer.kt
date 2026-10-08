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

enum class Cue(val sound: String) { BREAK("gong"), WORK("gong_double"), DONE("gong_long"), SOFT("bell"), OVER("gong") }

/**
 * Side-effect boundary: persists the session, arms the next AlarmManager wake-up,
 * posts the notification and drives the sound service. Nothing ticks in the background.
 */
object Timer {
    private const val TAG = "Timer"
    const val NOTIF_ID = 1
    private const val ALERT_ID = 2
    private const val CH_STATUS = "status"
    // "phase" replaced "alert": channel settings are frozen once created, and this one needs a (silent) sound.
    private const val CH_ALERT = "phase"

    val session = MutableStateFlow(Session())
    val settings = MutableStateFlow(Settings())
    val plan get() = session.value.quick?.let(::quickPlan) ?: plan(settings.value)

    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("state", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        settings.value = loadSettings()
        session.value = prefs.run {
            Session(getInt("index", -1), getLong("endsAt", 0), getLong("pausedLeft", 0), getLong("startedAt", 0), getLong("finishedAt", 0),
                getLong("phaseMs", 0), getLong("bonus", 0),
                getLong("workMs", 0), getLong("breakMs", 0), getInt("cycles_done", 0), getString("quick", null))
        }
        if (!session.value.running(plan)) resetFixedActivity()
        val nm = app.getSystemService(NotificationManager::class.java)
        // The gong and vibration are played by SoundService; channels add no audible sound of their own.
        // Status stays visible (not "Silent"); alerts pop up once per phase change.
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Current phase", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        // A channel with no sound makes Android treat phase alerts as silent, and silent alerts never
        // launch their full-screen (lock-screen) page. Half a second of silence keeps them "alerting"
        // without adding any noise; the audible gong is still SoundService's job.
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Phase changes", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    android.net.Uri.parse("android.resource://${app.packageName}/${R.raw.silence}"),
                    android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
                )
                enableVibration(false)
                setShowBadge(false)
            }
        )
        nm.deleteNotificationChannel("alert") // old soundless channel from ≤ v1.5.0
    }

    fun saveSettings(s: Settings) {
        settings.value = s
        prefs.edit().apply {
            putInt("workMin", s.workMin); putInt("breakMin", s.breakMin); putInt("prepMin", s.prepMin)
            putInt("cycles", s.cycles); putString("activity", s.activity); putString("focusSound", s.focusSound)
            putBoolean("gong", s.gong); putBoolean("vibrate", s.vibrate); putInt("volume", s.volume)
            putBoolean("custom", s.custom); putBoolean("lockPage", s.lockPage)
        }.apply()
    }

    private fun loadSettings() = Settings().let { d ->
        prefs.run {
            Settings(
                getInt("workMin", d.workMin), getInt("breakMin", d.breakMin), getInt("prepMin", d.prepMin),
                getInt("cycles", d.cycles), (getString("activity", d.activity) ?: d.activity), (getString("focusSound", d.focusSound) ?: d.focusSound),
                getBoolean("gong", d.gong), getBoolean("vibrate", d.vibrate), getInt("volume", d.volume),
                getBoolean("custom", d.custom), getBoolean("lockPage", d.lockPage),
            )
        }
    }

    private fun now() = System.currentTimeMillis()

    fun start() {
        resetFixedActivity()
        commit(start(plan(settings.value), now()), Cue.WORK)
    }

    /**
     * NSDR / nap / stretch (20 min) are chosen for one break only; every new break starts back on a
     * regular activity, so the workday's planned end time isn't stretched by the last pick.
     */
    private fun resetFixedActivity() {
        if (activity(settings.value.activity).minutes != null) saveSettings(settings.value.copy(activity = "breathing"))
    }

    /** One-off activity timer from home (NSDR, stretch, walk…): soft bell now, long gong at the end. */
    fun startQuick(id: String) = commit(start(quickPlan(id), now()).copy(quick = id), Cue.SOFT)
    fun pause() = commit(pause(session.value, now()), null)
    fun resume() = commit(resume(session.value, now()), null)
    fun skip() {
        val s = session.value
        val next = skip(plan, s, now())
        commit(next, cueFor(s, next))
    }
    /** Ends the day early; whatever was done so far still goes into history. */
    fun stop() {
        val s = session.value
        if (s.running(plan)) History.record(s.startedAt, credit(plan, s, now()))
        commit(Session(), null)
    }

    /** In-session choices: switch the break activity or focus music right now, or lengthen this break. */
    fun chooseActivity(id: String) {
        val s = session.value
        val before = plan
        saveSettings(settings.value.copy(activity = id))
        // NSDR / power nap are 20 min: if the activity is already running, stretch or shrink it now.
        val after = s.takeIf { it.running(before) && before[it.index].kind == Kind.ACTIVITY }?.let { resize(it, plan[it.index].ms - before[it.index].ms) } ?: s
        if (after.running(plan)) commit(after, null)
    }
    fun chooseFocus(id: String) = choose(settings.value.copy(focusSound = id))
    /** +10 min: lengthens a break, or restarts a 10-min countdown from overtime (work or break). */
    fun extendBreak() = commit(extend(plan, session.value, 10 * MIN, now()), null)

    private fun choose(s: Settings) {
        saveSettings(s)
        if (session.value.running(plan)) commit(session.value, null)
    }

    private val chosen get() = activity(session.value.quick ?: settings.value.activity)

    /** Alarm fired, device booted, or app opened: settle into whatever phase "now" is. */
    fun sync(playCue: Boolean) {
        val before = session.value
        if (!before.running(plan)) return
        val t = now()
        var after = catchUp(plan, before, t)
        var cue = cueFor(before, after)
        // Reaching 0 on work or a break activity doesn't advance: one "time's up" gong + alert, then it counts up.
        if (overtimeMs(plan, after, t) != null && !after.overAlerted) {
            after = after.copy(overAlerted = true)
            cue = Cue.OVER
        }
        // Nothing changed: skip (the UI's per-second check lands here during overtime). Boot/update
        // (playCue = false) still re-commits, because the system dropped our alarm.
        if (after == before && playCue) return
        commit(after, if (playCue) cue else null, restartSound = playCue)
    }

    private fun cueFor(before: Session, after: Session): Cue? = when {
        after.index == before.index -> null
        after.done(plan) -> Cue.DONE
        plan[after.index].kind == Kind.WORK -> Cue.WORK
        plan[after.index].kind == Kind.PREP || plan[before.index].kind == Kind.WORK -> Cue.BREAK
        else -> Cue.SOFT // prep -> activity
    }

    private fun commit(s: Session, cue: Cue?, restartSound: Boolean = true) {
        if (cue == Cue.WORK && s.quick == null) resetFixedActivity()
        // Alerts (heads-up + lock-screen page) are for phase changes, not for the moment you press Start.
        val transition = session.value.running(plan)
        if (s.done(plan) && session.value.running(plan)) History.record(s.startedAt, s)
        session.value = s
        prefs.edit().putInt("index", s.index).putLong("endsAt", s.endsAt).putLong("pausedLeft", s.pausedLeft)
            .putLong("startedAt", s.startedAt).putLong("finishedAt", s.finishedAt)
            .putLong("phaseMs", s.phaseMs).putLong("bonus", s.bonus)
            .putLong("workMs", s.workMs).putLong("breakMs", s.breakMs).putInt("cycles_done", s.cycles)
            .putString("quick", s.quick).apply()

        val am = app.getSystemService(AlarmManager::class.java)
        val alarm = PendingIntent.getBroadcast(app, 0, Intent(app, Receiver::class.java).setAction(Receiver.ALARM), PendingIntent.FLAG_IMMUTABLE)
        am.cancel(alarm)
        // No alarm once in overtime: nothing happens until you press Next or +10.
        if (s.running(plan) && !s.paused && s.endsAt > now()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endsAt, alarm)

        val nm = app.getSystemService(NotificationManager::class.java)
        nm.cancel(ALERT_ID)
        if (s.idle) nm.cancel(NOTIF_ID) else nm.notify(NOTIF_ID, notification())
        // A fresh post each time: Android only launches the lock-screen page for new notifications.
        if (cue != null && transition) nm.notify(ALERT_ID, alert(cue, s))

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
            Kind.ACTIVITY -> chosen.sound
            Kind.PREP -> null
        }
    }

    fun title(s: Session): String {
        val pl = plan
        if (s.done(pl)) return if (s.quick != null) "${activity(s.quick).name} complete" else "Workday complete"
        val p = pl[s.index]
        val head = when (p.kind) {
            Kind.WORK -> "Work · cycle ${p.cycle}/${settings.value.cycles}"
            Kind.PREP -> "Break · prepare"
            Kind.ACTIVITY -> "${chosen.emoji} ${chosen.name}"
        }
        return when {
            s.paused -> "Paused · $head"
            overtimeMs(pl, s, now()) != null -> "$head · time's up"
            else -> head
        }
    }

    fun subtitle(s: Session, now: Long): String {
        val pl = plan
        if (s.done(pl) && s.quick != null) return "${fmtDur(s.breakMs)} · nicely done"
        if (s.done(pl)) return "${fmtDur(s.workMs)} focused · ${s.cycles} ${if (s.cycles == 1) "cycle" else "cycles"}"
        val p = pl[s.index]
        if (overtimeMs(pl, s, now) != null) return "Still going? Tap Next when you're done, or +10 min."
        return when (p.kind) {
            Kind.PREP -> "Lie down. Put your phone aside. Close your eyes."
            Kind.ACTIVITY -> chosen.hint
            Kind.WORK -> {
                val next = pl.getOrNull(s.index + 1) ?: return "Last block of the day"
                val breakMs = pl.drop(s.index + 1).takeWhile { it.kind != Kind.WORK }.sumOf { it.ms }
                val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.endsAt))
                "Next: Break · ${fmtDur(breakMs)}" + if (s.paused) "" else " at $at"
            }
        }
    }

    /** Launched by the system over the lock screen (screen off/locked); unlocked users get the heads-up. */
    private val lockScreen get() = PendingIntent.getActivity(
        app, 3, Intent(app, TransitionActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private val openApp get() = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    /** Heads-up shown at a phase change, e.g. "Work session complete · 50m focused → 10m recovery". */
    private fun alert(cue: Cue, s: Session): Notification {
        val pl = plan
        val done = s.done(pl)
        val p = pl.getOrNull(s.index) ?: pl.last()
        val breakMs = pl.drop(s.index).takeWhile { it.kind != Kind.WORK }.sumOf { it.ms }
        val (title, text) = when {
            done -> title(s) to subtitle(s, now())
            else -> when (cue) {
            Cue.SOFT -> "${chosen.name} started" to chosen.hint
            Cue.OVER -> (if (p.kind == Kind.WORK) "Work time's up" else "${chosen.name} is over") to
                "Still going? It's counting up now. Tap Next when you're done, or +10 min."
            Cue.WORK -> "Break complete" to "Work cycle ${p.cycle}/${settings.value.cycles} · ${fmtDur(p.ms)}" +
                if (s.paused) " · tap Start when ready" else " started"
            else -> "Work session complete" to "${fmtDur(settings.value.workMin * MIN)} focused → ${fmtDur(breakMs)} recovery. " +
                "Lie down and get comfortable. Open to choose your recovery."
            }
        }
        return Notification.Builder(app, CH_ALERT)
            .setSmallIcon(R.drawable.ic_gong)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * MIN)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply { if (settings.value.lockPage) setFullScreenIntent(lockScreen, true) }
            // Own group: otherwise Android auto-groups it with the status notification, marks it SILENT,
            // and a silent alert never shows its heads-up or lock-screen page.
            .setGroup("phase-alert")
            .apply {
                if (done) return@apply // nothing left to skip or extend
                addAction(if (s.paused) action(Receiver.RESUME, "Start") else action(Receiver.SKIP, if (cue == Cue.OVER) "Next" else "Skip"))
                if (cue == Cue.BREAK || cue == Cue.OVER) addAction(action(Receiver.EXTEND, "+10 min"))
            }
            .build()
    }

    fun notification(): Notification {
        val s = session.value
        val pl = plan
        val b = Notification.Builder(app, CH_STATUS)
            .setSmallIcon(R.drawable.ic_gong)
            .setContentTitle(title(s))
            .setContentText(subtitle(s, now()))
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setGroup("phase-status")
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (s.running(pl)) {
            b.setOngoing(true)
            val over = overtimeMs(pl, s, now()) != null
            if (!s.paused) {
                // Counts down to 0, then (overtime) Android counts up from the same moment — still no app ticks.
                b.setUsesChronometer(true).setChronometerCountDown(!over).setWhen(s.endsAt).setShowWhen(true)
                // Live Update: Android shows this as a status-bar chip + top of the lock screen and draws the
                // countdown itself, so the app still never wakes to tick. Ignored where unsupported.
                // No short text: the chip then shows the system-drawn countdown (e.g. "47:12") next to the icon.
                b.extras.putBoolean("android.requestPromotedOngoing", true) // Notification.EXTRA_REQUEST_PROMOTED_ONGOING (API 36.1)
            }
            if (over) {
                b.addAction(action(Receiver.EXTEND, "+10 min"))
                b.addAction(action(Receiver.SKIP, "Next"))
            } else {
                b.addAction(action(if (s.paused) Receiver.RESUME else Receiver.PAUSE, if (s.paused) "Resume" else "Pause"))
                b.addAction(action(Receiver.SKIP, "Skip"))
            }
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
