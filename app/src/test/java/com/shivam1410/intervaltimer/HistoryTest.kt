package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoryTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun day(daysAgo: Long, cycles: Int = 8) = Day(today.minusDays(daysAgo), cycles * 50 * MIN, cycles * 10 * MIN, cycles)

    // What today already holds.

    @Test fun aMorningThatHasNotStartedSaysNothing() {
        assertNull(earlierToday(0, 0, emptyList(), 0))
        assertNull(earlierToday(30_000, 0, emptyList(), 0)) // half a minute is not worth a line
    }

    @Test fun earlierTodayReadsAsASentenceOfWhatIsBehindYou() {
        assertEquals("2 cycles · 1h 48m focused", earlierToday(108 * MIN, 2, emptyList(), 0))
        assertEquals("Morning done", earlierToday(0, 0, listOf("Morning"), 0))
        assertEquals("1 cycle · 50m focused · Morning done · 2 habits", earlierToday(50 * MIN, 1, listOf("Morning"), 2))
        assertEquals("Morning done · Evening done · 1 habit", earlierToday(0, 0, listOf("Morning", "Evening"), 1))
    }

    // Density: how often you showed up, with nothing to break.

    @Test fun thereIsNothingToSayBeforeYouHaveShownUp() {
        assertEquals(Density(0, 0), density(emptyList(), today))
        assertFalse(density(emptyList(), today).worthShowing)
        assertFalse(density(listOf(day(0), day(1)), today).worthShowing) // two finishes is a tally, not a pattern
    }

    @Test fun theWindowIsTheAgeOfTheRecordUntilItReachesAFortnight() {
        assertEquals(Density(3, 4), density(listOf(day(0), day(1), day(3)), today)) // four days old, three of them active
        assertTrue(density(listOf(day(0), day(1), day(3)), today).worthShowing)
        // A record older than a fortnight opens the window to its full 14, but only days inside it count.
        assertEquals(Density(3, 14), density(listOf(day(0), day(1), day(3), day(20)), today))
    }

    @Test fun aDayWithNoFinishedCycleDoesNotCount() {
        val lazyDay = day(1, cycles = 0)
        assertEquals(Density(2, 3), density(listOf(day(0), lazyDay, day(2)), today))
    }

    @Test fun daysOutsideTheWindowAreNotCounted() {
        val all = listOf(day(0), day(1), day(2), day(30), day(40))
        assertEquals(Density(3, 14), density(all, today)) // the two old ones set the age, not the count
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

    @Test fun ambientMergesPerPhoneWithoutDoubleCounting() {
        val mine = mapOf("2026-10-18|a" to 10 * MIN)
        val drive = mapOf("2026-10-18|a" to 6 * MIN, "2026-10-18|b" to 4 * MIN, "2026-10-17|b" to 30 * MIN)
        val merged = mergeAmbient(mine, drive)
        assertEquals(merged, mergeAmbient(merged, drive)) // syncing again changes nothing
        assertEquals(mapOf(LocalDate.of(2026, 10, 18) to 14 * MIN, LocalDate.of(2026, 10, 17) to 30 * MIN), ambientByDate(merged))
        assertEquals(emptyMap<LocalDate, Long>(), ambientByDate(mapOf("junk" to 5L)))
    }
}
