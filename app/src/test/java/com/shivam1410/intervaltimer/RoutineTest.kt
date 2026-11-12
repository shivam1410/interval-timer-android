package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RoutineTest {
    private val today = LocalDate.of(2026, 11, 12)
    private fun r(id: String = "m", at: Long = 1, done: Map<LocalDate, Int> = emptyMap()) =
        Routine(id, "☀️", "Morning", listOf("Meds", "Water"), remindMin = 450, done = done, updatedAt = at)

    @Test fun defaultsAreMorningLeavingEvening() {
        assertEquals(listOf("Morning", "Leaving the house", "Evening"), DEFAULT_ROUTINES.map { it.name })
        assertEquals(listOf("Take meds", "Drink a glass of water", "Brush teeth", "Eat breakfast"), DEFAULT_ROUTINES[0].steps)
        assertEquals(listOf("Keys", "Wallet", "Phone", "Earbuds", "Water bottle"), DEFAULT_ROUTINES[1].steps)
        assertTrue(DEFAULT_ROUTINES.all { it.updatedAt == 0L }) // any edit of yours wins over the shipped defaults
    }

    @Test fun doneTodayIsTheMinuteItWasFinished() {
        assertNull(doneToday(r(), today))
        assertEquals(8 * 60 + 14, doneToday(r(done = mapOf(today to 494)), today))
        assertNull(doneToday(r(done = mapOf(today.minusDays(1) to 494)), today))
        assertEquals("8:14", fmtMinute(494))
        assertEquals("22:05", fmtMinute(22 * 60 + 5))
    }

    @Test fun encodeRoundTripKeepsAwkwardText() {
        val odd = Routine("x", "🚪", "Out | door; now", listOf("Keys, wallet", "Phone|charger", "Water; bottle"), null, mapOf(today to 600), 7, deleted = true)
        val all = listOf(odd, r())
        assertEquals(all, decodeRoutines(encodeRoutines(all)))
        assertEquals(emptyList<Routine>(), decodeRoutines(null))
    }

    @Test fun mergeKeepsNewestEditAndEveryFinish() {
        val mine = r(at = 5, done = mapOf(today to 494))
        val theirs = r(at = 9, done = mapOf(today.minusDays(1) to 500)).copy(name = "Mornings")
        val m = mergeRoutines(listOf(mine), listOf(theirs)).single()
        assertEquals("Mornings", m.name)
        assertEquals(mapOf(today to 494, today.minusDays(1) to 500), m.done)
    }
}
