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

    /** Work ends at 50 min and Next is pressed right away; time then flows to minute [min] (prepare runs on). */
    private fun breakAt(min: Long) = catchUp(p, skip(p, start(p, 0), 50 * MIN), min * MIN)

    @Test fun workHoldsAtZeroAndCountsUp() {
        val held = catchUp(p, start(p, 0), 75 * MIN) // phone off, back 25 min after work ended
        assertEquals(0, held.index)
        assertEquals(0L, held.left(75 * MIN))
        assertEquals(25 * MIN, overtimeMs(p, held, 75 * MIN))
    }

    @Test fun prepFlowsIntoActivityWhichThenHolds() {
        val s1 = breakAt(51)
        assertEquals(Kind.PREP, p[s1.index].kind)
        assertEquals(null, overtimeMs(p, s1, 51 * MIN))
        val s2 = breakAt(80)
        assertEquals(Kind.ACTIVITY, p[s2.index].kind) // prepare didn't hold, the activity does
        assertEquals(20 * MIN, overtimeMs(p, s2, 80 * MIN))
    }

    @Test fun plusTenFromOvertimeRestartsCountdown() {
        val over = catchUp(p, start(p, 0), 55 * MIN)
        val more = extend(p, over, 10 * MIN, 55 * MIN)
        assertEquals(65 * MIN, more.endsAt)
        assertEquals(null, overtimeMs(p, more, 60 * MIN))
        val next = skip(p, catchUp(p, more, 65 * MIN), 65 * MIN)
        assertEquals(65 * MIN, next.workMs) // overtime counts as work
        assertEquals(1, next.cycles)
    }

    @Test fun overtimeCountsAsWork() {
        val held = catchUp(p, start(p, 0), 70 * MIN)
        assertEquals(70 * MIN, totals(p, held, 70 * MIN).first)
    }

    @Test fun extendDuringActivityShiftsRestOfDay() {
        val inBreak = breakAt(53) // activity ends at 60
        val longer = extend(p, inBreak, 10 * MIN, 53 * MIN)
        assertEquals(70 * MIN, longer.endsAt)
        val nextWork = skip(p, catchUp(p, longer, 70 * MIN), 70 * MIN)
        assertEquals(Kind.WORK, p[nextWork.index].kind)
        assertEquals(120 * MIN, nextWork.endsAt)
        assertEquals(0, nextWork.bonus)
    }

    @Test fun extendDuringPrepLengthensActivity() {
        val prep = breakAt(51)
        val act = catchUp(p, extend(p, prep, 10 * MIN, 51 * MIN), 52 * MIN)
        assertEquals(Kind.ACTIVITY, p[act.index].kind)
        assertEquals(18 * MIN, act.phaseMs)
        assertEquals(70 * MIN, act.endsAt)
    }

    @Test fun extendDuringWorkCountdownDoesNothing() {
        val st = start(p, 0)
        assertEquals(st, extend(p, st, 10 * MIN, 5 * MIN))
    }

    @Test fun zeroPrepSkipsPrepPhase() {
        assertFalse(plan(s.copy(prepMin = 0)).any { it.kind == Kind.PREP })
    }

    @Test fun dayFinishesWhenNextIsPressedAtEachEnd() {
        var st = start(p, 0)
        // Next at each work/break end; prepare runs into the activity by itself.
        while (!st.done(p)) st = if (holds(p, st)) skip(p, st, st.endsAt) else catchUp(p, st, st.endsAt)
        assertEquals(8 * 60 * MIN, st.finishedAt)
        assertEquals(8, st.cycles)
        assertEquals(400 * MIN, st.workMs)
    }

    @Test fun pauseShiftsSchedule() {
        val st = start(p, 0)
        val paused = pause(st, 10 * MIN)
        assertEquals(paused, catchUp(p, paused, 500 * MIN)) // paused never advances
        val resumed = resume(paused, 35 * MIN)
        assertEquals(75 * MIN, resumed.endsAt)
    }

    @Test fun skipMovesToNextPhaseNow() {
        val sk = skip(p, start(p, 0), 5 * MIN)
        assertEquals(1, sk.index)
        assertEquals(7 * MIN, sk.endsAt)
    }

    @Test fun totalsCountElapsed() {
        val st = breakAt(56)
        assertEquals(56 * MIN, totals(p, st, 56 * MIN).let { it.first + it.second })
        assertEquals(50 * MIN, totals(p, st, 56 * MIN).first)
    }

    @Test fun skippedWorkCountsOnlyElapsedAndNoCycle() {
        val sk = skip(p, start(p, 0), 20 * MIN)
        assertEquals(20 * MIN, sk.workMs)
        assertEquals(0, sk.cycles)
        assertEquals(1, skip(p, start(p, 0), 50 * MIN).cycles)
    }

    @Test fun pausedTimeIsNotCounted() {
        val paused = pause(start(p, 0), 10 * MIN)
        val resumed = resume(paused, 40 * MIN) // 30 min away
        assertEquals(15 * MIN, totals(p, resumed, 45 * MIN).first)
    }

    @Test fun fixedLengthActivityStretchesBreak() {
        val np = plan(s.copy(activity = "nsdr"))
        assertEquals(20 * MIN, np[2].ms)
        assertEquals(8 * MIN, p[2].ms)
    }

    @Test fun resizeShiftsCurrentPhase() {
        val act = breakAt(53)
        val r = resize(act, 12 * MIN)
        assertEquals(72 * MIN, r.endsAt)
        assertEquals(20 * MIN, r.phaseMs)
    }

    @Test fun guidedStepsLoop() {
        val steps = listOf(Step("a", 30), Step("b", 40))
        assertEquals(0 to 30, stepAt(steps, 0))
        assertEquals(1 to 10, stepAt(steps, 60_000))
        assertEquals(0 to 25, stepAt(steps, 75_000)) // looped
    }

    @Test fun silenceMetering() {
        val quiet = ShortArray(100) { 10 }
        val loud = ShortArray(100) { 16000 }
        assertTrue(dbfs(quiet, 100) < QUIET_DB)
        assertTrue(dbfs(loud, 100) > -10f)
        assertEquals(0.5f, quietShare(listOf(-60f, -20f)), 0.001f)
    }

    @Test fun quickTimerIsOneActivityThenDone() {
        val q = quickPlan("nsdr")
        assertEquals(listOf(Kind.ACTIVITY), q.map { it.kind })
        assertEquals(20 * MIN, q[0].ms)
        val done = catchUp(q, start(q, 0).copy(quick = "nsdr"), 20 * MIN)
        assertTrue(done.done(q))
        assertEquals(20 * MIN, done.breakMs)
    }

    @Test fun crossfadeKeepsPowerEven() {
        assertEquals(1f to 0f, crossfadeGains(0f))
        val (o, i) = crossfadeGains(0.5f)
        assertEquals(1f, o * o + i * i, 0.001f) // no dip in the middle
        assertEquals(0f, crossfadeGains(1f).first, 0.001f)
        assertEquals(1f, crossfadeGains(2f).second, 0.001f) // clamped
    }

    @Test fun versionCompare() {
        assertTrue(isNewer("v1.0.10", "1.0.9"))
        assertFalse(isNewer("v1.0.0", "1.0.0"))
        assertFalse(isNewer("v0.9.9", "1.0.0"))
    }

    @Test fun reminderIsTodayBeforeTenElseTomorrow() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val day = 20_000L * 24 * 60 * MIN // a UTC midnight
        assertEquals(day + 600 * MIN, nextDaily(day + 9 * 60 * MIN, 10, utc))
        assertEquals(day + 24 * 60 * MIN + 600 * MIN, nextDaily(day + 600 * MIN, 10, utc)) // exactly 10:00 → tomorrow
        assertEquals(day + 24 * 60 * MIN + 600 * MIN, nextDaily(day + 15 * 60 * MIN, 10, utc))
    }
}
