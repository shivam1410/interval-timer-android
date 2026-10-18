package com.shivam1410.intervaltimer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Links shared into the app from YouTube / YouTube Music (Share -> Interval Timer). Opens the
 * "Add to library" dialog prefilled; the dialog looks up a link's title and length itself.
 */
object Share {
    private const val TAG = "Share"

    /** [file] + [id]: a shared MP3/MP4 already copied in as lib-<id>.<ext>; [minutes] from its duration. */
    data class Pending(val url: String, val title: String, val file: String = "", val id: String? = null, val minutes: Int? = null)

    /** Shown as the library dialog while non-null. */
    val pending = MutableStateFlow<Pending?>(null)
    /** "Adding file…" or why it failed; shown as a banner. */
    val status = MutableStateFlow("")

    fun handle(ctx: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        if (type.startsWith("audio/") || type.startsWith("video/")) return importFile(ctx, intent, type)
        if (type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val url = extractYouTubeUrl(text)
        // Not a YouTube link: still open the dialog, showing what came in, so the error is visible.
        pending.value = Pending(url ?: text.orEmpty().trim().take(300), intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().take(80))
    }

    private fun importFile(ctx: Context, intent: Intent, type: String) {
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: return
        status.value = "Adding file…"
        thread {
            val id = java.util.UUID.randomUUID().toString().take(8)
            try {
                val f = LibraryFiles.import(ctx, uri, type, id)
                pending.value = Pending("", f.title, f.name, id, LibraryFiles.durationMs(ctx, f.name)?.let(::minutesFor))
                status.value = ""
            } catch (e: IOException) {
                Log.w(TAG, "Shared file not added", e)
                status.value = "Couldn't add the file: ${e.message}"
            } catch (e: SecurityException) {
                Log.w(TAG, "No access to the shared file", e)
                status.value = "Couldn't read the shared file"
            }
        }
    }
}
