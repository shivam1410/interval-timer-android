package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HistoryTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun day(daysAgo: Long, cycles: Int = 8) = Day(today.minusDays(daysAgo), cycles * 50 * MIN, cycles * 10 * MIN, cycles)

    @Test fun streakCountsBackFromToday() {
        assertEquals(3 to 3, streaks(listOf(day(0), day(1), day(2), day(4)), today))
    }

    @Test fun unfinishedTodayDoesNotBreakStreak() {
        assertEquals(2 to 2, streaks(listOf(day(1), day(2)), today))
    }

    @Test fun gapEndsCurrentStreakButKeepsBest() {
        assertEquals(0 to 3, streaks(listOf(day(2), day(3), day(4)), today))
    }

    @Test fun dayWithoutFullCycleIsNotActive() {
        assertEquals(1 to 1, streaks(listOf(day(0), day(1, cycles = 0)), today))
    }

    @Test fun encodeRoundTripAndBadInput() {
        val d = day(0)
        assertEquals(d, Day.decode(d.date.toString(), d.encode()))
        assertNull(Day.decode("nope", "1,2"))
    }

    @Test fun mergeKeepsFullerDayAndUnion() {
        val local = listOf(day(0, cycles = 3), day(1))
        val remote = listOf(day(0, cycles = 5), day(2))
        val m = merge(local, remote)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), m.map { it.date })
        assertEquals(5, m.last().cycles)
    }

    @Test fun daysUsedCountsOnlyThisMonthWithAnyTime() {
        val days = listOf(
            Day(LocalDate.of(2026, 10, 1), 5 * MIN, 0, 0), // quick timer only still counts
            Day(LocalDate.of(2026, 10, 3), 0, 0, 0), // nothing spent
            day(0),
            Day(LocalDate.of(2026, 9, 30), 50 * MIN, 0, 1), // last month
            Day(LocalDate.of(2025, 10, 5), 50 * MIN, 0, 1), // same month, last year
        )
        assertEquals(2, daysUsedInMonth(days, today))
    }

    @Test fun ambientMergesPerPhoneWithoutDoubleCounting() {
        val mine = mapOf("2026-10-18|a" to 10 * MIN)
        val drive = mapOf("2026-10-18|a" to 6 * MIN, "2026-10-18|b" to 4 * MIN, "2026-10-17|b" to 30 * MIN)
        val merged = mergeAmbient(mine, drive)
        assertEquals(merged, mergeAmbient(merged, drive)) // syncing again changes nothing
        assertEquals(mapOf(LocalDate.of(2026, 10, 18) to 14 * MIN, LocalDate.of(2026, 10, 17) to 30 * MIN), ambientByDate(merged))
        assertEquals(emptyMap<LocalDate, Long>(), ambientByDate(mapOf("junk" to 5L)))
    }
}
