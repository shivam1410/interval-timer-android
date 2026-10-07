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
            EXTEND -> Timer.extendBreak()
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Timer.sync(playCue = false)
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
        const val EXTEND = "it.EXTEND"
        const val INSTALL_STATUS = "it.INSTALL_STATUS"
    }
}
