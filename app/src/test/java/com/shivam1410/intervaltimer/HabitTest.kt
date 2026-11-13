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

    @Test fun stageDemoRoundTripsAndOldStagesStillRead() {
        val h = habit(listOf(Stage(7, 1, 10, demo = "tib-raise-lean")))
        assertEquals(h, decodeHabits(encodeHabits(listOf(h))).single())
        val old = "h1|item1|2026-11-01|-1|1|0||7,1,10,0,note," // 6 stage fields, before demos
        assertEquals("", decodeHabits(old).single().stages.single().demo)
        assertEquals("tib-raise-lean", TIB_RAISES[2].demo) // week 3 leans forward
        assertEquals("tib-raise-lean", habit().demoOn(16, "Tib raises"))
        assertEquals("tib-raise", habit().demoOn(3, "Tib raises"))
        assertNull(habit(listOf(Stage(30, 1, 10))).demoOn(3, "Wall push"))
    }

    @Test fun sameEveryDayTemplate() {
        val h = habit(sameEveryDay(30, sets = 2, reps = 15))
        assertEquals(30, h.totalDays)
        assertEquals("2 × 15", h.stageOn(1)?.let(::label))
        assertEquals("2 × 15", h.stageOn(30)?.let(::label))
        assertEquals("3 × 30 s", label(sameEveryDay(14, sets = 3, reps = 0, holdSec = 30).single()))
    }

    @Test fun growEachWeekTemplate() {
        val h = habit(growWeekly(30, sets = 1, start = 10, add = 10))
        assertEquals(30, h.totalDays)
        assertEquals("1 × 10", h.stageOn(1)?.let(::label))
        assertEquals("1 × 10", h.stageOn(7)?.let(::label))
        assertEquals("1 × 20", h.stageOn(8)?.let(::label))
        assertEquals("1 × 50", h.stageOn(29)?.let(::label)) // week 5: days 29–30
        assertEquals(listOf(7, 7, 7, 7, 2), h.stages.map { it.days })
        assertEquals("1 × 40 s", growWeekly(14, sets = 1, start = 30, add = 10, holdSec = true).last().let(::label))
    }

    @Test fun calendarEditOnlyThisDay() {
        val h = habit(done = setOf(day(2)))
        val e = h.copy(stages = setFrom(h, 10, sets = 1, reps = 15, holdSec = 0, onlyThisDay = true))
        assertEquals("1 × 15", e.stageOn(10)?.let(::label))
        assertEquals("1 × 20", e.stageOn(9)?.let(::label))
        assertEquals("1 × 20", e.stageOn(11)?.let(::label)) // the next day is as before
        assertEquals(30, e.totalDays)
        assertFalse(e.isStageStart(11)) // the rest of week 2 isn't a "new stage" again
        assertEquals(h.stageOn(11)?.note, e.stageOn(11)?.note)
    }

    @Test fun calendarEditFromThisDayOn() {
        val h = habit()
        val e = h.copy(stages = setFrom(h, 15, sets = 2, reps = 25, holdSec = 0, onlyThisDay = false))
        assertEquals("1 × 20", e.stageOn(14)?.let(::label))
        listOf(15, 22, 30).forEach { assertEquals("2 × 25", e.stageOn(it)?.let(::label)) }
        assertEquals(30, e.totalDays)
        assertEquals("tib-raise-lean", e.demoOn(15, "Tib raises")) // the day keeps its movement
    }

    @Test fun repeatedEditsKeepTheStageListSmall() {
        var h = habit()
        repeat(5) { h = h.copy(stages = setFrom(h, 10, 1, 15 + it, 0, onlyThisDay = true)) }
        h = h.copy(stages = setFrom(h, 10, 1, 20, 0, onlyThisDay = true)) // back to the week's target
        assertEquals(TIB_RAISES, h.stages) // merges back into the original program
    }

    @Test fun clipSyncUploadsDownloadsAndDropsOrphans() {
        val items = listOf(
            LibraryItem("a", "Tib", "", "", 10, 1, clip = "clip-a.mp4"),
            LibraryItem("d", "Gone", "", "", 10, 1, deleted = true, clip = "clip-d.mp4"),
        )
        val habits = listOf(habit(listOf(Stage(7, 1, 10, clip = "clip-s.mp4"))))
        val wanted = clipsWanted(items, habits)
        assertEquals(setOf("clip-a.mp4", "clip-s.mp4"), wanted)
        val plan = clipSync(wanted, local = setOf("clip-a.mp4", "clip-old.gif"), drive = setOf("clip-s.mp4", "clip-d.mp4"))
        assertEquals(setOf("clip-a.mp4"), plan.upload) // only this phone has it
        assertEquals(setOf("clip-s.mp4"), plan.download) // only Drive has it
        assertEquals(setOf("clip-d.mp4"), plan.deleteRemote) // its item was deleted
        // clip-old.gif (local, unused) is neither uploaded nor deleted: it may be a pick not saved yet.
    }

    @Test fun countsRepsSetBySetWithRestBetween() {
        val s = Stage(7, sets = 2, reps = 3)
        assertEquals(Count(set = 1, done = 0, of = 3, sets = 2), countAt(0, s, repMs = 1000, restMs = 5000))
        assertEquals(2, countAt(2500, s, 1000, 5000).done) // a rep counts when its movement ends
        val rest = countAt(3000, s, 1000, 5000)
        assertTrue(rest.resting)
        assertEquals(5, rest.restLeftSec)
        assertEquals(3, rest.done) // set 1 shows complete while resting
        assertEquals(Count(set = 2, done = 0, of = 3, sets = 2), countAt(8000, s, 1000, 5000))
        assertTrue(countAt(11_000, s, 1000, 5000).finished)
        assertFalse(countAt(10_999, s, 1000, 5000).finished)
    }

    @Test fun repsHeldForSecondsCountRepsWithAHoldThenRelax() {
        assertEquals(1_000, RELAX_MS) // a second between presses
        val s = Stage(7, sets = 1, reps = 20, holdSec = 5)
        assertEquals("1 × 20 · 5 s hold", label(s))
        val start = countAt(0, s, repMs = 1800, restMs = 30_000)
        assertEquals(Count(set = 1, done = 0, of = 20, sets = 1, holdLeftSec = 5), start)
        assertEquals(3, countAt(2_400, s, 1800, 30_000).holdLeftSec) // 2.4 s in: 3 s left to hold
        val relax = countAt(5_400, s, 1800, 30_000)
        assertEquals(0, relax.holdLeftSec) // relaxing between reps
        assertEquals(0, relax.done)
        assertEquals(1, countAt(5_000 + RELAX_MS.toLong(), s, 1800, 30_000).done) // rep 1 counts after its relax
        assertTrue(countAt(20L * (5_000 + RELAX_MS), s, 1800, 30_000).finished)
        assertFalse(countAt(20L * (5_000 + RELAX_MS) - 1, s, 1800, 30_000).finished)
    }

    @Test fun holdsCountSeconds() {
        val s = Stage(7, sets = 1, reps = 0, holdSec = 30)
        assertEquals(12, countAt(12_400, s, repMs = 1800, restMs = 30_000).done)
        assertEquals(30, countAt(12_400, s, 1800, 30_000).of)
        assertTrue(countAt(30_000, s, 1800, 30_000).finished)
    }

    @Test fun habitStandsAloneWithItsOwnName() {
        val own = habit().copy(itemId = "", title = "Stairs | 3 floors")
        assertEquals(own, decodeHabits(encodeHabits(listOf(own))).single())
        val old = "h1|item1|2026-11-01|-1|1|0||7,1,10,0,note," // 8 fields: before habits had names
        assertEquals("", decodeHabits(old).single().title)
        val items = listOf(LibraryItem("item1", "Tib raises", "", "", 10, 1))
        assertEquals("Stairs | 3 floors", habitTitle(own, items))
        assertEquals("Tib raises", habitTitle(habit(), items)) // older habits take the linked exercise's name
        assertEquals("Habit", habitTitle(habit().copy(itemId = "gone"), items))
    }

    @Test fun severalHabitsShareOneAlarmAtTheNextReminder() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val tib = habit().copy(id = "a", remindMin = 8 * 60)
        val stairs = habit().copy(id = "b", remindMin = 18 * 60)
        val off = habit().copy(id = "c", remindMin = null)
        val ended = habit().copy(id = "d", remindMin = 7 * 60, ended = true)
        val all = listOf(tib, stairs, off, ended)
        val nineAm = java.time.Instant.parse("2026-11-12T09:00:00Z").toEpochMilli()
        val sixPm = java.time.Instant.parse("2026-11-12T18:00:00Z").toEpochMilli()
        assertEquals(sixPm, nextReminderAt(all, nineAm, utc)) // 08:00 passed; 07:00 belongs to an ended habit
        assertEquals(listOf("b"), dueAt(all, sixPm, utc).map { it.id })
        assertNull(nextReminderAt(listOf(off, ended), nineAm, utc)) // nothing to remind: no alarm
        val both = listOf(tib, stairs.copy(remindMin = 8 * 60))
        val eightAm = java.time.Instant.parse("2026-11-13T08:00:00Z").toEpochMilli()
        assertEquals(listOf("a", "b"), dueAt(both, eightAm, utc).map { it.id }) // same time: both remind
    }

    @Test fun progressCountsDoneDaysInsideTheProgram() {
        val h = habit(done = setOf(day(1), day(2), day(40), start.minusDays(1)))
        assertEquals(2, h.doneCount)
    }

    @Test fun deckPutsTodaysUndoneHabitsOnTopAndDoneOnesUnder() {
        val a = habit().copy(id = "a", done = setOf(day(3)))
        val b = habit().copy(id = "b")
        val c = habit().copy(id = "c")
        assertEquals(listOf("b", "c", "a"), habitDeck(listOf(a, b, c), day(3)).map { it.id })
        assertEquals(listOf("a", "b", "c"), habitDeck(listOf(a, b, c), day(4)).map { it.id }) // new day: all back on
    }
}
