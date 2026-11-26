package com.shivam1410.intervaltimer

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.PowerManager
import android.os.Process
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar
import kotlin.random.Random

/**
 * "Sketch instead?" nudges after a while on Instagram or YouTube (not while a YouTube video or song
 * plays). Uses Usage access for on-screen time and Notification access only to see YouTube's media
 * player. No polling loop: one inexact alarm, set for when the limit could next be reached.
 */
object Nudges {
    private const val TAG = "Nudges"
    private const val CHANNEL = "nudge"
    private const val NOTIF_ID = 4
    val LIMITS = listOf(5, 10, 15, 20, 30)
    val BREATHE = listOf(0, 10, 15, 20, 30, 45, 60)

    data class Config(
        val enabled: Boolean = false,
        val ig: Boolean = true,
        val igMin: Int = 10,
        val yt: Boolean = true,
        val ytMin: Int = 10,
        /** Minutes on one app today past which the nudge becomes "Just breathe"; 0 = never. */
        val breatheMin: Int = 15,
    )

    val config = MutableStateFlow(Config())
    /** Quick timer picked from a nudge's button; Root runs the countdown for it. */
    val requestedQuick = MutableStateFlow<String?>(null)

    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("nudge", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        config.value = prefs.run {
            Config(getBoolean("enabled", false), getBoolean("ig", true), getInt("igMin", 10), getBoolean("yt", true), getInt("ytMin", 10), getInt("breatheMin", 15))
        }
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Instagram / YouTube nudges", NotificationManager.IMPORTANCE_HIGH))
        arm(5 * MIN)
    }

    fun save(c: Config) {
        config.value = c
        prefs.edit().putBoolean("enabled", c.enabled).putBoolean("ig", c.ig).putInt("igMin", c.igMin)
            .putBoolean("yt", c.yt).putInt("ytMin", c.ytMin).putInt("breatheMin", c.breatheMin).apply()
        arm(if (c.enabled) 5_000 else 0)
    }

