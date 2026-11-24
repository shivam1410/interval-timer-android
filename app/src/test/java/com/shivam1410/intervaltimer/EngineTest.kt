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
        val paused = pause(p, st, 10 * MIN)
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
        val paused = pause(p, start(p, 0), 10 * MIN)
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
        assertEquals(day + 600 * MIN, nextDaily(day + 9 * 60 * MIN, 600, utc))
        assertEquals(day + 24 * 60 * MIN + 600 * MIN, nextDaily(day + 600 * MIN, 600, utc)) // exactly 10:00 → tomorrow
        assertEquals(day + 24 * 60 * MIN + 600 * MIN, nextDaily(day + 15 * 60 * MIN, 600, utc))
        assertEquals(day + 8 * 60 * MIN + 45 * MIN, nextDaily(day + 8 * 60 * MIN, 8 * 60 + 45, utc)) // 08:45
    }

    // Timeline log: what the day graph draws.

    @Test fun pauseLeavesAGapInTheLog() {
        val resumed = resume(pause(p, start(p, 0), 10 * MIN), 40 * MIN)
        val next = skip(p, resumed, 60 * MIN)
        assertEquals(listOf(Segment(0, 10 * MIN, true), Segment(40 * MIN, 60 * MIN, true)), next.log)
    }

    @Test fun overtimeIsLoggedApartFromTheBlock() {
        val next = skip(p, catchUp(p, start(p, 0), 62 * MIN), 62 * MIN)
        assertEquals(listOf(Segment(0, 50 * MIN, true), Segment(50 * MIN, 62 * MIN, true, over = true)), next.log)
    }

    @Test fun plusTenInOvertimeKeepsTheOvertimeSoFar() {
        val more = extend(p, catchUp(p, start(p, 0), 55 * MIN), 10 * MIN, 55 * MIN)
        val next = skip(p, more, 60 * MIN)
        assertEquals(
            listOf(Segment(0, 50 * MIN, true), Segment(50 * MIN, 55 * MIN, true, over = true), Segment(55 * MIN, 60 * MIN, true)),
            next.log,
        )
    }

    @Test fun breakPhasesAreLoggedAsBreak() {
        val inActivity = breakAt(53) // prepare 50-52 flowed into the activity
        val back = skip(p, inActivity, 60 * MIN)
        assertEquals(
            listOf(Segment(0, 50 * MIN, true), Segment(50 * MIN, 52 * MIN, false), Segment(52 * MIN, 60 * MIN, false)),
            back.log,
        )
    }

    @Test fun loggedWorkMatchesFocusTime() {
        var st = start(p, 0)
        while (!st.done(p)) st = if (holds(p, st)) skip(p, st, st.endsAt) else catchUp(p, st, st.endsAt)
        assertEquals(st.workMs, st.log.filter { it.work }.sumOf { it.ms })
        assertEquals(st.breakMs, st.log.filter { !it.work }.sumOf { it.ms })
    }

    @Test fun closeBanksTheRunningStretch() {
        val ended = close(p, start(p, 0), 20 * MIN)
        assertEquals(listOf(Segment(0, 20 * MIN, true)), ended.log)
    }

    @Test fun fullHoursAfterNow() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val h = 60 * MIN
        // 09:20 -> 10:00, 11:00, 12:00; exactly 10:00 -> 11:00 onward (never "now").
        assertEquals(listOf(10 * h, 11 * h, 12 * h), nextFullHours(9 * h + 20 * MIN, 3, utc))
        assertEquals(listOf(11 * h, 12 * h), nextFullHours(10 * h, 2, utc))
    }

    @Test fun breakStretchesCarryTheirActivity() {
        val atAct = catchUp(p, skip(p, start(p, 0), 50 * MIN), 52 * MIN).copy(act = "nsdr") // prepare flowed into the activity
        val closed = close(p, atAct, 55 * MIN)
        assertEquals("nsdr", closed.log.last().label)
        val workClosed = close(p, start(p, 0).copy(act = "nsdr"), 10 * MIN)
        assertEquals("", workClosed.log.last().label) // work is never labelled
    }

    // You set your hours; the cycle count follows from them.

    @Test fun theDefaultHoursStillGiveEightCycles() {
        assertEquals(10 * 60, s.workStart) // 10:00
        assertEquals(18 * 60, s.workEnd) // 18:00
        assertEquals(8, s.cycles)
        assertEquals(0, s.spare)
    }

    @Test fun hoursThatDoNotDivideLeaveTheRemainderFree() {
        val short = s.copy(workStart = 9 * 60, workEnd = 17 * 60 + 30) // 510 min at 60 per cycle
        assertEquals(8, short.cycles)
        assertEquals(30, short.spare)
        assertEquals(8 * 60 * MIN, plan(short).sumOf { it.ms }) // the spare 30 is not padded out
    }

    @Test fun aWindowTooShortForOneCycleHasNoCycles() {
        val tiny = s.copy(workStart = 9 * 60, workEnd = 9 * 60 + 30)
        assertEquals(0, tiny.cycles)
        assertEquals(30, tiny.spare)
        assertTrue(plan(tiny).isEmpty())
    }

    @Test fun hoursThatRunPastMidnightWrapToTheNextDay() {
        val night = s.copy(workStart = 22 * 60, workEnd = 6 * 60) // 8 hours over midnight
        assertEquals(480, windowMin(night.workStart, night.workEnd))
        assertEquals(8, night.cycles)
    }

    @Test fun aLongerRhythmFitsFewerCyclesInTheSameHours() {
        val deep = s.copy(workMin = 90, breakMin = 15) // 105 per cycle in a 480 min day
        assertEquals(4, deep.cycles)
        assertEquals(60, deep.spare)
    }

    // The day drawn to scale.

    @Test fun theDayIsTwoBlocksPerCycleSizedByRealLength() {
        val b = dayBlocks(p, Session(), 0)
        assertEquals(16, b.size) // 8 cycles, work + break
        assertTrue(b[0].work)
        assertFalse(b[1].work)
        assertEquals(50 * MIN, b[0].ms)
        assertEquals(10 * MIN, b[1].ms) // prepare and its activity joined into one break
        assertEquals(8 * 60 * MIN, b.sumOf { it.ms })
    }

    @Test fun anIdleDayIsAllEmptyAndAFinishedOneAllFull() {
        assertTrue(dayBlocks(p, Session(), 0).all { it.done == 0f })
        val ended = Session(index = p.size, phaseMs = 1)
        assertTrue(dayBlocks(p, ended, 0).all { it.done == 1f })
    }

    @Test fun theBlockInProgressIsPartFilled() {
        val mid = catchUp(p, start(p, 0), 20 * MIN) // 20 min into the first 50 min block
        val b = dayBlocks(p, mid, 20 * MIN)
        assertEquals(0.4f, b[0].done, 0.01f)
        assertEquals(0f, b[1].done)
    }

    @Test fun finishedBlocksStayFullAsTheDayGoesOn() {
        val cycle2 = skip(p, start(p, 0), 50 * MIN) // straight into the first break
        val b = dayBlocks(p, cycle2, 50 * MIN)
        assertEquals(1f, b[0].done) // the work block is behind you
        assertTrue(b[1].done < 1f) // the break has only just begun
        assertEquals(0f, b[2].done)
    }

    // What is coming up, as clock times.

    @Test fun nothingIsComingWhenNothingIsRunning() {
        assertTrue(upNext(p, Session(), 0).isEmpty())
        assertTrue(upNext(p, Session(index = p.size, phaseMs = 1), 0).isEmpty())
    }

    @Test fun theNextBlocksCarryTheirOwnClockTimes() {
        val mid = catchUp(p, start(p, 0), 20 * MIN) // 20 min into the first 50 min work block
        val next = upNext(p, mid, 20 * MIN)
        assertEquals(3, next.size)
        // The break starts when this block ends, and prepare + activity count as one 10-minute break.
        assertEquals(50 * MIN, next[0].at)
        assertFalse(next[0].work)
        assertEquals(10 * MIN, next[0].ms)
        // Then cycle 2's work, an hour after the day began.
        assertEquals(60 * MIN, next[1].at)
        assertTrue(next[1].work)
        assertEquals(2, next[1].cycle)
        assertEquals(50 * MIN, next[1].ms)
    }

    @Test fun whatIsLeftAfterTheListIsCountedInWholeCycles() {
        val mid = catchUp(p, start(p, 0), 20 * MIN)
        val next = upNext(p, mid, 20 * MIN) // ends on cycle 2's break
        assertEquals(2, next.last().cycle)
        assertFalse(next.last().work)
        // Cycles 3 to 8 are still to come: six, not five. A cycle's break belongs to the cycle before it.
        assertEquals(6, cyclesAfter(p, next))
        // Ending on a cycle's work leaves the same count, since that cycle's break is part of it.
        assertEquals(6, cyclesAfter(p, next.dropLast(1)))
        assertEquals(0, cyclesAfter(p, emptyList()))
    }

    @Test fun theListStopsAtTheLimitAndAtTheEndOfTheDay() {
        val mid = catchUp(p, start(p, 0), 20 * MIN)
        assertEquals(1, upNext(p, mid, 20 * MIN, limit = 1).size)
        assertTrue(upNext(p, mid, 20 * MIN, limit = 0).isEmpty())
        // Inside the last work block there is nothing after it.
        val last = start(p, 0).copy(index = p.size - 1, endsAt = 1, phaseMs = 1)
        assertTrue(upNext(p, last, 0).isEmpty())
    }

    // Starting late: how much of your day is still ahead.

    private val day = s.copy(workStart = 9 * 60, workEnd = 17 * 60 + 30) // 510 min, 8 cycles, 30 spare

    @Test fun theWholeDayIsAheadWhenItHasNotStarted() {
        assertEquals(510, minsLeftOfDay(9 * 60, day.workStart, day.workEnd))
        assertEquals(510, minsLeftOfDay(7 * 60, day.workStart, day.workEnd)) // before your hours
        assertEquals(8, cyclesLeftToday(day, 7 * 60))
    }

    @Test fun lessIsAheadOnceTheDayIsUnderway() {
        assertEquals(350, minsLeftOfDay(11 * 60 + 40, day.workStart, day.workEnd))
        assertEquals(5, cyclesLeftToday(day, 11 * 60 + 40)) // 350 / 60
    }

    @Test fun minuteOfDayReadsTheClockNotTheEpoch() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        assertEquals(0, minuteOfDay(0, utc))
        assertEquals(11 * 60 + 40, minuteOfDay((11 * 60 + 40) * MIN, utc))
        assertEquals(9 * 60, minuteOfDay(5 * 24 * 60 * MIN + 9 * 60 * MIN, utc)) // five days on, same clock time
    }

    @Test fun epochDayStaysPutWhileASessionRunsPastMidnight() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val startedLateOnDayFive = 5 * 24 * 60 * MIN + 23 * 60 * MIN // 23:00 on day 5
        assertEquals(5L, epochDay(startedLateOnDayFive, utc))
        assertEquals(6L, epochDay(startedLateOnDayFive + 2 * 60 * MIN, utc)) // 01:00 is the next day
        assertEquals(0L, epochDay(0, utc))
    }

    @Test fun nothingIsAheadOnceYourHoursAreOver() {
        assertEquals(0, minsLeftOfDay(17 * 60 + 30, day.workStart, day.workEnd))
        assertEquals(0, cyclesLeftToday(day, 17 * 60 + 30))
    }

    @Test fun aShortenedRunPlansFewerCycles() {
        val five = plan(day, 5)
        assertEquals(5 * 3, five.size)
        assertEquals(5, cyclesIn(five))
        assertEquals(5 * 60 * MIN, five.sumOf { it.ms })
        assertEquals(8, cyclesIn(plan(day))) // the default is still the whole day
    }

    @Test fun upgradingFromAStoredCycleCountKeepsTheDayLength() {
        assertEquals(18 * 60, oldEnd(10 * 60, 8, 50, 10)) // the old default lands on the new one
        assertEquals(6 * 60, oldEnd(22 * 60, 8, 50, 10)) // and wraps past midnight
    }

    @Test fun theNudgeFollowsYourStartUnlessYouSetOne() {
        assertEquals(s.workStart, s.nudgeMin)
        assertEquals(7 * 60, s.copy(reminderMin = 7 * 60).nudgeMin)
        assertEquals(9 * 60, s.copy(workStart = 9 * 60).nudgeMin)
    }

    @Test fun movingTheEndTimeChangesThePlanLength() {
        val sixHours = s.copy(workEnd = 16 * 60) // 10:00-16:00
        assertEquals(6, sixHours.cycles)
        assertEquals(6 * 3, plan(sixHours).size) // WORK, PREP, ACTIVITY per cycle
    }
}
