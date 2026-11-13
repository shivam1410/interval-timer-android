package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CatalogTest {
    private fun h(stages: List<Stage>) = Habit("x", "", LocalDate.of(2026, 11, 1), stages, null, emptySet(), 1)
    private fun entry(id: String) = CATALOG.first { it.id == id }
    private fun on(plan: List<Stage>, day: Int) = h(plan).stageOn(day)?.let(::label)

    @Test fun everyPlanIsThirtyDaysAndAddsASetEachWeek() {
        CATALOG.forEach { e -> Level.entries.forEach { lv -> assertEquals("${e.id} $lv", 30, planFor(e, lv).sumOf { it.days }) } }
        val tib = entry("tib-raises")
        assertEquals("1 × 10", on(planFor(tib, Level.BEGINNER), 1))
        assertEquals("2 × 10", on(planFor(tib, Level.BEGINNER), 8))
        assertEquals("3 × 10", on(planFor(tib, Level.BEGINNER), 15))
        assertEquals("4 × 10", on(planFor(tib, Level.BEGINNER), 30)) // days 29–30 stay on week 4
        assertEquals("2 × 15", on(planFor(tib, Level.INTERMEDIATE), 1))
        assertEquals("5 × 15", on(planFor(tib, Level.INTERMEDIATE), 22))
        assertEquals("3 × 20", on(planFor(tib, Level.ADVANCED), 1))
    }

    @Test fun holdsCountSeconds() {
        val push = entry("wall-push")
        assertEquals("1 × 20 s", on(planFor(push, Level.BEGINNER), 1))
        assertEquals("2 × 30 s", on(planFor(push, Level.INTERMEDIATE), 1))
        assertEquals("3 × 45 s", on(planFor(push, Level.ADVANCED), 1))
        assertEquals("6 × 45 s", on(planFor(push, Level.ADVANCED), 22))
    }

    @Test fun supraspinatusIsSixMovesOfFivePressesOfFiveSecondsEveryDay() {
        val plan = planFor(entry("supraspinatus"), Level.BEGINNER)
        assertEquals("6 × 5 · 5 s hold", on(plan, 1)) // one set per move
        assertEquals("6 × 5 · 5 s hold", on(plan, 30)) // a physio dose: it doesn't grow each week
        assertEquals("6 × 5 · 5 s hold", on(planFor(entry("supraspinatus"), Level.ADVANCED), 22))
        assertEquals("supraspinatus-6", plan.first().demo)
    }

    @Test fun tibRaisesLeanFromWeekThreeAndEveryMoveHasAnAnimation() {
        val plan = planFor(entry("tib-raises"), Level.BEGINNER)
        assertEquals("tib-raise", h(plan).demoOn(8, ""))
        assertEquals("tib-raise-lean", h(plan).demoOn(15, ""))
        CATALOG.flatMap { e -> planFor(e, Level.BEGINNER).map { it.demo } }.toSet().forEach { assertTrue("animation for $it", DEMOS.containsKey(it) || CIRCUITS.containsKey(it)) }
        assertEquals(listOf("tib-raises", "calf-raises", "wall-push", "supraspinatus", "wall-sit", "glute-bridge", "squats"), CATALOG.map { it.id })
    }
}
