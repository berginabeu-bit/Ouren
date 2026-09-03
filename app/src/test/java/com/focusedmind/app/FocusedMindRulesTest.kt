package com.focusedmind.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusedMindRulesTest {
    @Test fun levelStartsAtOne() {
        val level = FocusedMindStore.levelFor(0)
        assertEquals(1, level.number)
        assertEquals(0L, level.floor)
    }

    @Test fun higherXpNeverProducesALowerLevel() {
        var previous = 1
        for (xp in listOf(0L, 500L, 5_000L, 50_000L, 500_000L, 5_000_000L)) {
            val level = FocusedMindStore.levelFor(xp).number
            assertTrue(level >= previous)
            previous = level
        }
    }
}
