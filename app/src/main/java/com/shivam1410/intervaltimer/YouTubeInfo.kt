package com.shivam1410.intervaltimer

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Title and length of a YouTube / YouTube Music video, without an API key. Blocking: call off the main thread. */
object YouTubeInfo {
    private const val TAG = "YouTubeInfo"

    /** oEmbed: a stable, documented endpoint returning {"title": …}. */
    fun title(url: String): String? = try {
        val c = open("https://www.youtube.com/oembed?format=json&url=${URLEncoder.encode(url, "UTF-8")}")
        JSONObject(c.inputStream.use { it.readBytes().decodeToString() }).optString("title").take(80).ifBlank { null }
    } catch (e: IOException) {
        Log.w(TAG, "No title", e)
        null
    } catch (e: JSONException) {
        Log.w(TAG, "Unexpected oEmbed response", e)
        null
    }

    /**
     * oEmbed has no duration, so this reads "lengthSeconds" from the public watch page.
     * ponytail: depends on YouTube's page markup; on failure the form keeps its manual length.
     */
    fun lengthSeconds(url: String): Int? {
        val id = youTubeId(url) ?: return null
        return try {
            val c = open("https://www.youtube.com/watch?v=$id")
            // The value sits early in the page; stop reading after 2 MB.
            val html = c.inputStream.use { it.readNBytes(2 * 1024 * 1024).decodeToString() }
            parseLengthSeconds(html)
        } catch (e: IOException) {
            Log.w(TAG, "No length for $id", e)
            null
        }
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) IntervalTimer")
        c.setRequestProperty("Accept-Language", "en") // headers before responseCode, which connects
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        return c
    }
}
