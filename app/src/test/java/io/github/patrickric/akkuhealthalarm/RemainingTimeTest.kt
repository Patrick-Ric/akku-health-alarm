package io.github.patrickric.akkuhealthalarm

import io.github.patrickric.akkuhealthalarm.data.RemainingTime
import io.github.patrickric.akkuhealthalarm.data.RemainingTime.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemainingTimeTest {
    private val min = 60_000L

    @Test
    fun chargingOnePercentEveryThreeMinutes() {
        // 40 % at 0 min, 41 % at 3, 42 % at 6, 43 % at 9 -> 3 min per percent.
        val pts = (0..3).map { Point(it * 3 * min, 40 + it, true) }
        assertEquals(37 * 3 * min, RemainingTime.estimate(pts, 80))
    }

    @Test
    fun waitingAtCurrentLevelIsSubtracted() {
        val pts = (0..3).map { Point(it * 3 * min, 40 + it, true) } + Point(11 * min, 43, true)
        assertEquals(37 * 3 * min - 2 * min, RemainingTime.estimate(pts, 80))
    }

    @Test
    fun dischargingTowardsLowLimit() {
        // 60 % -> 55 % in 50 min = 10 min per percent; 35 % more to reach 20 %.
        val pts = listOf(Point(0, 60, false), Point(50 * min, 55, false))
        assertEquals(35 * 10 * min, RemainingTime.estimate(pts, 20))
    }

    @Test
    fun onlyCurrentSessionCounts() {
        val pts = listOf(Point(0, 50, false), Point(10 * min, 48, false), Point(11 * min, 48, true))
        assertNull(RemainingTime.estimate(pts, 80))
    }

    @Test
    fun tooLittleDataGivesNull() {
        assertNull(RemainingTime.estimate(listOf(Point(0, 40, true), Point(2 * min, 41, true)), 80))
        assertNull(RemainingTime.estimate(emptyList(), 80))
    }

    @Test
    fun targetAlreadyReachedGivesNull() {
        val pts = (0..3).map { Point(it * 3 * min, 80 + it, true) }
        assertNull(RemainingTime.estimate(pts, 80))
    }

    @Test
    fun slowChargerUsesLongerWindow() {
        // 1 % per 20 min: 30 min show only 1 %, so the last 3 hours are used.
        val pts = (0..4).map { Point(it * 20 * min, 30 + it, true) }
        assertEquals(46 * 20 * min, RemainingTime.estimate(pts, 80))
    }

    @Test
    fun longWaitAtLevelSlowsTheEstimateInsteadOfOneMinute() {
        // Real case G30, 13.11.: 61 -> 51 % in 65 min (6.5 min per percent), then 2 hours at 51 %.
        // Before the fix: 16 x 6.5 min - 120 min < 0 -> "35 % in about 1 min".
        val pts = (0..10).map { Point(it * 6 * min + it * min / 2, 61 - it, false) } + Point(65 * min + 120 * min, 51, false)
        // The 3-hour window starts at 60 % (6.5 min). Speed now: 9 percent + the one due in 178.5 min
        // = 17.85 min per percent; the next percent counts as due, 15 more steps after it (about 4.5 h).
        val expected = (15 * (178.5 / 10) * min).toLong()
        assertEquals(expected.toDouble(), RemainingTime.estimate(pts, 35)!!.toDouble(), 1.0 * min)
    }

    @Test
    fun dischargingFollowsTheLastHalfHour() {
        // 2 hours resting (1 % per hour), then screen on: 3 % in 15 min.
        val pts = listOf(Point(0, 63, false), Point(60 * min, 62, false), Point(120 * min, 61, false)) +
            (1..3).map { Point(120 * min + it * 5 * min, 61 - it, false) }
        // Last 30 min: 61 % at 120 -> 58 % at 135 = 5 min per percent; 18 % to go.
        assertEquals(18 * 5 * min, RemainingTime.estimate(pts, 40))
    }

    @Test
    fun dischargeFromCurrent() {
        // Real case 13.11. 19:37: 60 % -> 55 %, 326 mA, 3877 mAh: 5 % = 194 mAh -> about 36 min.
        assertEquals(35.7, RemainingTime.fromCurrent(60, 55, 326.0, 3877)!! / min.toDouble(), 0.5)
        // Too little current to be a real drain: no estimate from it.
        assertNull(RemainingTime.fromCurrent(60, 55, 6.0, 3877))
        assertNull(RemainingTime.fromCurrent(50, 55, 300.0, 3877))
    }
}
