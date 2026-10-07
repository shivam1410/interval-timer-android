package com.shivam1410.intervaltimer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

const val REPO = "shivam1410/interval-timer-android"

/**
 * Sounds are not bundled in the APK: resources/manifest.json in the GitHub repo lists them and
 * they are downloaded once into filesDir/media, then everything works offline.
 */
object Media {
    private const val BASE = "https://raw.githubusercontent.com/$REPO/main/resources/"

    data class Sound(val id: String, val name: String, val file: String, val loop: Boolean)

    val sounds = MutableStateFlow<List<Sound>>(emptyList())
    val downloading = MutableStateFlow(false)
    private lateinit var dir: File

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "media").apply { mkdirs() }
        File(dir, "manifest.json").takeIf { it.exists() }?.let { sounds.value = parse(it.readText()) }
    }

    fun file(id: String): File? = sounds.value.firstOrNull { it.id == id }?.let { File(dir, it.file) }?.takeIf { it.exists() }

    val missing get() = sounds.value.isEmpty() || sounds.value.any { file(it.id) == null }

    // ponytail: files are fetched once by name; publish changed audio under a new file name.
    fun sync() {
        if (downloading.value) return
        downloading.value = true
        thread {
            try {
                val json = fetch(BASE + "manifest.json")
                val list = parse(json)
                list.filter { !File(dir, it.file).exists() }.forEach { download(BASE + it.file, File(dir, it.file)) }
                File(dir, "manifest.json").writeText(json)
                sounds.value = list
            } catch (e: IOException) {
                Log.w("Media", "Sound sync failed; will retry next launch", e)
            } finally {
                downloading.value = false
            }
        }
    }

    private fun parse(json: String): List<Sound> {
        val arr = JSONObject(json).getJSONArray("sounds")
        return List(arr.length()) { i ->
            arr.getJSONObject(i).run { Sound(getString("id"), getString("name"), getString("file"), optBoolean("loop")) }
        }
    }
}

private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = 15_000
    readTimeout = 30_000
    setRequestProperty("Accept", "application/vnd.github+json, */*")
    if (responseCode !in 200..299) throw IOException("HTTP $responseCode for $url")
}

fun fetch(url: String): String = open(url).inputStream.use { it.readBytes().decodeToString() }

/** Downloads to a temp file first so a half-finished download is never mistaken for a real one. */
fun download(url: String, dest: File) {
    val tmp = File(dest.path + ".part")
    open(url).inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
    if (!tmp.renameTo(dest)) throw IOException("Cannot move $tmp")
}
