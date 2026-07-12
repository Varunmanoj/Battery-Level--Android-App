package com.varunmanojkumar.batterylevel

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryStateTest {
    @Test
    fun normalizesBatteryLevelAgainstScale() {
        assertEquals(50, normalizedBatteryLevel(level = 25, scale = 50))
    }

    @Test
    fun clampsInvalidBatteryValues() {
        assertEquals(0, normalizedBatteryLevel(level = -1, scale = 100))
        assertEquals(100, normalizedBatteryLevel(level = 150, scale = 100))
        assertEquals(0, normalizedBatteryLevel(level = 50, scale = 0))
    }
}
