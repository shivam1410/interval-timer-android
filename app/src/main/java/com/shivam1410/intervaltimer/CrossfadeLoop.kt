package com.shivam1410.intervaltimer

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Loops a recording without an audible seam: shortly before the end, a second player starts from
 * the top and the two are crossfaded, so a recording that tails off (like the ocean waves) never
 * "stops and restarts". Work happens only during the few-second fade; the rest is plain playback.
 */
class CrossfadeLoop(private val make: () -> MediaPlayer?, private val volume: Float) {
    private val handler = Handler(Looper.getMainLooper())
    private var current: MediaPlayer? = null
    private var incoming: MediaPlayer? = null

    fun start(): Boolean {
        val p = make() ?: return false
        current = p
        p.setVolume(volume, volume)
        // Too short to crossfade: fall back to a plain loop.
        if (p.duration < 3 * FADE_MS) p.isLooping = true else scheduleNext(p)
        p.start()
        return true
    }

    private fun scheduleNext(p: MediaPlayer) {
        handler.postDelayed({ beginFade() }, (p.duration - p.currentPosition - FADE_MS).coerceAtLeast(0).toLong())
    }

    private fun beginFade() {
        val out = current ?: return
        val inc = make() ?: return run { out.seekTo(0) ; scheduleNext(out) }
        incoming = inc
        inc.setVolume(0f, 0f)
        inc.start()
        val t0 = SystemClock.uptimeMillis()
        handler.post(object : Runnable {
            override fun run() {
                val (gOut, gIn) = crossfadeGains((SystemClock.uptimeMillis() - t0) / FADE_MS.toFloat())
                out.setVolume(volume * gOut, volume * gOut)
                inc.setVolume(volume * gIn, volume * gIn)
                if (gIn < 1f) {
                    handler.postDelayed(this, STEP_MS)
                } else {
                    out.release()
                    current = inc
                    incoming = null
                    scheduleNext(inc)
                }
            }
        })
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        current?.release()
        incoming?.release()
        current = null
        incoming = null
    }

    companion object {
        private const val FADE_MS = 6_000
        private const val STEP_MS = 50L
    }
}
