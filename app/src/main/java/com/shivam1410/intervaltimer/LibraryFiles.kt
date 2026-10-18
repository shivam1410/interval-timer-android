package com.shivam1410.intervaltimer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Shared MP3/MP4 files in the library: copied into app storage (files/library/lib-<id>.<ext>),
 * uploaded to the Drive app folder by Drive.sync, and played with the phone's own player.
 */
object LibraryFiles {
    private const val TAG = "LibraryFiles"
    const val MAX_BYTES = 500L * 1024 * 1024

    fun dir(ctx: Context) = File(ctx.filesDir, "library").apply { mkdirs() }
    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    data class Imported(val name: String, val title: String)

    /** Copies a shared file in; throws IOException if it's too big or unreadable. Runs off the main thread. */
    fun import(ctx: Context, uri: Uri, mime: String?, id: String): Imported {
        var display: String? = null
        var size = -1L
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                display = c.getString(0)
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size > MAX_BYTES) throw IOException("File is over ${MAX_BYTES / 1024 / 1024} MB")
        val name = libraryFileName(id, display, mime)
        val dest = file(ctx, name)
        try {
            val copied = ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> copyCapped(input, out) }
            } ?: throw IOException("Can't open the shared file")
            Log.i(TAG, "Imported $name ($copied bytes)")
        } catch (e: IOException) {
            dest.delete()
            throw e
        }
        return Imported(name, display?.substringBeforeLast('.')?.take(80).orEmpty())
    }

    /** Stops at MAX_BYTES even when the sender didn't report a size. */
    private fun copyCapped(input: java.io.InputStream, out: java.io.OutputStream): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return total
            total += n
            if (total > MAX_BYTES) throw IOException("File is over ${MAX_BYTES / 1024 / 1024} MB")
            out.write(buf, 0, n)
        }
    }

    /** Length of a library file in ms, read from its metadata; null if Android can't tell. */
    fun durationMs(ctx: Context, name: String): Long? {
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(file(ctx, name).path)
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: RuntimeException) { // thrown for files it can't parse
            Log.w(TAG, "No duration for $name", e)
            null
        } finally {
            r.release()
        }
    }

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.')) ?: "audio/*"

    /** Opens a library video in the phone's player; returns why not, or null when it opened. */
    fun play(ctx: Context, name: String): String? {
        val f = file(ctx, name)
        if (!f.exists()) return "Downloading from Drive, try again in a moment"
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            null
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No player for $name", e)
            "No app on this phone can play this video"
        }
    }
}
