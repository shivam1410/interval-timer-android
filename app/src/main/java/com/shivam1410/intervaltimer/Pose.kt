package com.shivam1410.intervaltimer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Built-in movement demos: a side-view figure (one arm, one leg) eased between a few key poses.
// Pure maths here, unit-tested in PoseTest; DemoUi.kt draws it. Coordinates are in a 160 × 165 box.

data class Pt(val x: Float, val y: Float)

enum class Joint { HEAD, NECK, SHOULDER, HAND, HIP, HEEL, TOE }

typealias Pose = Map<Joint, Pt>

/** A looping movement: key poses at times (ms), the first at 0; it eases back to the first pose at [loopMs]. */
data class Demo(val keys: List<Pair<Int, Pose>>, val loopMs: Int, val wall: Boolean = true)

const val DEMO_W = 160f
const val DEMO_H = 165f
const val FLOOR_Y = 152f
const val WALL_X = 30f
const val HEAD_R = 10f

/** Lines of the figure; the last one (heel → toe) is the moving part, drawn in the accent colour. */
val BONES = listOf(Joint.NECK to Joint.HIP, Joint.SHOULDER to Joint.HAND, Joint.HIP to Joint.HEEL, Joint.HEEL to Joint.TOE)

private const val FOOT = 22f

/** Toe point for a foot of [FOOT] length lifted [deg] degrees about the heel. */
private fun toe(heel: Pt, deg: Double) =
    Pt(heel.x + FOOT * cos(deg * PI / 180).toFloat(), heel.y - FOOT * sin(deg * PI / 180).toFloat())

/** Back, head and hips on the wall, straight leg ~18° to the heel about a foot out (the approved mockup). */
private val WALL_STAND: Pose = mapOf(
    Joint.HEAD to Pt(41f, 18f), Joint.NECK to Pt(38f, 29f), Joint.SHOULDER to Pt(37f, 40f), Joint.HAND to Pt(39f, 76f),
    Joint.HIP to Pt(35f, 82f), Joint.HEEL to Pt(58f, 150f),
)

/**
 * Weeks 3–4 of the tib raise program: butt still on the wall, same straight leg and heels, upper body
 * hinged ~25° forward from the hips (quads tight); the arm hangs straight down.
 */
private val LEAN_FORWARD: Pose = WALL_STAND + mapOf(
    Joint.HEAD to Pt(62f, 24f), Joint.NECK to Pt(57.4f, 34f), Joint.SHOULDER to Pt(52.7f, 43.9f), Joint.HAND to Pt(53.5f, 79.9f),
)

/** Toes down, short hold, lift ~30°, hold, lower: 1.8 s. */
private fun toeRaise(body: Pose): Demo {
    val down = body + (Joint.TOE to toe(body.getValue(Joint.HEEL), 0.0))
    val up = body + (Joint.TOE to toe(body.getValue(Joint.HEEL), 30.0))
    return Demo(listOf(0 to down, 270 to down, 810 to up, 1080 to up, 1620 to down), loopMs = 1800)
}

val DEMOS: Map<String, Demo> = mapOf(
    "tib-raise" to toeRaise(WALL_STAND),
    "tib-raise-lean" to toeRaise(LEAN_FORWARD),
)

/** The built-in demo an exercise's title asks for, or null (then nothing is drawn, never a placeholder). */
fun demoFor(title: String): String? = if (title.contains("tib", ignoreCase = true)) "tib-raise" else null

fun lerp(a: Pose, b: Pose, f: Float): Pose = a.mapValues { (j, p) ->
    val q = b.getValue(j)
    Pt(p.x + (q.x - p.x) * f, p.y + (q.y - p.y) * f)
}

/** Smooth start and stop between key poses. */
private fun ease(f: Float) = (1 - cos(f * PI).toFloat()) / 2

/** The figure [ms] into the loop. */
fun poseAt(d: Demo, ms: Long): Pose {
    val t = (ms % d.loopMs).toInt()
    val keys = d.keys + (d.loopMs to d.keys.first().second)
    val i = keys.indexOfLast { it.first <= t }
    val (t0, a) = keys[i]
    val (t1, b) = keys[i + 1]
    return lerp(a, b, ease((t - t0).toFloat() / (t1 - t0)))
}
