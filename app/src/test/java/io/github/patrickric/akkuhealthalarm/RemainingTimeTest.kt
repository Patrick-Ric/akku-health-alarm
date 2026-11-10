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
}
