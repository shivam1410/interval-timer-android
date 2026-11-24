package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TimelineTest {
    // Offering something you haven't done lately.

    private fun used(id: String, daysAgo: Long) =
        LocalDate.of(2026, 10, 15).minusDays(daysAgo) to listOf(Segment(0, MIN, work = false, label = id))

    private val today = LocalDate.of(2026, 10, 15)

    @Test fun lastUsedKeepsTheMostRecentDayPerActivity() {
        val lines = mapOf(used("stretch", 0), used("sketch", 12))
        val last = lastUsed(lines)
        assertEquals(today, last["stretch"])
        assertEquals(today.minusDays(12), last["sketch"])
        assertEquals(0L, daysSinceUsed(last, "stretch", today))
        assertEquals(12L, daysSinceUsed(last, "sketch", today))
        assertNull(daysSinceUsed(last, "nap", today)) // never used
    }

    @Test fun workStretchesAndUnlabelledBreaksAreNotActivities() {
        val lines = mapOf(
            today to listOf(Segment(0, MIN, work = true, label = "stretch"), Segment(MIN, 2 * MIN, work = false)),
        )
        assertEquals(emptyMap<String, LocalDate>(), lastUsed(lines))
    }

    @Test fun theSuggestionIsWhateverYouHaveLeftLongest() {
        val last = lastUsed(mapOf(used("stretch", 0), used("sketch", 12), used("walk", 3)))
        val ids = listOf("stretch", "sketch", "walk")
        assertEquals("sketch", staleChoice(ids, last, today, exclude = "stretch"))
        // Never used beats merely old, and the one you are already offered is skipped.
        assertEquals("nap", staleChoice(ids + "nap", last, today, exclude = "stretch"))
        assertEquals("sketch", staleChoice(ids, last, today, exclude = "nap"))
    }

    @Test fun nothingIsSuggestedWhenEverythingIsRecent() {
        val last = lastUsed(mapOf(used("stretch", 0), used("sketch", 2)))
        assertNull(staleChoice(listOf("stretch", "sketch"), last, today, exclude = "stretch"))
    }

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

    // "Your 9th walk in 14 days": how often one activity has come round lately.
    @Test fun timesDoneCountsEachRunInsideTheWindow() {
        fun day(n: Long, vararg segs: Segment) = today.minusDays(n) to segs.toList()
        fun walk(ms: Long = 15 * MIN) = Segment(0, ms, work = false, label = "walk")
        val lines = mapOf(
            day(0, walk(), walk()),          // twice today
            day(3, walk()),
            day(13, walk()),                 // the oldest day still inside a fortnight
            day(14, walk()),                 // one day too old
            day(1, walk(30_000)),            // half a minute: a blip, not a walk
            day(2, Segment(0, 15 * MIN, work = false, label = "sketch")),
        )
        assertEquals(4, timesDone(lines, "walk", today))
        assertEquals(1, timesDone(lines, "sketch", today))
        assertEquals(0, timesDone(lines, "nap", today))
        // A shorter window looks back less far.
        assertEquals(3, timesDone(lines, "walk", today, window = 4))
    }

    // One doing of a thing leaves several stretches behind: a timer that holds at 0 and counts up is
    // split at its planned end, and almost every walk ends that way.
    @Test fun oneWalkThatRanIntoOvertimeIsStillOneWalk() {
        val ran = listOf(
            Segment(0, 15 * MIN, work = false, label = "walk"),
            Segment(15 * MIN, 18 * MIN, work = false, over = true, label = "walk"),
        )
        assertEquals(1, timesDone(mapOf(today to ran), "walk", today))
        // A false start and the real thing, back to back, are one walk of 5 m 40 s — not a blip and a walk.
        val after = listOf(
            Segment(0, 40_000, work = false, label = "walk"),
            Segment(40_000, 40_000 + 5 * MIN, work = false, label = "walk"),
        )
        assertEquals(1, timesDone(mapOf(today to after), "walk", today))
        // Two sittings with something else between them are two.
        val twice = listOf(
            Segment(0, 15 * MIN, work = false, label = "walk"),
            Segment(15 * MIN, 30 * MIN, work = true),
            Segment(30 * MIN, 45 * MIN, work = false, label = "walk"),
        )
        assertEquals(2, timesDone(mapOf(today to twice), "walk", today))
    }

    @Test fun ordinalsReadTheWayPeopleSayThem() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th"), (1..4).map(::ordinal))
        assertEquals(listOf("11th", "12th", "13th"), (11..13).map(::ordinal))
        assertEquals(listOf("21st", "22nd", "23rd"), (21..23).map(::ordinal))
        assertEquals("101st", ordinal(101))
        assertEquals("111th", ordinal(111))
    }

    // The workday's own stretches, summed; the ones you chose an activity for are their own things.
    @Test fun theWorkdayLineCountsOnlyTheWorkdaysOwnStretches() {
        fun seg(fromMin: Int, toMin: Int, work: Boolean, label: String = "") =
            Segment(fromMin * MIN, toMin * MIN, work, label = label)
        val day = listOf(
            seg(0, 50, true),
            seg(50, 60, false),
            seg(60, 75, false, "walk"),   // a walk is not the workday's break
            seg(75, 125, true),
        )
        assertEquals("1h 40m work · 10m breaks", workdayLine(day))
        assertEquals("50m work", workdayLine(listOf(seg(0, 50, true))))
        assertEquals("10m breaks", workdayLine(listOf(seg(0, 10, false))))
        assertNull(workdayLine(emptyList()))
        assertNull(workdayLine(listOf(seg(0, 15, false, "walk")))) // nothing but a walk is no workday
    }
}
