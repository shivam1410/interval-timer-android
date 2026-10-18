package com.shivam1410.intervaltimer

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Links shared into the app from YouTube / YouTube Music (Share -> Interval Timer). Opens the
 * "Add to library" dialog prefilled; the title comes from the share itself, then YouTube's oEmbed.
 */
object Share {
    private const val TAG = "Share"

    data class Pending(val url: String, val title: String)

    /** Shown as the library dialog while non-null. */
    val pending = MutableStateFlow<Pending?>(null)

    fun handle(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val url = extractYouTubeUrl(text)
        // Not a YouTube link: still open the dialog, showing what came in, so the error is visible.
        pending.value = Pending(url ?: text.orEmpty().trim().take(300), intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().take(80))
        if (url != null) thread { fetchTitle(url) }
    }

    /** oEmbed needs no key and returns {"title": …}; only fills the title if the share didn't carry one. */
    private fun fetchTitle(url: String) {
        try {
            val c = URL("https://www.youtube.com/oembed?format=json&url=${URLEncoder.encode(url, "UTF-8")}").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            if (c.responseCode !in 200..299) throw IOException("oEmbed HTTP ${c.responseCode}")
            val title = JSONObject(c.inputStream.use { it.readBytes().decodeToString() }).optString("title").take(80)
            pending.value?.let { p -> if (p.url == url && p.title.isBlank() && title.isNotBlank()) pending.value = p.copy(title = title) }
        } catch (e: IOException) {
            Log.w(TAG, "No title for shared link", e)
        } catch (e: JSONException) {
            Log.w(TAG, "Unexpected oEmbed response", e)
        }
    }
}
