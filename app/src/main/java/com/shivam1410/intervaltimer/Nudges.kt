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

    data class Config(val enabled: Boolean = false, val ig: Boolean = true, val igMin: Int = 10, val yt: Boolean = true, val ytMin: Int = 10)

    val config = MutableStateFlow(Config())
    /** Quick timer picked from a nudge's button; Root runs the countdown for it. */
    val requestedQuick = MutableStateFlow<String?>(null)

    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("nudge", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        config.value = prefs.run { Config(getBoolean("enabled", false), getBoolean("ig", true), getInt("igMin", 10), getBoolean("yt", true), getInt("ytMin", 10)) }
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Instagram / YouTube nudges", NotificationManager.IMPORTANCE_HIGH))
        arm(5 * MIN)
    }

    fun save(c: Config) {
        config.value = c
        prefs.edit().putBoolean("enabled", c.enabled).putBoolean("ig", c.ig).putInt("igMin", c.igMin)
            .putBoolean("yt", c.yt).putInt("ytMin", c.ytMin).apply()
        arm(if (c.enabled) 5_000 else 0)
    }

    fun usageAccess(): Boolean = app.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName) == AppOpsManager.MODE_ALLOWED

    private val listener get() = ComponentName(app, NudgeListener::class.java)

    fun mediaAccess(): Boolean = app.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)

    private fun alarmIntent() =
        PendingIntent.getBroadcast(app, 3, Intent(app, Receiver::class.java).setAction(Receiver.NUDGE_CHECK), PendingIntent.FLAG_IMMUTABLE)

    /** Inexact on purpose: Android may batch it with other wake-ups; a minute either way doesn't matter here. */
    private fun arm(inMs: Long) {
        val am = app.getSystemService(AlarmManager::class.java)
        val c = config.value
        if (!c.enabled || !(c.ig || c.yt) || inMs <= 0) return am.cancel(alarmIntent())
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + inMs, alarmIntent())
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
        val u = usage(events, pkg, now, NUDGE_GAP_MS)
        val watching = pkg == YOUTUBE && u.onScreen && youTubePlaying()
        if (shouldNudge(u, limitMs, prefs.getLong("last_$pkg", 0), now, watching)) {
            prefs.edit().putLong("last_$pkg", now).apply()
            post(name, u.todayMs)
        }
        return nextCheckMs(u, limitMs, Random.nextLong(0, 2 * MIN))
    }

    private fun events(now: Long): List<AppEvent> {
        val midnight = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val it = app.getSystemService(UsageStatsManager::class.java).queryEvents(midnight, now)
        val e = UsageEvents.Event()
        return buildList {
            while (it.hasNextEvent()) {
                it.getNextEvent(e)
                val watched = e.packageName == INSTAGRAM || e.packageName == YOUTUBE
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> add(AppEvent(if (watched) e.packageName else OTHER_APP, true, e.timeStamp))
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                        if (watched) add(AppEvent(e.packageName, false, e.timeStamp))
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

    private fun post(name: String, todayMs: Long) {
        val (title, text) = LINES.random()
        fun quick(id: String, label: String, code: Int) = Notification.Action.Builder(
            null, label,
            PendingIntent.getActivity(
                app, code, Intent(app, MainActivity::class.java).putExtra(EXTRA_QUICK, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()
        app.getSystemService(NotificationManager::class.java).notify(
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
