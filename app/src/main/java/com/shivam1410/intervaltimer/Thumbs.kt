package com.shivam1410.intervaltimer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Library card thumbnails, cached as files/thumbs/<itemId>.jpg (rebuilt on each phone, not synced):
 * YouTube's public thumbnail for links, a frame for video files, embedded cover art for audio.
 * Blocking: call off the main thread. Null = show the emoji instead.
 */
object Thumbs {
    private const val TAG = "Thumbs"

    private fun cache(ctx: Context, id: String) = File(File(ctx.filesDir, "thumbs").apply { mkdirs() }, "$id.jpg")

    fun get(ctx: Context, item: LibraryItem): Bitmap? {
        val f = cache(ctx, item.id)
        if (!f.exists()) {
            val bmp = try {
                if (item.isFile) fromFile(LibraryFiles.file(ctx, item.file)) else youTubeThumb(item.url)?.let(::download)
            } catch (e: IOException) {
                Log.w(TAG, "No thumbnail for ${item.id}", e)
                null
            } ?: return null
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            return bmp
        }
        return BitmapFactory.decodeFile(f.path)
    }

    fun forget(ctx: Context, id: String) = cache(ctx, id).delete()

    private fun download(url: String): Bitmap? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        return c.inputStream.use { BitmapFactory.decodeStream(it) }
    }

    private fun fromFile(file: File): Bitmap? {
        if (!file.exists()) return null // still on Drive; tried again next time the list shows
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.path)
            r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?: if (isVideoFile(file.name)) r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180) else null
        } catch (e: RuntimeException) { // thrown for files it can't parse
            Log.w(TAG, "Can't read ${file.name}", e)
            null
        } finally {
            r.release()
        }
    }
}
