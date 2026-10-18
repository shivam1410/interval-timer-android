package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private fun item(id: String, at: Long = 1, deleted: Boolean = false) =
        LibraryItem(id, "Title $id", "https://www.youtube.com/watch?v=$id", "move", 10, at, deleted)

    @Test fun encodeRoundTripKeepsAwkwardText() {
        val odd = LibraryItem("a1", "Yoga | 20 min, \"deep\"\nstretch", "https://youtu.be/x?t=1&a=b", "méditation", 20, 42, false)
        val all = listOf(odd, item("b2", deleted = true))
        assertEquals(all, decodeLibrary(encodeLibrary(all)))
        assertEquals(emptyList<LibraryItem>(), decodeLibrary(null))
        assertEquals(listOf(item("ok")), decodeLibrary("junk\n" + encodeLibrary(listOf(item("ok")))))
    }

    @Test fun mergeKeepsNewestPerIdIncludingDeletes() {
        val local = listOf(item("a", at = 5), item("b", at = 1))
        val remote = listOf(item("a", at = 3).copy(title = "old"), item("b", at = 9, deleted = true), item("c", at = 2))
        val m = mergeLibrary(local, remote).associateBy { it.id }
        assertEquals("Title a", m.getValue("a").title)
        assertTrue(m.getValue("b").deleted) // deleted on the other phone stays deleted
        assertEquals(setOf("a", "b", "c"), m.keys)
    }

    @Test fun onlyYouTubeHttpsLinksAreAccepted() {
        listOf(
            "https://www.youtube.com/watch?v=AF9d2Icl4fA", "https://youtu.be/AF9d2Icl4fA",
            "https://m.youtube.com/watch?v=x", "https://music.youtube.com/watch?v=x", "https://youtube.com/shorts/x",
        ).forEach { assertTrue(it, isYouTubeUrl(it)) }
        listOf(
            "http://www.youtube.com/watch?v=x", "https://evil.com/youtube.com", "https://youtube.com.evil.com/x",
            "javascript:alert(1)", "", "not a url",
        ).forEach { assertFalse(it, isYouTubeUrl(it)) }
    }

    @Test fun itemBecomesAQuickTimerActivity() {
        val a = item("z").toActivity()
        assertEquals("lib:z", a.id)
        assertEquals(listOf("https://www.youtube.com/watch?v=z"), a.tracks)
        assertEquals(10, a.quickMin)
        assertEquals("move", a.group)
        assertEquals("Play the video and follow along.", item("z").copy(tag = "").toActivity().hint) // no blank line on the card
    }

    @Test fun sharedTextYieldsTheYouTubeLink() {
        assertEquals("https://youtu.be/AF9d2Icl4fA?si=abc", extractYouTubeUrl("https://youtu.be/AF9d2Icl4fA?si=abc"))
        // YouTube Music shares "title + link"; trailing punctuation is not part of the link.
        assertEquals("https://music.youtube.com/watch?v=bk_lD69u204", extractYouTubeUrl("Yog Nidra\nhttps://music.youtube.com/watch?v=bk_lD69u204."))
        assertEquals(null, extractYouTubeUrl("look https://evil.com/watch?v=x"))
        assertEquals(null, extractYouTubeUrl(null))
    }

    @Test fun sharedFilesRoundTripAndOldLinesStillRead() {
        val f = item("f").copy(url = "", file = "lib-f.mp3")
        assertEquals(listOf(f), decodeLibrary(encodeLibrary(listOf(f))))
        val old = "a|1|0|10|T|https%3A%2F%2Fyoutu.be%2Fx|move" // 7 fields, before files
        assertEquals("", decodeLibrary(old).single().file)
        val a = f.toActivity()
        assertEquals("audio plays in the app", "lib-f.mp3", a.sound)
        assertEquals(null, a.media)
        assertEquals(emptyList<String>(), a.tracks)
        val v = f.copy(file = "lib-f.mp4").toActivity()
        assertEquals("video opens the player", "lib-f.mp4", v.media)
        assertEquals(null, v.sound)
    }

    @Test fun fileNamesKeepASafeExtension() {
        assertEquals("lib-a.mp3", libraryFileName("a", "Song.MP3", "audio/mpeg"))
        assertEquals("lib-a.mp4", libraryFileName("a", "no extension", "video/mp4"))
        assertEquals("lib-a.mp3", libraryFileName("a", "evil.../../x", "audio/ogg"))
        assertEquals("lib-a.m4a", libraryFileName("a", "voice.m4a", null))
    }

    @Test fun videoIdFromEveryLinkShape() {
        assertEquals("AF9d2Icl4fA", youTubeId("https://www.youtube.com/watch?v=AF9d2Icl4fA&t=30"))
        assertEquals("AF9d2Icl4fA", youTubeId("https://youtu.be/AF9d2Icl4fA?si=abc"))
        assertEquals("bk_lD69u204", youTubeId("https://music.youtube.com/watch?v=bk_lD69u204&list=x"))
        assertEquals("abcDEF12345", youTubeId("https://youtube.com/shorts/abcDEF12345"))
        assertEquals(null, youTubeId("https://www.youtube.com/feed/library"))
        assertEquals(null, youTubeId("https://evil.com/watch?v=AF9d2Icl4fA"))
    }

    @Test fun lengthComesFromThePage() {
        assertEquals(1234, parseLengthSeconds("""..."videoDetails":{"videoId":"x","lengthSeconds":"1234","keywords"..."""))
        assertEquals(null, parseLengthSeconds("<html>consent page</html>"))
    }

    @Test fun durationRoundsUpToWholeMinutes() {
        assertEquals(21, minutesFor(20 * MIN + 1_000)) // 20:01 -> 21 so the video fits
        assertEquals(20, minutesFor(20 * MIN))
        assertEquals(1, minutesFor(10_000))
        assertEquals(120, minutesFor(5 * 60 * MIN)) // long mixes cap at the stepper's max
    }
}
