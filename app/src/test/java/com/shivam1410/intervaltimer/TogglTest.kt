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
        assertEquals(TogglOp.START, nextOp(e))
        assertEquals(TogglOp.CREATE_DONE, nextOp(e.copy(stop = 60_000))) // never reached Toggl while running
        assertEquals(TogglOp.STOP, nextOp(e.copy(remoteId = 9, stop = 60_000)))
        assertNull(nextOp(e.copy(remoteId = 9))) // running and already in Toggl
        assertEquals(TogglOp.DONE, nextOp(e.copy(remoteId = 9, stop = 60_000, stopSent = true)))
        assertEquals(TogglOp.DELETE, nextOp(e.copy(remoteId = 9, discard = true)))
        assertEquals(TogglOp.DONE, nextOp(e.copy(discard = true))) // discarded before it was ever sent
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
}
