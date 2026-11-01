package com.sautique.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoringTest {
    @Test
    fun perfectPointsGrowAfterThirdPerfect() {
        assertEquals(1, Scoring.perfectPoints(0))
        assertEquals(1, Scoring.perfectPoints(2))
        assertEquals(2, Scoring.perfectPoints(3))
        assertEquals(4, Scoring.perfectPoints(5))
        assertEquals(5, Scoring.perfectPoints(6))
        assertEquals(5, Scoring.perfectPoints(20))
    }

    @Test
    fun cursorPointsDependOnDistanceToCenter() {
        assertEquals(10, Scoring.cursorPoints(0f))
        assertEquals(10, Scoring.cursorPoints(-0.02f))
        assertEquals(3, Scoring.cursorPoints(0.45f))
        assertEquals(0, Scoring.cursorPoints(1f))
        assertEquals(0, Scoring.cursorPoints(-1f))
    }

    @Test
    fun versionCodeFromTag() {
        assertEquals(40000, Scoring.versionCodeOf("v4.0"))
        assertEquals(40201, Scoring.versionCodeOf("v4.2.1"))
        assertEquals(50000, Scoring.versionCodeOf("5"))
        assertNull(Scoring.versionCodeOf("apk"))
    }
}
