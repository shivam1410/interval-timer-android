package com.shivam1410.intervaltimer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Built-in movement demos: a side-view figure (one arm, one leg) eased between a few key poses.
// Pure maths here, unit-tested in PoseTest; DemoUi.kt draws it. Coordinates are in a 160 × 165 box.

data class Pt(val x: Float, val y: Float)

enum class Joint { HEAD, NECK, SHOULDER, HAND, HIP, KNEE, HEEL, TOE }

typealias Pose = Map<Joint, Pt>

/** A looping movement: key poses at times (ms), the first at 0; it eases back to the first pose at [loopMs]. [bones] are drawn, the last in the accent colour. */
data class Demo(val keys: List<Pair<Int, Pose>>, val loopMs: Int, val wall: Boolean = true, val bones: List<Pair<Joint, Joint>> = BONES)

const val DEMO_W = 160f
const val DEMO_H = 165f
const val FLOOR_Y = 152f
const val WALL_X = 30f
const val HEAD_R = 10f

/** Default lines of the figure; the last one (heel → toe) is the moving part, drawn in the accent colour. */
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

/** Standing tall, facing right, no wall (a little shorter than the wall figure so a calf raise stays in the box). */
private val STAND: Pose = mapOf(
    Joint.HEAD to Pt(78f, 30f), Joint.NECK to Pt(78f, 41f), Joint.SHOULDER to Pt(78f, 51f), Joint.HAND to Pt(79f, 86f),
    Joint.HIP to Pt(78f, 90f), Joint.KNEE to Pt(77f, 120f), Joint.HEEL to Pt(76f, 150f), Joint.TOE to Pt(98f, 150f),
)

/** Calf raise: the foot pivots ~30° at the ball (toe stays down), heel up 11, the whole straight body rises with it. */
private val CALF_UP: Pose = STAND.mapValues { (j, p) ->
    when (j) {
        Joint.TOE -> p
        Joint.HEEL -> Pt(78.95f, 139f)
        Joint.KNEE -> Pt(79.95f, 109f)
        Joint.HIP -> Pt(80.95f, 79f)
        else -> Pt(p.x + 2.95f, p.y - 11f)
    }
}

/** Squat bottom: heels and toes down, knee over the toes, thigh ~15° off parallel, hips back, torso ~40° forward, arms out front. */
private val SQUAT_DOWN: Pose = STAND + mapOf(
    Joint.KNEE to Pt(98f, 129.6f), Joint.HIP to Pt(69f, 121.8f), Joint.NECK to Pt(100.5f, 84.3f),
    Joint.HEAD to Pt(107.6f, 75.8f), Joint.SHOULDER to Pt(94.1f, 91.9f), Joint.HAND to Pt(129.1f, 91.9f),
)

/** Shoulder wall push: facing the wall (on the left), arm straight out at shoulder height, palm flat on it. */
private val PUSH_REST: Pose = mapOf(
    Joint.HEAD to Pt(64f, 30f), Joint.NECK to Pt(66f, 41f), Joint.SHOULDER to Pt(65f, 51f), Joint.HAND to Pt(30.5f, 51f),
    Joint.HIP to Pt(68f, 90f), Joint.HEEL to Pt(72f, 150f), Joint.TOE to Pt(50f, 150f),
)

/** Pressing: the body leans a touch into the hand; the hand itself doesn't move (isometric). */
private val PUSH_PRESS: Pose = PUSH_REST + mapOf(
    Joint.HEAD to Pt(62.8f, 30f), Joint.NECK to Pt(64.8f, 41f), Joint.SHOULDER to Pt(63.8f, 51.3f), Joint.HIP to Pt(67.3f, 90f),
)

/**
 * Supraspinatus isometric, seen from the front: wall on your side, arm down by the side, back of the hand
 * against the wall. In this view TOE is the second foot (both legs from the hip).
 */
private val SUPRA_REST: Pose = mapOf(
    Joint.HEAD to Pt(66f, 30f), Joint.NECK to Pt(66f, 41f), Joint.SHOULDER to Pt(50f, 48f), Joint.HAND to Pt(31f, 84f),
    Joint.HIP to Pt(66f, 92f), Joint.HEEL to Pt(58f, 150f), Joint.TOE to Pt(74f, 150f),
)

/** Pressing out as if lifting the arm sideways: the hand stays, the body shifts a touch away from the wall. */
private val SUPRA_PRESS: Pose = SUPRA_REST + mapOf(
    Joint.HEAD to Pt(68.2f, 30f), Joint.NECK to Pt(68f, 41f), Joint.SHOULDER to Pt(51.5f, 48f), Joint.HIP to Pt(67f, 92f),
)

