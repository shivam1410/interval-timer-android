package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogglTest {
    private val p = plan(Settings()) // 50/10 x 8: WORK, PREP, ACTIVITY, …
    private val day = start(p, 0)
    private val breathing = activity("breathing")

    @Test fun workBlockIsTrackedAsWork() {
        val t = desiredTrack(p, day, breathing, mapOf(TOGGL_WORK to "deep"))
        assertEquals("Work", t?.description)
        assertEquals(listOf("deep"), t?.tags)
        assertEquals(0L, t?.start)
    }

    @Test fun prepareAndAmbientSoundsAreNeverSent() {
        val prep = skip(p, day, 50 * MIN)
        assertEquals(Kind.PREP, p[prep.index].kind)
        assertNull(desiredTrack(p, prep, breathing, emptyMap()))
        val act = skip(p, prep, 52 * MIN)
        assertNull(desiredTrack(p, act, activity("rain"), emptyMap()))
        assertEquals("NSDR", desiredTrack(p, act, activity("nsdr"), emptyMap())?.description)
    }

    @Test fun skipMappingAndPauseSendNothing() {
        assertNull(desiredTrack(p, day, breathing, mapOf(TOGGL_WORK to TOGGL_SKIP)))
        assertNull(desiredTrack(p, pause(p, day, 5 * MIN), breathing, emptyMap()))
    }

    @Test fun libraryItemsUseTheirOwnTag() {
        val item = LibraryItem("y", "Yoga", "https://youtu.be/x", "stretch", 10, 1)
        extraActivities = listOf(item.toActivity())
        val quick = start(quickPlan("lib:y"), 0).copy(quick = "lib:y")
        val t = desiredTrack(quickPlan("lib:y"), quick, activity("lib:y"), emptyMap())
        assertEquals("Yoga", t?.description)
        assertEquals(listOf("stretch"), t?.tags)
        extraActivities = emptyList()
    }

    @Test fun reconcileStopsTheOldEntryAndStartsTheNew() {
        val work = Track("a", "Work", emptyList(), 0)
        val one = reconcile(emptyList(), work, 0, "1")
        assertEquals(1, one.size)
        assertEquals(one, reconcile(one, work, 10 * MIN, "x")) // same thing still running: nothing changes
        val two = reconcile(one, Track("b", "NSDR", emptyList(), 50 * MIN), 50 * MIN, "2")
        assertEquals(50 * MIN, two[0].stop)
        assertNull(two[1].stop)
        val idle = reconcile(two, null, 70 * MIN, "3")
        assertTrue(idle.all { it.stop != null })
    }

    @Test fun nextCallFollowsTheEntryLifecycle() {
        val e = TogglEntry("1", null, "k", "Work", emptyList(), 0, null)
        val old = TOGGL_MIN_MS // long enough to be worth a line
        assertEquals(TogglOp.START, nextOp(e, old))
        assertEquals(TogglOp.CREATE_DONE, nextOp(e.copy(stop = 10 * MIN), old)) // never reached Toggl while running
        assertEquals(TogglOp.STOP, nextOp(e.copy(remoteId = 9, stop = 10 * MIN), old))
        assertNull(nextOp(e.copy(remoteId = 9), old)) // running and already in Toggl
        assertEquals(TogglOp.DONE, nextOp(e.copy(remoteId = 9, stop = 10 * MIN, stopSent = true), old))
        assertEquals(TogglOp.DELETE, nextOp(e.copy(remoteId = 9, discard = true), old))
        assertEquals(TogglOp.DONE, nextOp(e.copy(discard = true), old)) // discarded before it was ever sent
    }

    // A phase shorter than five minutes is never sent at all, rather than appearing in Toggl and being
    // deleted a minute later: two minutes of prepare, a break you cut short, a mistaken tap.
    @Test fun aShortPhaseNeverReachesToggl() {
        val running = TogglEntry("1", null, "k", "Work", emptyList(), 0, null)
        assertNull(nextOp(running, 4 * MIN))                       // still too young to send
        assertNull(nextOp(running, TOGGL_MIN_MS - 1))
        assertEquals(TogglOp.START, nextOp(running, TOGGL_MIN_MS)) // on the mark it goes, back-dated
        // Over before it was worth sending: forgotten, with no call made at all.
        assertEquals(TogglOp.DONE, nextOp(running.copy(stop = 4 * MIN), 4 * MIN))
        assertEquals(TogglOp.DONE, nextOp(running.copy(stop = TOGGL_MIN_MS - 1), 9 * MIN))
        assertEquals(TogglOp.CREATE_DONE, nextOp(running.copy(stop = TOGGL_MIN_MS), 9 * MIN))
        // One already in Toggl is still stopped properly, however short it turned out to be.
        assertEquals(TogglOp.STOP, nextOp(running.copy(remoteId = 9, stop = 1 * MIN), 9 * MIN))
    }

    // The flush that catches a held-back entry is scheduled from the entry itself.
    @Test fun aHeldEntrySaysWhenItIsWorthSending() {
        val running = TogglEntry("1", null, "k", "Work", emptyList(), 2 * MIN, null)
        assertEquals(2 * MIN + TOGGL_MIN_MS, maturesAt(running))
        assertNull(maturesAt(running.copy(remoteId = 9)))              // already sent
        assertNull(maturesAt(running.copy(stop = 3 * MIN)))            // over; nextOp decides
        assertNull(maturesAt(running.copy(discard = true)))            // thrown away
    }

    @Test fun discardMarksOnlyThatSession() {
        val a = TogglEntry("1", 5, sessionKey(100, 0, "work"), "Work", emptyList(), 100, null)
        val b = TogglEntry("2", 6, sessionKey(999, 0, "work"), "Work", emptyList(), 999, 2000, stopSent = true)
        val out = discardSession(listOf(a, b), 100)
        assertTrue(out[0].discard)
        assertEquals(false, out[1].discard)
    }

    @Test fun timesAreWholeSecondsUtc() {
        assertEquals("2026-10-18T15:30:00Z", togglTime(1792337400031))
    }

    @Test fun aRoutineIsSentWithItsTagOnlyOnceYouPickOne() {
        val r = Routine("ten", "⭐", "10 am", listOf("Water"), 600, emptyMap(), 1)
        assertNull(routineTrack(r, 1_000, emptyMap())) // not picked yet: not sent
        assertNull(routineTrack(r, 1_000, mapOf("routine:ten" to TOGGL_SKIP)))
        val t = routineTrack(r, 1_000, mapOf("routine:ten" to "Health"))
        assertEquals(Track("routine:ten:1000", "10 am", listOf("Health"), 1_000), t)
        assertEquals(emptyList<String>(), routineTrack(r, 1_000, mapOf("routine:ten" to ""))?.tags) // sent, no tag
    }
}
