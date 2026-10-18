package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TimelineTest {
    private val day = LocalDate.of(2026, 10, 15)
    private fun work(from: Long, to: Long, over: Boolean = false) = Segment(from * MIN, to * MIN, work = true, over = over)
    private fun rest(from: Long, to: Long, over: Boolean = false) = Segment(from * MIN, to * MIN, work = false, over = over)

    @Test fun encodeRoundTripSkipsBadEntries() {
        val segs = listOf(work(0, 50), work(50, 62, over = true), rest(62, 72), rest(72, 75, over = true))
        assertEquals(segs, decodeSegments(encodeSegments(segs)))
        assertEquals(listOf(work(0, 50)), decodeSegments("0,3000000,W;junk;1,2"))
        assertEquals(emptyList<Segment>(), decodeSegments(null))
        assertEquals(emptyList<Segment>(), decodeSegments(""))
    }

    @Test fun runBeforeTheEndIsPlain() {
        assertEquals(listOf(work(0, 30)), splitRun(true, 0, 30 * MIN, overFrom = 50 * MIN))
        assertEquals(listOf(rest(0, 30)), splitRun(false, 0, 30 * MIN, overFrom = null))
    }

    @Test fun runPastTheEndSplitsIntoOvertime() {
        assertEquals(listOf(work(0, 50), work(50, 62, over = true)), splitRun(true, 0, 62 * MIN, overFrom = 50 * MIN))
        assertEquals(listOf(work(55, 60, over = true)), splitRun(true, 55 * MIN, 60 * MIN, overFrom = 50 * MIN))
    }

    @Test fun emptyRunLogsNothing() {
        assertEquals(emptyList<Segment>(), splitRun(true, 10 * MIN, 10 * MIN, overFrom = null))
    }

    @Test fun mergeUnitesPhonesWithoutDuplicates() {
        val a = mapOf(day to listOf(work(0, 50), rest(50, 60)))
        val b = mapOf(day to listOf(rest(50, 60), work(120, 170)), day.plusDays(1) to listOf(work(0, 25)))
        val m = mergeTimelines(a, b)
        assertEquals(listOf(work(0, 50), rest(50, 60), work(120, 170)), m[day])
        assertEquals(listOf(work(0, 25)), m[day.plusDays(1)])
    }

    @Test fun chartSpansWholeHours() {
        val utc = ZoneOffset.UTC
        val nine = day.atTime(9, 10).toInstant(utc).toEpochMilli()
        val segs = listOf(Segment(nine, nine + 50 * MIN, true), Segment(nine + 50 * MIN, nine + 130 * MIN, false))
        val (from, to) = hourSpan(segs, utc)
        assertEquals(day.atTime(9, 0).toInstant(utc).toEpochMilli(), from)
        assertEquals(day.atTime(12, 0).toInstant(utc).toEpochMilli(), to)
    }

    @Test fun backToBackStretchesOfOneKindJoin() {
        val segs = listOf(work(0, 50), work(50, 55, over = true), rest(55, 57), rest(57, 65), work(80, 130))
        assertEquals(listOf(work(0, 50), work(50, 55, over = true), rest(55, 65), work(80, 130)), joined(segs))
    }

    @Test fun blipsFromSkippingAreDropped() {
        val segs = listOf(work(0, 50), Segment(50 * MIN, 50 * MIN + 20_000, work = false), Segment(50 * MIN + 20_000, 50 * MIN + 25_000, work = true), rest(51, 60))
        assertEquals(listOf(work(0, 50), rest(51, 60)), dropBlips(segs, keepLast = false))
        // A stretch that just started today stays visible.
        val live = listOf(work(0, 50), Segment(50 * MIN, 50 * MIN + 20_000, work = false))
        assertEquals(live, dropBlips(live, keepLast = true))
    }

    @Test fun activityLabelsRoundTripAndOldEntriesStillRead() {
        val segs = listOf(work(0, 50), rest(52, 60).copy(label = "nsdr"), rest(60, 62, over = true).copy(label = "lib:a1"))
        assertEquals(segs, decodeSegments(encodeSegments(segs)))
        assertEquals("", decodeSegments("0,600000,B").single().label) // saved before labels existed
    }

    @Test fun activityTotalsSumBreakTimePerActivity() {
        val segs = listOf(work(0, 50), rest(50, 52), rest(52, 60).copy(label = "nsdr"), rest(110, 130).copy(label = "nsdr"), rest(170, 180).copy(label = "walk"))
        assertEquals(mapOf("nsdr" to 28 * MIN, "walk" to 10 * MIN), activityTotals(segs))
    }
}