/** Wall sit, part way down: back on the wall, knee in front of the heel. */
private val WALL_SIT_HIGH: Pose = mapOf(
    Joint.HEAD to Pt(40f, 29.4f), Joint.NECK to Pt(37f, 40.4f), Joint.SHOULDER to Pt(36f, 51.4f), Joint.HAND to Pt(46f, 85f),
    Joint.HIP to Pt(37f, 93.4f), Joint.KNEE to Pt(63.7f, 119f), Joint.HEEL to Pt(75f, 150f), Joint.TOE to Pt(97f, 150f),
)

/** Wall sit, held: thighs level, knees over the ankles, hands resting on the thighs. */
private val WALL_SIT_DOWN: Pose = WALL_SIT_HIGH + mapOf(
    Joint.HEAD to Pt(40f, 53f), Joint.NECK to Pt(37f, 64f), Joint.SHOULDER to Pt(36f, 75f), Joint.HAND to Pt(52f, 107f),
    Joint.HIP to Pt(38f, 117f), Joint.KNEE to Pt(75f, 117f),
)

/** Glute bridge, lying on the back (head left), knees bent, feet flat. */
private val BRIDGE_DOWN: Pose = mapOf(
    Joint.HEAD to Pt(26f, 142f), Joint.NECK to Pt(37f, 145f), Joint.SHOULDER to Pt(44f, 146f), Joint.HAND to Pt(78f, 148f),
    Joint.HIP to Pt(86f, 146f), Joint.KNEE to Pt(110f, 122f), Joint.HEEL to Pt(122f, 150f), Joint.TOE to Pt(140f, 150f),
)

/** Hips up into a straight line from shoulders to knees; shoulders and feet stay down. */
private val BRIDGE_UP: Pose = BRIDGE_DOWN + mapOf(Joint.HIP to Pt(84f, 128f), Joint.KNEE to Pt(117f, 120f))

private val LEG_BONES = listOf(Joint.NECK to Joint.HIP, Joint.SHOULDER to Joint.HAND, Joint.HIP to Joint.KNEE, Joint.KNEE to Joint.HEEL, Joint.HEEL to Joint.TOE)

val DEMOS: Map<String, Demo> = mapOf(
    "tib-raise" to toeRaise(WALL_STAND),
    "tib-raise-lean" to toeRaise(LEAN_FORWARD),
    "calf-raise" to Demo(
        listOf(0 to STAND, 300 to STAND, 900 to CALF_UP, 1300 to CALF_UP, 1900 to STAND), loopMs = 2000, wall = false,
        bones = LEG_BONES, // ends with heel → toe: the foot is the accent
    ),
    "squat" to Demo(
        listOf(0 to STAND, 300 to STAND, 1100 to SQUAT_DOWN, 1500 to SQUAT_DOWN, 2300 to STAND), loopMs = 2400, wall = false,
        bones = LEG_BONES - (Joint.HIP to Joint.KNEE) + (Joint.HIP to Joint.KNEE), // the thigh is the accent
    ),
    "wall-push" to Demo(
        listOf(0 to PUSH_REST, 500 to PUSH_REST, 1100 to PUSH_PRESS, 1700 to PUSH_PRESS, 2300 to PUSH_REST), loopMs = 2400,
        bones = listOf(Joint.NECK to Joint.HIP, Joint.HIP to Joint.HEEL, Joint.HEEL to Joint.TOE, Joint.SHOULDER to Joint.HAND),
    ),
    "supraspinatus" to Demo(
        listOf(0 to SUPRA_REST, 500 to SUPRA_REST, 1100 to SUPRA_PRESS, 1900 to SUPRA_PRESS, 2500 to SUPRA_REST), loopMs = 2600,
        bones = listOf(Joint.NECK to Joint.HIP, Joint.NECK to Joint.SHOULDER, Joint.HIP to Joint.HEEL, Joint.HIP to Joint.TOE, Joint.SHOULDER to Joint.HAND),
    ),
    "wall-sit" to Demo(
        listOf(0 to WALL_SIT_HIGH, 300 to WALL_SIT_HIGH, 1200 to WALL_SIT_DOWN, 3200 to WALL_SIT_DOWN, 4000 to WALL_SIT_HIGH), loopMs = 4300,
        bones = LEG_BONES - (Joint.HIP to Joint.KNEE) + (Joint.HIP to Joint.KNEE), // the thigh is the accent
    ),
    "glute-bridge" to Demo(
        listOf(0 to BRIDGE_DOWN, 300 to BRIDGE_DOWN, 1000 to BRIDGE_UP, 1500 to BRIDGE_UP, 2200 to BRIDGE_DOWN), loopMs = 2400, wall = false,
        bones = listOf(Joint.NECK to Joint.SHOULDER, Joint.SHOULDER to Joint.HAND, Joint.HIP to Joint.KNEE, Joint.KNEE to Joint.HEEL, Joint.HEEL to Joint.TOE, Joint.SHOULDER to Joint.HIP),
    ),
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