    fun usageAccess(): Boolean = app.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName) == AppOpsManager.MODE_ALLOWED

    private val listener get() = ComponentName(app, NudgeListener::class.java)

    fun mediaAccess(): Boolean = app.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)

    private fun alarmIntent() =
        PendingIntent.getBroadcast(app, 3, Intent(app, Receiver::class.java).setAction(Receiver.NUDGE_CHECK), PendingIntent.FLAG_IMMUTABLE)

    /**
     * Exact (the app holds USE_EXACT_ALARM as a timer): inexact alarms were delayed by up to 75 % and
     * capped per hour by app-standby quotas once the app sat unused, so nudges after the first stopped coming.
     * Checks stay sparse: every few minutes with the screen on, 15 min off, none during a cooldown.
     */
    private fun arm(inMs: Long) {
        val am = app.getSystemService(AlarmManager::class.java)
        val c = config.value
        if (!c.enabled || !(c.ig || c.yt) || inMs <= 0) return am.cancel(alarmIntent())
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + inMs, alarmIntent())
    }

    /** Alarm fired: look at the last hours of app usage, nudge if due, and set the next check. */
    fun check() {
        val c = config.value
        if (!c.enabled) return
        if (!usageAccess()) return arm(30 * MIN) // granted later: picked up within half an hour
        // Screen off: nobody is scrolling; look again later (Doze would batch it anyway).
        if (!app.getSystemService(PowerManager::class.java).isInteractive) return arm(15 * MIN)
        val now = System.currentTimeMillis()
        val events = events(now)
        val next = listOfNotNull(
            if (c.ig) checkApp(INSTAGRAM, "Instagram", c.igMin * MIN, events, now) else null,
            if (c.yt) checkApp(YOUTUBE, "YouTube", c.ytMin * MIN, events, now) else null,
        ).minOrNull() ?: (5 * MIN)
        arm(next)
    }

    /** Returns when to check this app again. */
    private fun checkApp(pkg: String, name: String, limitMs: Long, events: List<AppEvent>, now: Long): Long {
        val u = usage(events, pkg, now, windowFor(limitMs))
        val watching = pkg == YOUTUBE && u.onScreen && youTubePlaying()
        val breatheMin = config.value.breatheMin
        var last = prefs.getLong("last_$pkg", 0)
        var lastBreathe = prefs.getLong("breathe_$pkg", 0)
        // The breathe ask wins where both are due: once you are past the day's line there is nothing
        // left to offer, and both share one notification slot anyway.
        when {
            shouldBreathe(u, breatheMin, lastBreathe, now, watching) -> {
                lastBreathe = now
                prefs.edit().putLong("breathe_$pkg", now).apply()
                post(name, u.todayMs, breathe = true)
            }
            shouldNudge(u, limitMs, last, now, watching) -> {
                last = now
                prefs.edit().putLong("last_$pkg", now).apply()
                post(name, u.todayMs, breathe = false)
            }
        }
        val nudge = nextCheckAfter(u, limitMs, last, now, Random.nextLong(0, 2 * MIN))
        return nextBreatheMs(u, breatheMin, lastBreathe, now)?.let { minOf(nudge, it) } ?: nudge
    }

    private fun midnight(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun events(now: Long): List<AppEvent> {
        val it = app.getSystemService(UsageStatsManager::class.java).queryEvents(midnight(now), now)
        val e = UsageEvents.Event()
        return buildList {
            while (it.hasNextEvent()) {
                it.getNextEvent(e)
                val watched = e.packageName == INSTAGRAM || e.packageName == YOUTUBE
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> add(AppEvent(if (watched) e.packageName else OTHER_APP, true, e.timeStamp, e.className.orEmpty()))
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                        if (watched) add(AppEvent(e.packageName, false, e.timeStamp, e.className.orEmpty()))
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> add(AppEvent(OTHER_APP, true, e.timeStamp))
                }
            }
        }
    }

    /**
     * Is YouTube playing a video or song (studying, music, NSDR)? With Notification access we read its
     * media session; without it, "any music playing" is the fallback (it can't tell Shorts apart).
     */
    private fun youTubePlaying(): Boolean {
        if (!mediaAccess()) return app.getSystemService(AudioManager::class.java).isMusicActive
        return try {
            app.getSystemService(MediaSessionManager::class.java).getActiveSessions(listener)
                .filter { it.packageName == YOUTUBE }
                .any { mc ->
                    val meta = mc.metadata
                    isWatching(
                        mc.playbackState?.state == PlaybackState.STATE_PLAYING,
                        meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
                        meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
                    )
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access was revoked", e)
            false
        }
    }

    private val LINES = listOf(
        "Sketch instead?" to "Draw today's prompt for 15 minutes.",
        "Relax instead?" to "Five minutes of breathing.",
        "Put it down for a bit?" to "A quick sketch or a few slow breaths.",
    )

    private fun opens(id: String, code: Int) = PendingIntent.getActivity(
        app, code, Intent(app, MainActivity::class.java).putExtra(EXTRA_QUICK, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(name: String, todayMs: Long, breathe: Boolean) {
        val nm = app.getSystemService(NotificationManager::class.java)
        if (breathe) return nm.notify(NOTIF_ID, breathe(name, todayMs))
        val (title, text) = LINES.random()
        fun quick(id: String, label: String, code: Int) = Notification.Action.Builder(null, label, opens(id, code)).build()
        nm.notify(
            NOTIF_ID,
            Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_gong)
                .setContentTitle("${todayMs / MIN} min on $name today · $title")
                .setContentText(text)
                .setAutoCancel(true)
                .addAction(quick("sketch", "✏️ Sketch", 41))
                .addAction(quick("breathing", "🫁 Relax", 42))
                .build(),
        )
    }

    /**
     * The escalation: past [Config.breatheMin] on the app today there is nothing to choose between,
     * only one thing to do. Ongoing, so "Clear all" leaves it and it sits at the top of the shade, and
     * promoted, so Android 16 shows it as a status-bar chip over whatever you are scrolling — as close
     * to the screen as the app can get without the overlay permission payment apps refuse to sit under.
     * It clears itself after the cooldown, so an ignored one is never there forever.
     */
    private fun breathe(name: String, todayMs: Long): Notification =
        Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_gong)
            .setContentTitle("Just breathe")
            .setContentText("${todayMs / MIN} min on $name today. Five minutes, in and out.")
            .setContentIntent(opens("breathing", 43))
            .addAction(Notification.Action.Builder(null, "🫁 Start", opens("breathing", 43)).build())
            .setOngoing(true)
            .setTimeoutAfter(NUDGE_COOLDOWN_MS)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply { extras.putBoolean("android.requestPromotedOngoing", true) } // EXTRA_REQUEST_PROMOTED_ONGOING (API 36.1)
            .build()

    const val EXTRA_QUICK = "nudge_quick"

    /** From MainActivity: a nudge button was tapped. */
    fun handle(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_QUICK) ?: return
        intent.removeExtra(EXTRA_QUICK)
        app.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        if (Timer.session.value.idle) requestedQuick.value = id
    }
}

/** Does nothing with notifications: it exists so Android lets Nudges read YouTube's media session. */
class NudgeListener : NotificationListenerService()
