package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeTest {
    private val ig = "com.instagram.android"
    private fun on(min: Long, pkg: String = ig) = AppEvent(pkg, resumed = true, at = min * MIN)
    private fun off(min: Long, pkg: String = ig) = AppEvent(pkg, resumed = false, at = min * MIN)

    private val window = 10 * MIN // 5-min limit -> last 10 min

    @Test fun onScreenTimeIsSummedOverTheLastWindow() {
        // 3 on, 1 away, 2 on, still open at 6: 5 min of the last 10.
        val u = usage(listOf(on(0), off(3), on(4)), ig, now = 6 * MIN, windowMs = window)
        assertTrue(u.onScreen)
        assertEquals(5 * MIN, u.recentMs)
    }

    @Test fun shortCheckInsAddUpEvenWithLongerGaps() {
        // 2 min, 4 min away, 2 min, 1 min away, now 1 min in: 5 min within 10.
        val u = usage(listOf(on(0), off(2), on(6), off(8), on(9)), ig, now = 10 * MIN, windowMs = window)
        assertEquals(5 * MIN, u.recentMs)
    }

    @Test fun timeOlderThanTheWindowFallsOff() {
        // 8 min straight long ago, then 2 min now: only what overlaps the last 10 counts.
        val u = usage(listOf(on(0), off(8), on(20)), ig, now = 22 * MIN, windowMs = window)
        assertEquals(2 * MIN, u.recentMs)
        assertEquals(10 * MIN, u.todayMs)
        val edge = usage(listOf(on(0), off(8)), ig, now = 12 * MIN, windowMs = window)
        assertEquals(6 * MIN, edge.recentMs) // the part from minute 2 to 8
    }

    @Test fun closedAppIsNotOnScreen() {
        val u = usage(listOf(on(0), off(4), on(5, "com.whatsapp")), ig, now = 6 * MIN, windowMs = window)
        assertFalse(u.onScreen)
        assertEquals(4 * MIN, u.todayMs)
    }

    @Test fun nextCheckLandsWhenTheLimitRunsOut() {
        val limit = 10 * MIN
        assertEquals(5 * MIN, nextCheckMs(Usage(false, 0, 0), limit, jitterMs = 0))
        assertEquals(7 * MIN + 30_000, nextCheckMs(Usage(true, 3 * MIN, 3 * MIN), limit, jitterMs = 30_000))
        assertEquals(1 * MIN, nextCheckMs(Usage(true, 12 * MIN, 12 * MIN), limit, jitterMs = 0)) // past it: soon again
    }

    @Test fun nudgeOnlyPastTheLimitOutsideCooldownAndNotWhileWatching() {
        val u = Usage(true, 11 * MIN, 30 * MIN)
        assertTrue(shouldNudge(u, 10 * MIN, lastNudgeAt = 0, now = 60 * MIN, watching = false))
        assertFalse(shouldNudge(u.copy(recentMs = 9 * MIN), 10 * MIN, 0, 60 * MIN, false))
        assertFalse(shouldNudge(u, 10 * MIN, lastNudgeAt = 40 * MIN, now = 60 * MIN, watching = false)) // 30-min cooldown
        assertFalse(shouldNudge(u, 10 * MIN, 0, 60 * MIN, watching = true))
    }

    @Test fun watchingMeansAPlayingTitledLongVideo() {
        assertTrue(isWatching(playing = true, title = "Yoga Stretch", durationMs = 20 * MIN))
        assertTrue(isWatching(playing = true, title = "Lo-fi radio", durationMs = 0)) // live: no length
        assertFalse(isWatching(playing = false, title = null, durationMs = 0)) // Shorts: state NONE, no title
        assertFalse(isWatching(playing = true, title = "A short", durationMs = 45_000))
    }

    @Test fun anotherAppOrScreenOffEndsTheSpanEvenWithoutAPause() {
        // Force-closed apps log no pause: the next app coming forward (or the screen going off) ends it.
        val other = usage(listOf(on(0), AppEvent(OTHER_APP, true, 4 * MIN)), ig, now = 60 * MIN, windowMs = window)
        assertFalse(other.onScreen)
        assertEquals(4 * MIN, other.todayMs)
        val dark = usage(listOf(on(0), AppEvent(OTHER_APP, true, 3 * MIN), on(30)), ig, now = 32 * MIN, windowMs = window)
        assertEquals(2 * MIN, dark.recentMs)
    }

    @Test fun duringTheCooldownTheNextCheckWaitsForItsEnd() {
        val past = Usage(true, 8 * MIN, 40 * MIN) // over the 5-min limit, but nudged 10 min ago
        assertEquals(20 * MIN, nextCheckAfter(past, 5 * MIN, lastNudgeAt = 50 * MIN, now = 60 * MIN, jitterMs = 0))
        // Cooldown over: back to the usual timing.
        assertEquals(1 * MIN, nextCheckAfter(past, 5 * MIN, lastNudgeAt = 0, now = 60 * MIN, jitterMs = 0))
        // App closed during the cooldown: still no point checking before it ends.
        assertEquals(20 * MIN, nextCheckAfter(Usage(false, 0, 40 * MIN), 5 * MIN, lastNudgeAt = 50 * MIN, now = 60 * MIN, jitterMs = 0))
    }

    @Test fun screensInsideTheAppDontEndItsTime() {
        // YouTube on the emulator: launcher screen resumes, pauses, main screen resumes, then the first one STOPS.
        val events = listOf(
            AppEvent(ig, true, 0, "Launch"), AppEvent(ig, false, 0, "Launch"),
            AppEvent(ig, true, 0, "Main"), AppEvent(ig, false, 1_000, "Launch"),
        )
        val u = usage(events, ig, now = 6 * MIN, windowMs = window)
        assertTrue(u.onScreen)
        assertEquals(6 * MIN, u.recentMs)
        // Leaving the last screen does end it.
        val left = usage(events + AppEvent(ig, false, 6 * MIN, "Main"), ig, now = 8 * MIN, windowMs = window)
        assertFalse(left.onScreen)
        assertEquals(6 * MIN, left.recentMs)
    }
}
