package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CueTest {
    private val lists = CueLists()

    @Test fun breaksRotateThroughTheList() {
        val n = lists.breaks.size
        assertEquals(lists.breaks[0], phaseCue(Phase(Kind.PREP, 1, MIN), quick = false, lists))
        assertEquals(lists.breaks[1], phaseCue(Phase(Kind.ACTIVITY, 2, MIN), quick = false, lists))
        assertEquals(lists.breaks[0], phaseCue(Phase(Kind.PREP, n + 1, MIN), quick = false, lists)) // wraps round
    }

    @Test fun startOfDayOnlyOnTheFirstWorkBlock() {
        assertEquals("Pick today's top 3", phaseCue(Phase(Kind.WORK, 1, MIN), quick = false, lists))
        assertNull(phaseCue(Phase(Kind.WORK, 2, MIN), quick = false, lists))
        assertEquals("Write tomorrow's first task · Close the laptop", endCue(lists))
    }

    @Test fun quietWhenOffEmptyOrAQuickTimer() {
        assertNull(phaseCue(Phase(Kind.ACTIVITY, 1, MIN), quick = true, lists)) // a one-off NSDR gets no cue
        assertNull(phaseCue(Phase(Kind.PREP, 1, MIN), quick = false, lists.copy(on = false)))
        assertNull(phaseCue(Phase(Kind.PREP, 1, MIN), quick = false, lists.copy(breaks = emptyList())))
        assertNull(endCue(lists.copy(end = emptyList())))
        assertEquals("Walk", phaseCue(Phase(Kind.PREP, 2, MIN), quick = false, lists.copy(breaks = listOf("", "Walk", " ")))) // a blank + Add line is skipped
    }

    @Test fun editedListsRoundTrip() {
        val mine = CueLists(on = false, start = listOf("Water | coffee"), breaks = listOf("Walk; 5 min", "Eyes 20-20-20"), end = emptyList())
        assertEquals(mine, decodeCues(encodeCues(mine)))
        assertEquals(CueLists(), decodeCues(null)) // first run: the defaults
    }
}
