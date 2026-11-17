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

    /** Runs the engine with a new limit and returns the result. */
    private fun step(level: Int, plugged: Boolean, settings: AlarmSettings, st: EngineState, temp: Float = 25f) =
        AlarmEngine.evaluate(level, plugged, temp, settings, st)

    @Test
    fun lowEndsAtOnceWhenTheUserLowersTheLimitBelowTheLevel() {
        val fired = step(24, false, AlarmSettings(low = 25, high = 80), EngineState())
        assertEquals(listOf(AlarmType.LOW), fired.fire)
        // Limit 24 = level: still "at 24 %", the alarm stays.
        val same = step(24, false, AlarmSettings(low = 24, high = 80), fired.state)
        assertTrue(same.clear.isEmpty())
        // Limit 23: the level is above it, the alarm ends without waiting for 3 % more.
        val below = step(24, false, AlarmSettings(low = 23, high = 80), same.state)
        assertEquals(listOf(AlarmType.LOW), below.clear)
        assertTrue(below.state.lowArmed)
        // With the new limit it fires again when the level really falls to it.
        assertEquals(listOf(AlarmType.LOW), step(23, false, AlarmSettings(low = 23, high = 80), below.state).fire)
    }

    @Test
    fun lowStillWaitsForHysteresisWhenOnlyTheLevelWobbles() {
        val st = step(25, false, s.copy(low = 25), EngineState()).state
        assertTrue(step(26, false, s.copy(low = 25), st).clear.isEmpty())
        assertEquals(listOf(AlarmType.LOW), step(28, false, s.copy(low = 25), st).clear)
    }

    @Test
    fun highEndsAtOnceWhenTheUserRaisesTheLimitAboveTheLevel() {
        val fired = step(80, true, s, EngineState())
        assertEquals(listOf(AlarmType.HIGH), fired.fire)
        assertEquals(listOf(AlarmType.HIGH), step(80, true, s.copy(high = 81), fired.state).clear)
        // Raising the limit the other way round (lower) keeps the alarm.
        assertTrue(step(80, true, s.copy(high = 75), fired.state).clear.isEmpty())
    }

    @Test
    fun hotEndsAtOnceWhenTheUserRaisesTheLimit() {
        val t = AlarmSettings(tempEnabled = true, tempHigh = 40)
        val fired = step(50, false, t, EngineState(), temp = 41f)
        assertEquals(listOf(AlarmType.HOT), fired.fire)
        assertTrue(step(50, false, t, fired.state, temp = 39f).clear.isEmpty())
        assertEquals(listOf(AlarmType.HOT), step(50, false, t.copy(tempHigh = 42), fired.state, temp = 41f).clear)
    }

    @Test
    fun stateFromAnOlderVersionKeepsTheHysteresis() {
        // Saved before the limit was remembered: no early end, only the usual 3 %.
        val old = EngineState(lowArmed = false)
        assertTrue(step(24, false, AlarmSettings(low = 22, high = 80), old).clear.isEmpty())
        assertEquals(listOf(AlarmType.LOW), step(25, false, AlarmSettings(low = 22, high = 80), old).clear)
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
