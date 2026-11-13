package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StravaTest {
    @Test
    fun `walk records as a Strava Walk`() {
        assertEquals("Walk", Strava.sportFor("walk"))
    }

    @Test
    fun `other quick timers don't touch Strava`() {
        QUICK.filter { it != "walk" }.forEach { assertNull(it, Strava.sportFor(it)) }
        assertNull(Strava.sportFor("lib:anything"))
    }
}
