package com.sautique.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinsTest {
    @Test
    fun eachSlotHasAFreeDefault() {
        for (slot in Skins.Slot.values()) {
            val first = Skins.bySlot(slot).first()
            assertEquals(0, first.cost)
            assertEquals(0, first.price)
        }
    }

    @Test
    fun unlocksBetweenPointValues() {
        val labels = Skins.unlockedBetween(0, 30).map { it.id }
        assertTrue("boule" in labels)
        assertTrue(Skins.unlockedBetween(30, 30).isEmpty())
    }

    @Test
    fun shopItemsAreNotUnlockedByPoints() {
        assertTrue(Skins.items.filter { it.price > 0 }.isNotEmpty())
        assertTrue(Skins.unlockedBetween(0, 1_000_000).none { it.price > 0 })
    }

    @Test
    fun idsAreUniquePerSlot() {
        for (slot in Skins.Slot.values()) {
            val ids = Skins.bySlot(slot).map { it.id }
            assertEquals(ids.size, ids.toSet().size)
        }
    }
}
