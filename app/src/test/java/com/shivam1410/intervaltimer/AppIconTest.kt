package com.shivam1410.intervaltimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconTest {
    @Test fun sevenIconsTheClassicGongFirstEachWithItsOwnLauncherEntry() {
        assertEquals(listOf("classic", "night", "ripple", "ringgong", "interval", "stretch", "focus"), APP_ICONS.map { it.id })
        assertEquals("com.shivam1410.intervaltimer.IconClassic", iconAlias("classic"))
        assertEquals("com.shivam1410.intervaltimer.IconRinggong", iconAlias("ringgong"))
        assertEquals(APP_ICONS.size, APP_ICONS.map { iconAlias(it.id) }.toSet().size)
        assertTrue(APP_ICONS.all { it.label.isNotBlank() })
    }
}
