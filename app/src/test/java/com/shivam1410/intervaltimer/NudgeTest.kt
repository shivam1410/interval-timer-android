package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeTest {
    private val ig = "com.instagram.android"
    private fun on(min: Long, pkg: String = ig) = AppEvent(pkg, resumed = true, at = min * MIN)
    private fun off(min: Long, pkg: String = ig) = AppEvent(pkg, resumed = false, at = min * MIN)

    @Test fun shortStepsAwayDontResetTheSession() {
        // 0-4 on, 1 min away, 5-9 on and still open at 9: one 8-minute session.
        val u = usage(listOf(on(0), off(4), on(5)), ig, now = 9 * MIN, gapMs = 2 * MIN)
        assertTrue(u.onScreen)
        assertEquals(8 * MIN, u.sessionMs)
    }

    @Test fun longerBreaksStartANewSession() {
        val u = usage(listOf(on(0), off(4), on(10)), ig, now = 12 * MIN, gapMs = 2 * MIN)
        assertEquals(2 * MIN, u.sessionMs)
        assertEquals(6 * MIN, u.todayMs)
    }

    @Test fun closedAppIsNotOnScreen() {
        val u = usage(listOf(on(0), off(4), on(5, "com.whatsapp")), ig, now = 6 * MIN, gapMs = 2 * MIN)
        assertFalse(u.onScreen)
        assertEquals(0L, u.sessionMs)
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
        assertFalse(shouldNudge(u.copy(sessionMs = 9 * MIN), 10 * MIN, 0, 60 * MIN, false))
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
        val other = usage(listOf(on(0), AppEvent(OTHER_APP, true, 4 * MIN)), ig, now = 60 * MIN, gapMs = 2 * MIN)
        assertFalse(other.onScreen)
        assertEquals(4 * MIN, other.todayMs)
        val dark = usage(listOf(on(0), AppEvent(OTHER_APP, true, 3 * MIN), on(30)), ig, now = 32 * MIN, gapMs = 2 * MIN)
        assertEquals(2 * MIN, dark.sessionMs)
    }
}
