package com.shivam1410.intervaltimer

// The habit catalog: fixed 30-day plans you pick from, at three levels. Pure, unit-tested in CatalogTest.

enum class Level(val label: String, val reps: Int, val holdSec: Int, val sets: Int) {
    BEGINNER("Beginner", 10, 20, 1),
    INTERMEDIATE("Intermediate", 15, 30, 2),
    ADVANCED("Advanced", 20, 45, 3),
}

/** One exercise in the catalog: [demos] per week (the last repeats), [notes] shown when each week starts. */
data class CatalogEntry(
    val id: String,
    val name: String,
    val blurb: String,
    val hold: Boolean,
    val demos: List<String>,
    val notes: List<String> = emptyList(),
    val reps: Int? = null, // a fixed rep count instead of the level's
    val holdEach: Int = 0, // each rep held this many seconds
    val grow: Boolean = true, // one more set each week; off for a physio dose
    val sets: Int? = null, // a fixed set count instead of the level's (a circuit: one set per move)
)

val CATALOG = listOf(
    CatalogEntry(
        "tib-raises", "Tib raises", "Back against the wall, toes up and down. Stronger shins, kinder knees.", hold = false,
        demos = listOf("tib-raise", "tib-raise", "tib-raise-lean", "tib-raise-lean"),
        notes = TIB_RAISES.map { it.note },
    ),
    CatalogEntry(
        "calf-raises", "Calf raises", "Stand tall, rise onto your toes, lower slowly.", hold = false,
        demos = listOf("calf-raise"), notes = listOf("Rise onto the balls of your feet, pause, lower slowly. Hold a wall for balance if you need to."),
    ),
    CatalogEntry(
        "wall-push", "Shoulder wall push", "Press one hand into the wall and hold. No movement, just effort.", hold = true,
        demos = listOf("wall-push"), notes = listOf("Face the wall, arm straight at shoulder height, palm flat. Press steadily, keep the shoulder down, breathe."),
    ),
    CatalogEntry(
        "supraspinatus", "Supraspinatus · 6 presses", "Six isometric presses in one: arm at 90° in and out, by the wall forward and back, arm at 90° up and down. 5 presses of 5 s each.", hold = false,
        demos = listOf("supraspinatus-6"), reps = 5, holdEach = 5, grow = false, sets = 6,
        notes = listOf("Press against a wall or door frame; the hand doesn't move. Gentle, no pain, shoulder down, breathe. 10 s between moves."),
    ),
    CatalogEntry(
        "wall-sit", "Wall sit", "Back on the wall, slide down until your thighs are level, and hold.", hold = true,
        demos = listOf("wall-sit"), notes = listOf("Feet a step out, knees over the ankles, back flat on the wall. Go only as low as your knees allow."),
    ),
    CatalogEntry(
        "glute-bridge", "Glute bridge", "On your back, knees bent, lift the hips and squeeze.", hold = false,
        demos = listOf("glute-bridge"), notes = listOf("Feet flat, hip-width. Push through the heels, lift until shoulders, hips and knees line up, pause, lower slowly."),
    ),
    CatalogEntry(
        "squats", "Squats", "Hips back and down, heels flat, chest up.", hold = false,
        demos = listOf("squat"), notes = listOf("Feet shoulder-width, sit the hips back, knees over toes, heels down. Stand up through the heels."),
    ),
)

/** A catalog plan: four weeks (7, 7, 7, 9 days), the level's reps (or seconds), one more set each week unless [CatalogEntry.grow] is off. */
fun planFor(e: CatalogEntry, level: Level): List<Stage> =
    listOf(7, 7, 7, 9).mapIndexed { week, days ->
        Stage(
            days, e.sets ?: (level.sets + if (e.grow) week else 0), if (e.hold) 0 else e.reps ?: level.reps, if (e.hold) level.holdSec else e.holdEach,
            note = e.notes.getOrNull(week).orEmpty(), demo = e.demos.getOrElse(week) { e.demos.last() },
        )
    }
