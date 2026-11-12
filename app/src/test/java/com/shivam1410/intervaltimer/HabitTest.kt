package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitTest {
    private val start = LocalDate.of(2026, 11, 1)
    private fun habit(stages: List<Stage> = TIB_RAISES, done: Set<LocalDate> = emptySet(), at: Long = 1) =
        Habit("h1", "item1", start, stages, remindMin = null, done = done, updatedAt = at)
    private fun day(n: Int) = start.plusDays(n - 1L)

    @Test fun tibRaiseProgramFollowsTheWeeks() {
        val h = habit()
        assertEquals(30, h.totalDays)
        assertEquals(1, h.dayOf(start))
        assertEquals("1 × 10", h.stageOn(1)?.let(::label))
        assertEquals("1 × 20", h.stageOn(8)?.let(::label))
        assertEquals(2, h.stageIndex(15)) // week 3: lean forward and flex quads
        assertTrue(h.stageOn(15)!!.note.contains("quads"))
        assertEquals("2 × 20", h.stageOn(29)?.let(::label)) // days 29–30 belong to week 4
        assertEquals("2 × 20", h.stageOn(30)?.let(::label))
        assertNull(h.stageOn(31))
        assertFalse(h.finished(day(30)))
        assertTrue(h.finished(day(31)))
    }

    @Test fun stageStartsOnlyOnItsFirstDay() {
        val h = habit()
        assertTrue(h.isStageStart(1))
        assertFalse(h.isStageStart(2))
        assertTrue(h.isStageStart(15))
        assertFalse(h.isStageStart(16))
    }

    @Test fun holdStagesReadInSeconds() {
        assertEquals("3 × 30 s", label(Stage(days = 7, sets = 3, reps = 0, holdSec = 30)))
    }

    @Test fun clipFallsBackToEarlierStageThenItem() {
        val stages = listOf(Stage(7, 1, 10, clip = "a.mp4"), Stage(7, 1, 20), Stage(7, 1, 20, clip = "b.mp4"), Stage(9, 2, 20))
        val h = habit(stages)
        assertEquals("a.mp4", h.clipOn(1, "item.mp4"))
        assertEquals("a.mp4", h.clipOn(10, "item.mp4")) // week 2 inherits week 1's clip
        assertEquals("b.mp4", h.clipOn(16, "item.mp4"))
        assertEquals("b.mp4", h.clipOn(25, "item.mp4"))
        assertEquals("item.mp4", habit(listOf(Stage(7, 1, 10))).clipOn(3, "item.mp4"))
    }

    @Test fun encodeRoundTripKeepsAwkwardNotes() {
        val odd = listOf(Stage(7, 1, 10, note = "a | b; c, d\nnext line", clip = "c.mp4"), Stage(3, 2, 0, holdSec = 45))
        val all = listOf(habit(odd, done = setOf(day(1), day(3))).copy(remindMin = 480), habit().copy(id = "h2", ended = true))
        assertEquals(all, decodeHabits(encodeHabits(all)))
        assertEquals(emptyList<Habit>(), decodeHabits(null))
        assertEquals(listOf(habit()), decodeHabits("junk\n" + encodeHabits(listOf(habit()))))
    }

    @Test fun mergeKeepsNewestEditAndEveryDoneDay() {
        val mine = habit(done = setOf(day(1), day(2)), at = 5)
        val theirs = habit(done = setOf(day(3)), at = 9).copy(remindMin = 420)
        val m = mergeHabits(listOf(mine), listOf(theirs)).single()
        assertEquals(420, m.remindMin) // newer edit wins
        assertEquals(setOf(day(1), day(2), day(3)), m.done) // done days from both phones
    }

    @Test fun reminderSaysTodaysSetsAndStaysQuietWhenNotNeeded() {
        val h = habit().copy(remindMin = 480)
        assertEquals("1 × 20 today", reminderText(h, day(9)))
        assertEquals("1 × 20 today · New: lean forward and flex your quads too. Your knee's brake and shock absorber.", reminderText(h, day(15))) // a new stage says what changed
        assertNull(reminderText(h.copy(done = setOf(day(9))), day(9))) // already done
        assertNull(reminderText(h.copy(remindMin = null), day(9))) // reminder off
        assertNull(reminderText(h.copy(ended = true), day(9)))
        assertNull(reminderText(h, day(31))) // program over
    }

    @Test fun progressCountsDoneDaysInsideTheProgram() {
        val h = habit(done = setOf(day(1), day(2), day(40), start.minusDays(1)))
        assertEquals(2, h.doneCount)
    }
}
