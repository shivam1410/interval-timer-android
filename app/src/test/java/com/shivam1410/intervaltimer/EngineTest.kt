package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val s = Settings() // 50/10 x 8, 2 min prep
    private val p = plan(s)

    @Test fun planIsEightHoursOfWorkPrepActivity() {
        assertEquals(24, p.size)
        assertEquals(8 * 60 * MIN, p.sumOf { it.ms })
        assertEquals(listOf(Kind.WORK, Kind.PREP, Kind.ACTIVITY), p.take(3).map { it.kind })
        assertEquals(8 * MIN, p[2].ms)
    }

    @Test fun longBreakEveryN() {
        val lp = plan(s.copy(longEvery = 3, longMin = 20, longActivity = "nap"))
        val longs = lp.filter { it.long && it.kind == Kind.ACTIVITY }
        assertEquals(listOf(3, 6), longs.map { it.cycle })
        assertEquals(18 * MIN, longs[0].ms)
        assertEquals("nap", longs[0].activity!!.id)
    }

    @Test fun zeroPrepSkipsPrepPhase() {
        assertFalse(plan(s.copy(prepMin = 0)).any { it.kind == Kind.PREP })
    }

    @Test fun catchUpReconstructsPhaseAfterReboot() {
        // 9:00 start, phone off 9:37 -> 10:15. Expect cycle 2 work with 35 min left.
        val t0 = 0L
        val after = catchUp(p, start(p, t0), t0 + 75 * MIN)
        assertEquals(Kind.WORK, p[after.index].kind)
        assertEquals(2, p[after.index].cycle)
        assertEquals(35 * MIN, after.left(t0 + 75 * MIN))
    }

    @Test fun catchUpFinishesDay() {
        val done = catchUp(p, start(p, 0), 9 * 60 * MIN)
        assertTrue(done.done(p))
        assertEquals(8 * 60 * MIN, done.finishedAt)
    }

    @Test fun pauseShiftsSchedule() {
        val st = start(p, 0)
        val paused = pause(st, 10 * MIN)
        assertEquals(paused, catchUp(p, paused, 500 * MIN)) // paused never advances
        val resumed = resume(paused, 35 * MIN)
        assertEquals(75 * MIN, resumed.endsAt)
    }

    @Test fun waitBeforeWorkStopsAtWork() {
        val s2 = catchUp(p, start(p, 0), 61 * MIN, waitBeforeWork = true)
        assertEquals(3, s2.index)
        assertTrue(s2.paused)
        assertEquals(50 * MIN, s2.pausedLeft)
    }

    @Test fun skipMovesToNextPhaseNow() {
        val sk = skip(p, start(p, 0), 5 * MIN)
        assertEquals(1, sk.index)
        assertEquals(7 * MIN, sk.endsAt)
    }

    @Test fun totalsCountElapsed() {
        val st = catchUp(p, start(p, 0), 70 * MIN)
        assertEquals(70 * MIN, totals(p, st, 70 * MIN).let { it.first + it.second })
        assertEquals(60 * MIN, totals(p, st, 70 * MIN).first)
    }

    @Test fun versionCompare() {
        assertTrue(isNewer("v1.0.10", "1.0.9"))
        assertFalse(isNewer("v1.0.0", "1.0.0"))
        assertFalse(isNewer("v0.9.9", "1.0.0"))
    }
}
