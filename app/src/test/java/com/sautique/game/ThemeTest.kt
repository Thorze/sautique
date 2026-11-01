package com.sautique.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun lerpEndsAndMiddle() {
        assertEquals(0xFF000000.toInt(), Theme.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), Theme.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF808080.toInt(), Theme.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
    }

    @Test
    fun skyChangesWithScore() {
        assertEquals("morning", Theme.sky(0).name)
        assertEquals(0f, Theme.sky(0).night)
        assertEquals("night", Theme.sky(65).name)
        assertEquals(1f, Theme.sky(65).night)
        assertEquals("space", Theme.sky(500).name)
        // Transition progressive avant un palier
        val mid = Theme.sky(56).night
        assertTrue(mid > 0.1f && mid < 1f)
    }
}
