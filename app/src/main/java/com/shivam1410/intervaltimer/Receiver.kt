package com.shivam1410.intervaltimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/** Alarm wake-ups, notification buttons, boot/update restore and installer callbacks. */
class Receiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ALARM -> Timer.sync(playCue = true)
            PAUSE -> Timer.pause()
            RESUME -> Timer.resume()
            SKIP -> Timer.skip()
            END -> Timer.stop()
            EXTEND -> Timer.extendBreak()
            REMINDER -> Timer.remind()
            START_DAY -> Timer.startWorkday()
            START_AT -> Timer.startScheduled()
            NUDGE_CHECK -> Nudges.check()
            AMBIENT_OFF -> Timer.playAmbient(null)
            // Android doesn't let music start from boot; after an update it may, so the session's music resumes.
            Intent.ACTION_BOOT_COMPLETED -> Timer.sync(playCue = false)
            Intent.ACTION_MY_PACKAGE_REPLACED -> Timer.sync(playCue = false, resumeSound = true)
            INSTALL_STATUS -> onInstallStatus(ctx, intent)
        }
    }

    private fun onInstallStatus(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> Log.i("Updater", "Update installed")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e("Updater", "Install failed ($status): $msg")
                Updater.error.value = "Update failed: $msg"
            }
        }
    }

    companion object {
        const val ALARM = "it.ALARM"
        const val PAUSE = "it.PAUSE"
        const val RESUME = "it.RESUME"
        const val SKIP = "it.SKIP"
        const val END = "it.END"
        const val EXTEND = "it.EXTEND"
        const val REMINDER = "it.REMINDER"
        const val START_DAY = "it.START_DAY"
        const val START_AT = "it.START_AT"
        const val NUDGE_CHECK = "it.NUDGE_CHECK"
        const val AMBIENT_OFF = "it.AMBIENT_OFF"
        const val INSTALL_STATUS = "it.INSTALL_STATUS"
    }
}
