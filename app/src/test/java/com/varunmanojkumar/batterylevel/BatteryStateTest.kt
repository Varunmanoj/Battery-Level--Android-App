package com.varunmanojkumar.batterylevel

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun reportsChargingImmediatelyFromCurrentBatteryStatus() {
        assertTrue(
            batteryUiState(72, 100, BatteryManager.BATTERY_STATUS_CHARGING).isCharging,
        )
        assertTrue(
            batteryUiState(100, 100, BatteryManager.BATTERY_STATUS_FULL).isCharging,
        )
        assertFalse(
            batteryUiState(72, 100, BatteryManager.BATTERY_STATUS_DISCHARGING).isCharging,
        )
    }
}
