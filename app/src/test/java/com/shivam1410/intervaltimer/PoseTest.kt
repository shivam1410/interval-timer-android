package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

class PoseTest {
    private val tib = DEMOS.getValue("tib-raise")
    private fun p(pose: Pose, joint: Joint) = pose.getValue(joint)

    @Test fun holdsAtTheBottomThenTheTop() {
        val down = poseAt(tib, 0)
        assertEquals(down, poseAt(tib, 200)) // short hold before lifting
        val up = poseAt(tib, 900)
        assertTrue("toes are up mid-loop", p(up, Joint.TOE).y < p(down, Joint.TOE).y - 5)
        assertEquals(down, poseAt(tib, tib.loopMs.toLong())) // loops back to the start
        assertEquals(poseAt(tib, 300), poseAt(tib, tib.loopMs + 300L))
    }

    @Test fun heelStaysDownAndTheFootPivotsAboutThirtyDegrees() {
        val down = poseAt(tib, 0)
        val up = poseAt(tib, 900)
        assertEquals(p(down, Joint.HEEL), p(up, Joint.HEEL))
        val len = { q: Pose -> hypot(p(q, Joint.TOE).x - p(q, Joint.HEEL).x, p(q, Joint.TOE).y - p(q, Joint.HEEL).y) }
        assertEquals(len(down), len(up), 0.5f) // the foot rotates, it doesn't stretch
        val angle = Math.toDegrees(atan2((p(up, Joint.HEEL).y - p(up, Joint.TOE).y).toDouble(), (p(up, Joint.TOE).x - p(up, Joint.HEEL).x).toDouble()))
        assertEquals(30.0, angle, 2.0)
    }

    @Test fun halfwayIsBetweenTheTwoPoses() {
        val a = mapOf(Joint.TOE to Pt(0f, 0f))
        val b = mapOf(Joint.TOE to Pt(10f, -20f))
        assertEquals(Pt(5f, -10f), lerp(a, b, 0.5f).getValue(Joint.TOE))
    }

    @Test fun legIsStraightAndLeansAboutEighteenDegrees() {
        val down = poseAt(tib, 0)
        val dx = p(down, Joint.HEEL).x - p(down, Joint.HIP).x
        val dy = p(down, Joint.HEEL).y - p(down, Joint.HIP).y
        assertEquals(18.0, Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())), 3.0)
    }

    @Test fun leanKeepsHipsOnTheWallAndHingesTheUpperBody() {
        val lean = poseAt(DEMOS.getValue("tib-raise-lean"), 0)
        val wall = poseAt(tib, 0)
        assertEquals(p(wall, Joint.HIP), p(lean, Joint.HIP)) // butt stays on the wall
        assertEquals(p(wall, Joint.HEEL), p(lean, Joint.HEEL)) // same feet, same straight leg
        val dx = p(lean, Joint.NECK).x - p(lean, Joint.HIP).x
        val dy = p(lean, Joint.HIP).y - p(lean, Joint.NECK).y
        assertEquals(25.0, Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())), 3.0) // torso tips forward from the hip
        assertTrue("head clear of the wall", p(lean, Joint.HEAD).x - HEAD_R > WALL_X + 10)
    }

    private fun len(q: Pose, a: Joint, b: Joint) = hypot(p(q, a).x - p(q, b).x, p(q, a).y - p(q, b).y)

    @Test fun calfRaisePivotsAtTheToesAndLiftsTheHeel() {
        val d = DEMOS.getValue("calf-raise")
        val down = poseAt(d, 0)
        val up = poseAt(d, d.keys.first { (_, q) -> q.getValue(Joint.HEEL).y < down.getValue(Joint.HEEL).y }.first.toLong())
        assertEquals(p(down, Joint.TOE), p(up, Joint.TOE)) // the ball of the foot stays put
        val lift = p(down, Joint.HEEL).y - p(up, Joint.HEEL).y
        assertTrue("heel lifts 8–14, was $lift", lift in 8f..14f)
        assertEquals(len(down, Joint.HIP, Joint.HEEL), len(up, Joint.HIP, Joint.HEEL), 0.5f) // straight leg, same length
        assertTrue("whole body rises", p(up, Joint.HEAD).y < p(down, Joint.HEAD).y - 6)
    }

    @Test fun squatKeepsHeelsDownKneesOverToesAndThighsNearParallel() {
        val d = DEMOS.getValue("squat")
        val top = poseAt(d, 0)
        val bottom = d.keys.map { it.second }.maxBy { it.getValue(Joint.HIP).y }
        assertEquals(p(top, Joint.HEEL), p(bottom, Joint.HEEL))
        assertEquals(p(top, Joint.TOE), p(bottom, Joint.TOE))
        assertEquals(p(bottom, Joint.TOE).x, p(bottom, Joint.KNEE).x, 4f) // knees over the toes, not past them
        assertTrue("thigh near parallel", kotlin.math.abs(p(bottom, Joint.HIP).y - p(bottom, Joint.KNEE).y) < 10)
        assertTrue("hips behind the heels", p(bottom, Joint.HIP).x < p(bottom, Joint.HEEL).x)
        listOf(Joint.HIP to Joint.KNEE, Joint.KNEE to Joint.HEEL, Joint.HIP to Joint.NECK).forEach { (a, b) ->
            assertEquals("$a-$b", len(top, a, b), len(bottom, a, b), 1f) // bones don't stretch
        }
    }

    @Test fun wallPushHasThePalmOnTheWallAtShoulderHeight() {
        val d = DEMOS.getValue("wall-push")
        assertTrue(d.wall)
        d.keys.forEach { (_, q) ->
            assertEquals(WALL_X, p(q, Joint.HAND).x, 2f) // palm on the wall
            assertEquals(p(q, Joint.SHOULDER).y, p(q, Joint.HAND).y, 3f) // arm straight out at shoulder height
        }
        assertEquals(p(poseAt(d, 0), Joint.HAND), p(poseAt(d, 1000), Joint.HAND)) // isometric: the hand never moves
        assertEquals(Joint.SHOULDER to Joint.HAND, d.bones.last()) // the pressing arm is the accent
    }

    @Test fun titlePicksTheDemo() {
        assertEquals("tib-raise", demoFor("Tib raises"))
        assertEquals("tib-raise", demoFor("Tibialis raise"))
        assertNull(demoFor("Wall push right shoulder"))
        assertTrue(DEMOS.containsKey("tib-raise-lean"))
    }
}
