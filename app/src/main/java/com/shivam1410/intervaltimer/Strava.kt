package com.shivam1410.intervaltimer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * Starts a GPS recording in the Strava app when a quick timer begins, so a walk gets its map,
 * distance and pace without touching Strava first.
 *
 * Strava's v3 API can't start a recording (it only creates finished activities), so this fires the
 * app's own undocumented recording intent — the one its NFC tags use. Stopping isn't possible the
 * same way: strava.com/nfc/stop and /nfc/toggle only open the record screen and leave the recording
 * running, so the walk is paused and saved in Strava by hand.
 */
object Strava {
    private const val TAG = "Strava"
    private const val RECORD = "http://strava.com/nfc/record"

    /** Quick timers that also record in Strava, and the sport Strava records them as. */
    private val SPORTS = mapOf("walk" to "Walk")

    fun sportFor(quickId: String): String? = SPORTS[quickId]

    fun record(ctx: Context, quickId: String) {
        val sport = sportFor(quickId) ?: return
        val intent = Intent(Intent.ACTION_RUN, Uri.parse(RECORD))
            .putExtra("rideType", sport)
            // NEW_TASK | CLEAR_TOP: without them, a Strava that is already open is merely brought to
            // the front ("Activity not started, its current task has been brought to the front") and
            // no recording starts.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Nothing on this phone takes Strava's recording intent", e)
            Toast.makeText(ctx, "Strava isn't installed", Toast.LENGTH_SHORT).show()
        }
    }
}
