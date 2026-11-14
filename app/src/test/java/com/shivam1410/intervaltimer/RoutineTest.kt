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

    @Test fun aRoutineOpensAtTheFirstStepStillToDo() {
        val stages = sameEveryDay(30, 1, 10)
        fun h(id: String, done: Set<LocalDate> = emptySet(), ended: Boolean = false) =
            Habit(id, "", today.minusDays(2), stages, null, done, 1, ended, title = id)
        val steps = listOf(habitStep("a"), habitStep("b"), "Water", habitStep("c"))
        val routine = r().copy(steps = steps)
        val habits = listOf(h("a", done = setOf(today)), h("b"), h("c"))
        assertEquals(1, firstUndoneStep(routine, habits, today)) // a is done today, b isn't
        assertEquals(2, firstUndoneStep(routine, listOf(h("a", setOf(today)), h("b", setOf(today)), h("c")), today))
        assertEquals(0, firstUndoneStep(routine, habits, today.plusDays(1))) // a new day starts at the top again
        // A habit that's gone or ended is behind you too; a text step is never tracked, so it's always to do.
        assertEquals(2, firstUndoneStep(routine, listOf(h("a", setOf(today)), h("b", ended = true)), today))
        assertEquals(2, firstUndoneStep(r().copy(steps = listOf(habitStep("a"), habitStep("b"))), listOf(h("a", setOf(today)), h("b", setOf(today))), today))
    }

    @Test fun addingAStretchAfterTheRoutineIsFinishedMakesItToDoAgain() {
        val stages = sameEveryDay(30, 1, 10)
        fun h(id: String, done: Set<LocalDate> = emptySet(), ended: Boolean = false) =
            Habit(id, "", today.minusDays(2), stages, null, done, 1, ended, title = id)
        val finished = r(done = mapOf(today to 540)).copy(steps = listOf(habitStep("a"), "Water"))
        val allDone = listOf(h("a", done = setOf(today)))
        assertEquals(540, routineDoneToday(finished, allDone, today)) // every stretch done: the routine is done
        // A stretch added at 11:00 to a routine finished at 9:00 is still to do, so the routine is too.
        val withNew = finished.copy(steps = finished.steps + habitStep("b"))
        assertNull(routineDoneToday(withNew, allDone + h("b"), today))
        assertEquals(540, routineDoneToday(withNew, allDone + h("b", done = setOf(today)), today)) // then done
        // A habit that ended or was deleted can't be done, so it never holds the routine open.
        assertEquals(540, routineDoneToday(withNew, allDone + h("b", ended = true), today))
        assertEquals(540, routineDoneToday(withNew, allDone, today))
        assertNull(routineDoneToday(finished.copy(done = emptyMap()), allDone, today)) // never finished today
    }

    @Test fun encodeRoundTripKeepsAwkwardText() {
        val odd = Routine("x", "🚪", "Out | door; now", listOf("Keys, wallet", "Phone|charger", "Water; bottle"), null, mapOf(today to 600), 7, deleted = true)
        val all = listOf(odd, r())
        assertEquals(all, decodeRoutines(encodeRoutines(all)))
        assertEquals(emptyList<Routine>(), decodeRoutines(null))
    }

    @Test fun aRoutineCanCarryHowLongItShouldTake() {
        val timed = r().copy(minutes = 15)
        assertEquals(listOf(timed), decodeRoutines(encodeRoutines(listOf(timed)))) // the length survives a round trip
        assertNull(r().minutes) // no timer unless you ask for one
        // Routines stored before lengths existed have 8 fields and read back with no timer.
        val old = encodeRoutines(listOf(r())).substringBeforeLast("|")
        assertEquals(8, old.split("|").size)
        assertEquals(listOf(r()), decodeRoutines(old))
    }

    @Test fun aTimedRoutineIsAnActivityOfItsOwnLength() {
        val a = r().copy(minutes = 20).toActivity()
        assertEquals("routine:m", a.id)
        assertEquals("Morning", a.name)
        assertEquals(20, a.quickMin)
        assertEquals(15, r().toActivity().quickMin) // no length set: a quick timer of 15 min
    }

    @Test fun remindersAtTheirTimeUnlessDoneOrDeleted() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val morning = r("m").copy(remindMin = 7 * 60 + 30)
        val evening = r("e").copy(remindMin = 22 * 60)
        val gone = r("g").copy(remindMin = 9 * 60, deleted = true)
        val noTime = r("n").copy(remindMin = null)
        val all = listOf(morning, evening, gone, noTime)
        val eightAm = java.time.Instant.parse("2026-11-12T08:00:00Z").toEpochMilli()
        val tenPm = java.time.Instant.parse("2026-11-12T22:00:00Z").toEpochMilli()
        assertEquals(tenPm, nextRoutineAt(all, eightAm, utc)) // 07:30 passed, 09:00 is deleted
        assertEquals(listOf("e"), routineDue(all, tenPm, utc).map { it.id })
        val doneTonight = evening.copy(done = mapOf(today to 21 * 60))
        assertEquals(emptyList<Routine>(), routineDue(listOf(doneTonight), tenPm, utc)) // already did it: no nagging
        assertNull(nextRoutineAt(listOf(gone, noTime), eightAm, utc))
    }

    @Test fun typedTextIsCutWithoutSplittingAnEmoji() {
        assertEquals("ii🌙", "ii🌙x".clip(3)) // 3 characters as you see them, the moon kept whole
        assertEquals("ab", "ab".clip(4))
        assertEquals("🌙🌙", "🌙🌙🌙".clip(2))
    }

    @Test fun mergeKeepsNewestEditAndEveryFinish() {
        val mine = r(at = 5, done = mapOf(today to 494))
        val theirs = r(at = 9, done = mapOf(today.minusDays(1) to 500)).copy(name = "Mornings")
        val m = mergeRoutines(listOf(mine), listOf(theirs)).single()
        assertEquals("Mornings", m.name)
        assertEquals(mapOf(today to 494, today.minusDays(1) to 500), m.done)
    }

    @Test fun aStepCanBeOneOfYourHabits() {
        val r = r().copy(steps = listOf(habitStep("h1"), "Drink water"))
        assertEquals(listOf(HabitStep("h1"), TextStep("Drink water")), routineSteps(r))
        val gone = r("g").copy(steps = listOf(habitStep("h2")), deleted = true)
        assertEquals(setOf("h1"), habitsInRoutines(listOf(r, gone)))
    }

    @Test fun anUndoneRoutineNudgesThirtyMinutesLater() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val ten = r("t").copy(remindMin = 10 * 60)
        val at10 = java.time.Instant.parse("2026-11-12T10:00:00Z").toEpochMilli()
        val at1030 = java.time.Instant.parse("2026-11-12T10:30:00Z").toEpochMilli()
        assertEquals(listOf("t"), routineDue(listOf(ten), at10, utc).map { it.id })
        assertEquals(emptyList<Routine>(), routineNudgeDue(listOf(ten), at10, utc))
        assertEquals(at1030, nextRoutineAt(listOf(ten), at10, utc)) // after the 10:00 alarm, the nudge is next
        assertEquals(listOf("t"), routineNudgeDue(listOf(ten), at1030, utc).map { it.id })
        val done = ten.copy(done = mapOf(today to 10 * 60 + 12))
        assertEquals(emptyList<Routine>(), routineNudgeDue(listOf(done), at1030, utc)) // finished: no nudge
        assertEquals(java.time.Instant.parse("2026-11-13T10:00:00Z").toEpochMilli(), nextRoutineAt(listOf(done), at10, utc))
    }

    @Test fun settingsGroupsRoutinesAndHabitsByTime() {
        fun h(id: String, remind: Int?, ended: Boolean = false) = Habit(id, "", today, TIB_RAISES, remind, emptySet(), 1, ended = ended, title = id)
        val ten = r("ten").copy(remindMin = 600, steps = listOf(habitStep("calf"), "Water"))
        val eve = r("eve").copy(remindMin = 1080)
        val loose = r("loose").copy(remindMin = null)
        val habits = listOf(h("calf", 600), h("tib", 780), h("free", null), h("old", 600, ended = true))
        val slots = daySlots(habits, listOf(eve, ten, loose, r("gone").copy(deleted = true)))
        assertEquals(listOf(600, 780, 1080, null), slots.map { it.min }) // by time, Anytime last
        assertEquals(listOf("ten"), slots[0].routines.map { it.id })
        assertEquals(emptyList<String>(), slots[0].habits.map { it.id }) // calf lives inside the 10:00 routine
        assertEquals(listOf("tib"), slots[1].habits.map { it.id })
        assertEquals(listOf("loose") to listOf("free"), slots[3].routines.map { it.id } to slots[3].habits.map { it.id })
    }

    @Test fun routineRecordCountsTheStreakAndTheLastThirtyDays() {
        val days = listOf(0, 1, 2, 4, 40).associate { today.minusDays(it.toLong()) to 600 }
        assertEquals(3, routineStreak(r(done = days), today)) // today, yesterday, the day before; then a gap
        assertEquals(2, routineStreak(r(done = days - today), today)) // not done yet today: the run up to yesterday still counts
        assertEquals(0, routineStreak(r(), today))
        assertEquals(4, doneInLast(r(done = days), today, 30)) // day 40 is outside
    }
}
