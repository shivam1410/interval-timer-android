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

    @Test fun titlePicksTheDemo() {
        assertEquals("tib-raise", demoFor("Tib raises"))
        assertEquals("tib-raise", demoFor("Tibialis raise"))
        assertNull(demoFor("Wall push right shoulder"))
        assertTrue(DEMOS.containsKey("tib-raise-lean"))
    }
}
