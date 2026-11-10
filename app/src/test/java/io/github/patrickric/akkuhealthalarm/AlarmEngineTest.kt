package io.github.patrickric.akkuhealthalarm

import io.github.patrickric.akkuhealthalarm.data.AlarmSettings
import io.github.patrickric.akkuhealthalarm.service.AlarmEngine
import io.github.patrickric.akkuhealthalarm.service.AlarmType
import io.github.patrickric.akkuhealthalarm.service.EngineState
import io.github.patrickric.akkuhealthalarm.service.QuietHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEngineTest {
    private val s = AlarmSettings(low = 20, high = 80)

    /** Feeds a sequence of (level, plugged) and returns all fired alarms in order. */
    private fun run(settings: AlarmSettings, vararg steps: Pair<Int, Boolean>, temp: Float = 25f): List<AlarmType> {
        var st = EngineState()
        val fired = mutableListOf<AlarmType>()
        for ((level, plugged) in steps) {
            val r = AlarmEngine.evaluate(level, plugged, temp, settings, st)
            st = r.state
            fired += r.fire
        }
        return fired
    }

    @Test
    fun highFiresOnceWhileCharging() {
        assertEquals(listOf(AlarmType.HIGH), run(s, 78 to true, 79 to true, 80 to true, 81 to true, 82 to true))
    }

    @Test
    fun highDoesNotRepeatOnSmallWobble() {
        assertEquals(listOf(AlarmType.HIGH), run(s, 80 to true, 79 to true, 80 to true))
    }

    @Test
    fun highRearmsAfterUnplug() {
        assertEquals(
            listOf(AlarmType.HIGH, AlarmType.HIGH),
            run(s, 80 to true, 80 to false, 80 to true),
        )
    }

    @Test
    fun noHighOnBattery() {
        assertTrue(run(s, 90 to false, 85 to false).isEmpty())
    }

    @Test
    fun lowFiresOnceOnBatteryAndRearmsWhenPlugged() {
        assertEquals(
            listOf(AlarmType.LOW, AlarmType.LOW),
            run(s, 21 to false, 20 to false, 19 to false, 19 to true, 19 to false),
        )
    }

    @Test
    fun disabledFiresNothing() {
        assertTrue(run(s.copy(enabled = false), 85 to true, 10 to false).isEmpty())
    }

    @Test
    fun temperatureOffByDefault() {
        assertTrue(run(s, 50 to true, temp = 50f).isEmpty())
    }

    @Test
    fun hotFiresWhenEnabled() {
        assertEquals(listOf(AlarmType.HOT), run(s.copy(tempEnabled = true), 50 to true, 50 to true, temp = 41f))
    }

    @Test
    fun coldOnlyWhileChargingByDefault() {
        val t = s.copy(tempEnabled = true)
        assertTrue(run(t, 50 to false, temp = 5f).isEmpty())
        assertEquals(listOf(AlarmType.COLD), run(t, 50 to true, temp = 5f))
        assertEquals(listOf(AlarmType.COLD), run(t.copy(coldOnlyCharging = false), 50 to false, temp = 5f))
    }

    @Test
    fun quietHoursAcrossMidnight() {
        assertTrue(QuietHours.contains(23 * 60, 22 * 60, 7 * 60))
        assertTrue(QuietHours.contains(3 * 60, 22 * 60, 7 * 60))
        assertFalse(QuietHours.contains(7 * 60, 22 * 60, 7 * 60))
        assertFalse(QuietHours.contains(12 * 60, 22 * 60, 7 * 60))
        assertTrue(QuietHours.contains(13 * 60, 12 * 60, 14 * 60))
        assertFalse(QuietHours.contains(13 * 60, 12 * 60, 12 * 60))
    }
}
