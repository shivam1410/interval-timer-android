package com.shivam1410.intervaltimer

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import java.io.IOException

/**
 * Lives only while sound is playing: a transition gong, then optional looped focus/ambient audio.
 * With nothing to play it detaches its notification and stops, so idle phases cost nothing.
 */
class SoundService : Service() {
    private var cue: MediaPlayer? = null
    private var music: MediaPlayer? = null
    private var musicId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Timer.NOTIF_ID, Timer.notification(false), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        val s = Timer.settings.value
        val cueId = intent?.getStringExtra("cue")
        val wanted = intent?.getStringExtra("music")
        if (wanted != musicId) stopMusic()
        musicId = wanted

        if (cueId != null && s.vibrate) vibrate(cueId)
        if (cueId != null && s.gong) playCue(cueId, s.volume / 100f) else startMusicOrStop()
        return START_NOT_STICKY
    }

    private fun playCue(id: String, volume: Float) {
        cue?.release()
        cue = player(AudioAttributes.USAGE_ALARM, volume, loop = false, id)?.apply {
            setOnCompletionListener { startMusicOrStop() }
            start()
        } ?: run { startMusicOrStop(); null }
    }

    private fun startMusicOrStop() {
        val id = musicId
        if (id != null && music == null) {
            music = player(AudioAttributes.USAGE_MEDIA, Timer.settings.value.volume / 200f, loop = true, id)?.apply { start() }
        }
        if (music == null && cue?.isPlaying != true) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun player(usage: Int, volume: Float, loop: Boolean, id: String): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            val f = Media.file(id)
            when {
                f != null -> setDataSource(f.path)
                loop -> return null // no ambient fallback; it simply stays quiet until downloaded
                else -> setDataSource(this@SoundService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            setVolume(volume, volume)
            isLooping = loop
            setWakeMode(this@SoundService, PowerManager.PARTIAL_WAKE_LOCK)
            prepare()
        }
    } catch (e: IOException) {
        Log.e("SoundService", "Cannot play $id", e)
        null
    }

    private fun vibrate(cueId: String) {
        val pulses = when (cueId) { "gong" -> 1; "gong_double" -> 2; "gong_long" -> 3; else -> 0 }
        val timings = if (pulses == 0) longArrayOf(0, 120) else LongArray(pulses * 2) { if (it % 2 == 0) 250L else 450L }
        getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    private fun stopMusic() {
        music?.release()
        music = null
    }

    override fun onDestroy() {
        cue?.release()
        stopMusic()
    }
}
