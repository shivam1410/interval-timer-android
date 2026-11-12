package com.shivam1410.intervaltimer

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

// Pure library logic (no Android), unit-tested in LibraryTest. Stored and synced by LibraryStore.

/**
 * A quick exercise you added yourself: a YouTube link, a file, or a plain exercise with just a note;
 * plus a tag and how long it runs.
 * Deletes are kept as tombstones ([deleted]) so a sync can't bring them back from another phone.
 */
data class LibraryItem(
    val id: String,
    val title: String,
    val url: String,
    val tag: String,
    val minutes: Int,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val file: String = "", // shared MP3/MP4 kept in the app (and on Drive) instead of a YouTube link
    val note: String = "", // how to do it ("right hand on the wall, 30 s × 3")
    val clip: String = "", // short looping clip of the movement (habit phase 3); not the "Play" media
) {
    val isFile get() = file.isNotEmpty()

    /** No link and no file: tapping it just runs its timer with the note on screen. */
    val isPlain get() = !isFile && url.isBlank()

    /** Links open in YouTube / YouTube Music; files (audio or video) in the app you pick; plain ones show their note. */
    fun toActivity() = Activity(
        id = "lib:$id", emoji = when { isPlain -> "🏋️"; !isFile -> "▶️"; isVideoFile(file) -> "🎬"; else -> "🎵" }, name = title, group = tag,
        hint = note.ifBlank { tag.ifBlank { if (isPlain) "Your own exercise." else if (isFile) "Plays in the app you pick." else "Play the video and follow along." } },
        tracks = if (isFile || isPlain) emptyList() else listOf(url), media = file.ifEmpty { null },
        playLabel = when { isPlain -> ""; isFile -> "Play"; else -> "Play video" }, quickMin = minutes,
        clip = clip.ifEmpty { null },
    )
}

/** At most [n] characters as you see them (code points): a plain take(n) can cut an emoji in half. */
fun String.clip(n: Int): String = if (codePointCount(0, length) <= n) this else substring(0, offsetByCodePoints(0, n))

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

/** One item per line, fields URL-encoded and joined by "|": safe for any title. */
fun encodeLibrary(items: List<LibraryItem>): String = items.joinToString("\n") {
    listOf(it.id, it.updatedAt, if (it.deleted) 1 else 0, it.minutes, enc(it.title), enc(it.url), enc(it.tag), enc(it.file), enc(it.note), enc(it.clip)).joinToString("|")
}

/** 7 fields = written before shared files existed, 8 = before notes and clips; both still read. */
fun decodeLibrary(text: String?): List<LibraryItem> =
    text.orEmpty().lines().mapNotNull { line ->
        val p = line.split("|")
        if (p.size !in 7..10) return@mapNotNull null
        fun opt(i: Int) = p.getOrNull(i)?.let(::dec).orEmpty()
        runCatching {
            LibraryItem(p[0], dec(p[4]), dec(p[5]), dec(p[6]), p[3].toInt(), p[1].toLong(), p[2] == "1", opt(7), opt(8), opt(9))
        }.getOrNull()
    }

fun isVideoFile(name: String) = name.substringAfterLast('.').lowercase() in setOf("mp4", "mkv", "webm", "3gp", "mov")

/** Library files are stored as "lib-<id>.<ext>", locally and on Drive; the extension comes from the shared file. */
fun libraryFileName(id: String, displayName: String?, mime: String?): String {
    val ext = displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
        ?: when { mime?.startsWith("video/") == true -> "mp4"; else -> "mp3" }
    return "lib-$id.$ext"
}

/** Looping clips are stored as "clip-<id>.<ext>"; the extension comes only from a known media type. */
fun clipFileName(id: String, mime: String?): String {
    val ext = when (mime) {
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/3gpp" -> "3gp"
        else -> "mp4"
    }
    return "clip-$id.$ext"
}

/** Newest edit (or delete) of each item wins. */
fun mergeLibrary(a: List<LibraryItem>, b: List<LibraryItem>): List<LibraryItem> =
    (a + b).groupBy { it.id }.values.map { same -> same.maxBy { it.updatedAt } }.sortedBy { it.title.lowercase() }

private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

/** Only https YouTube / YouTube Music links are opened, so a pasted link can't point anywhere else. */
fun isYouTubeUrl(url: String): Boolean {
    val u = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    return u.scheme == "https" && u.host?.lowercase() in YOUTUBE_HOSTS
}

/** The first YouTube link in text shared from another app ("Title\nhttps://…" or just the link). */
fun extractYouTubeUrl(text: String?): String? =
    text.orEmpty().split(Regex("\\s+")).map { it.trimEnd('.', ',', ')', '"', '\'') }.firstOrNull(::isYouTubeUrl)

/** The 11-character video id from any YouTube / YouTube Music link shape, or null. */
fun youTubeId(url: String): String? {
    if (!isYouTubeUrl(url)) return null
    val u = URI(url.trim())
    val id = when {
        u.host.equals("youtu.be", ignoreCase = true) -> u.path.trim('/')
        u.path.startsWith("/shorts/") || u.path.startsWith("/live/") -> u.path.split('/').getOrNull(2)
        else -> u.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
    }
    return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
}

/** Video length from a YouTube watch page ("lengthSeconds":"1234"); null if the page doesn't carry it. */
fun parseLengthSeconds(html: String): Int? =
    Regex("\"lengthSeconds\":\"(\\d+)\"").find(html)?.groupValues?.get(1)?.toIntOrNull()

/** Whole minutes, rounded up so the timer covers the whole video or track; within the 1–120 stepper. */
fun minutesFor(durationMs: Long): Int = ((durationMs + MIN - 1) / MIN).toInt().coerceIn(1, 120)

const val YOUTUBE_APP = YOUTUBE
const val YOUTUBE_MUSIC_APP = "com.google.android.apps.youtube.music"

/** music.youtube.com opens in YouTube Music, other YouTube links in YouTube; null for anything else. */
fun youTubeAppFor(url: String): String? = when {
    !isYouTubeUrl(url) -> null
    URI(url.trim()).host.equals("music.youtube.com", ignoreCase = true) -> YOUTUBE_MUSIC_APP
    else -> YOUTUBE_APP
}

/** YouTube's public 320×180 thumbnail for a link (no key), or null. */
fun youTubeThumb(url: String): String? = youTubeId(url)?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }

/** Name a player shows for a library file: your title plus the file's extension (storage name if untitled). */
fun playerName(title: String, fileName: String): String {
    val clean = title.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").take(80)
    return if (clean.isEmpty()) fileName else "$clean.${fileName.substringAfterLast('.')}"
}
