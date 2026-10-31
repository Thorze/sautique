package com.sautique.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionsTest {
    private class MapStore : KeyValueStore {
        val ints = HashMap<String, Int>()
        val strings = HashMap<String, String>()
        override fun getInt(key: String, def: Int) = ints[key] ?: def
        override fun putInt(key: String, value: Int) { ints[key] = value }
        override fun getString(key: String) = strings[key]
        override fun putString(key: String, value: String) { strings[key] = value }
    }

    @Test
    fun sameMissionsForSameDay() {
        val a = DailyMissions.generate("2026-11-01").map { it.label }
        val b = DailyMissions.generate("2026-11-01").map { it.label }
        assertEquals(a, b)
        assertEquals(4, a.size)
        assertTrue(DailyMissions.generate("2026-11-01").last().challenge)
    }

    @Test
    fun progressCompletesAndRewardsOnce() {
        val store = MapStore()
        val dm = DailyMissions(store) { "2026-11-01" }
        val m = dm.list()[0]
        var coins = 0
        repeat(m.target + 3) {
            coins += dm.record(m.type, if (m.type.max) m.target else 1).coins
        }
        assertTrue(dm.isDone(0))
        assertTrue(coins >= m.reward)
        // Plus rien une fois terminée
        assertEquals(0, dm.record(m.type, m.target).coins.let { if (dm.list().count { it.type == m.type } == 1) it else 0 })
    }

    @Test
    fun maxTypesKeepBestValue() {
        val store = MapStore()
        // Cherche une date dont la 1re mission est de type « max »
        var date = 0
        var dm: DailyMissions
        do {
            val d = "2026-12-%02d".format(++date)
            dm = DailyMissions(store) { d }
        } while (!dm.list()[0].type.max && date < 28)
        val m = dm.list()[0]
        if (!m.type.max) return
        dm.record(m.type, 1)
        dm.record(m.type, 1)
        assertEquals(1, dm.progress(0))
        assertFalse(dm.isDone(0))
    }

    @Test
    fun newDayResetsProgress() {
        val store = MapStore()
        var day = "2026-11-01"
        val dm = DailyMissions(store) { day }
        val m = dm.list()[0]
        dm.record(m.type, 1)
        assertTrue(dm.progress(0) > 0)
        day = "2026-11-02"
        assertEquals(0, dm.progress(0))
    }
}
