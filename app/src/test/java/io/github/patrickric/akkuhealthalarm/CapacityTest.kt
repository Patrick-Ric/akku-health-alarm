package io.github.patrickric.akkuhealthalarm

import io.github.patrickric.akkuhealthalarm.data.Capacity
import io.github.patrickric.akkuhealthalarm.data.ChargeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapacityTest {
    @Test
    fun scalesChargeToFullCapacity() {
        // 2 000 mAh for 40 % -> 5 000 mAh.
        assertEquals(5000, Capacity.fromSession(30, 70, 2000.0, null))
    }

    @Test
    fun prefersChargeCounter() {
        assertEquals(4800, Capacity.fromSession(30, 70, 2000.0, 1920))
    }

    @Test
    fun shortChargeGivesNoEstimate() {
        assertNull(Capacity.fromSession(50, 60, 500.0, null))
    }

    @Test
    fun implausibleValuesAreRejected() {
        assertNull(Capacity.fromSession(20, 80, 10.0, null))
        assertNull(Capacity.fromSession(20, 80, 0.0, null))
    }

    @Test
    fun medianIgnoresOutlier() {
        assertEquals(4800, Capacity.median(listOf(4700, 4800, 9000)))
        assertEquals(4750, Capacity.median(listOf(4700, 4800)))
        assertNull(Capacity.median(emptyList()))
    }

    @Test
    fun cyclesAddUp() {
        fun s(a: Int, b: Int) = ChargeSession(0, 0, 0, a, b, 1, 0.0, null, 0, 0, 0, null)
        assertEquals(1.1, Capacity.cycles(listOf(s(20, 80), s(30, 80))), 1e-9)
    }

    private fun session(start: Int, end: Int, cap: Int, counter: Boolean = false, full: Boolean = false) =
        ChargeSession(0, 0, 0, start, end, 1, 0.0, if (counter) 1 else null, 0, 0, 0, cap, full)

    @Test
    fun uncertaintyShrinksWithMoreCharges() {
        val one = Capacity.estimate(listOf(session(30, 50, 5000)))!!
        val four = Capacity.estimate(List(4) { session(30, 50, 5000) })!!
        assertEquals(5000, four.mah)
        assert(four.plusMinusMah < one.plusMinusMah)
    }

    @Test
    fun longChargeIsMorePrecise() {
        val short = Capacity.estimate(listOf(session(40, 55, 5000)))!!
        val long = Capacity.estimate(listOf(session(20, 80, 5000)))!!
        assert(long.plusMinusMah < short.plusMinusMah)
    }

    @Test
    fun longChargeWeighsMore() {
        val e = Capacity.estimate(listOf(session(20, 90, 5000), session(40, 55, 4000)))!!
        // Plain average would be 4 500; the long charge pulls the result towards 5 000.
        assert(e.mah in 4800..4950) { "was ${e.mah}" }
        assertEquals(85, e.totalPercent)
    }

    @Test
    fun scatterIncreasesUncertainty() {
        val calm = Capacity.estimate(List(4) { session(20, 80, 5000) })!!
        val wild = Capacity.estimate(listOf(4000, 6000, 4200, 5800).map { session(20, 80, it) })!!
        assert(wild.plusMinusMah > calm.plusMinusMah * 2)
    }

    @Test
    fun fullIsNoBetterEndPointThanAnyOther() {
        // Many phones show 100 % before the battery is full; only the size of the charge counts.
        val toFull = Capacity.estimate(listOf(session(30, 100, 5000, full = true)))!!
        val partial = Capacity.estimate(listOf(session(20, 90, 5000)))!!
        assertEquals(partial.plusMinusMah, toFull.plusMinusMah)
    }

    @Test
    fun systematicErrorRemainsWithManyCharges() {
        // Even 50 identical charges cannot get below the sensor error (about 4 % at 2 sigma).
        val many = Capacity.estimate(List(50) { session(20, 100, 5000) })!!
        assert(many.plusMinusMah >= 190) { "was ${many.plusMinusMah}" }
    }

    @Test
    fun sixtyPercentChargeIsNotTooOptimistic() {
        // One charge 20 -> 80 % from added-up current: about +-12 %, not +-1 %.
        val e = Capacity.estimate(listOf(session(20, 80, 5000)))!!
        assert(e.plusMinusMah in 500..700) { "was ${e.plusMinusMah}" }
    }

    @Test
    fun counterThatOnlyFollowsTheLevelIsDetected() {
        // Motorola edge 20 lite: counter = level x 4931 mAh at every reading (4928..4931 after rounding).
        assert(Capacity.counterFollowsLevel(4928f, 4931f, 4931))
        assert(Capacity.counterFollowsLevel(4928f, 4931f, null))
        // A real counter moves within each percent step: at 30 % the ratio spreads by about 1/30.
        assert(!Capacity.counterFollowsLevel(4800f, 4960f, 4931))
        // Constant, but far from the rated value: the phone's own learned capacity, keep it.
        assert(!Capacity.counterFollowsLevel(4300f, 4310f, 4931))
        assert(!Capacity.counterFollowsLevel(0f, 0f, 4931))
    }

    @Test
    fun basisIsTheLatestMeasuredSessions() {
        val day = 24 * 60 * 60 * 1000L
        val sessions = (1..12).map { ChargeSession(it.toLong(), 0, it * day, 30, 70, 1, 0.0, null, 0, 0, 0, 5000) } +
            ChargeSession(99, 0, 50 * day, 30, 40, 1, 0.0, null, 0, 0, 0, null)
        val basis = Capacity.basis(sessions)
        assertEquals((12 downTo 3).map { it.toLong() }, basis.map { it.id })
    }

    @Test
    fun counterFromLevelIsNotTreatedAsCounter() {
        // Same session, once with a real counter, once with one that only follows the level: the latter
        // has the larger error of the current measurement.
        val real = ChargeSession(1, 0, 1, 28, 56, 1, 1214.0, 1381, 0, 0, 0, 4932)
        val fake = real.copy(capacityMah = 4335, counterFromLevel = true)
        assert(Capacity.sessionError(fake) > Capacity.sessionError(real))
    }

    @Test
    fun counterInWrongUnitIsIgnored() {
        // Counter says 2 mAh for 40 % (reported in mAh, divided again): use the measured current instead.
        assertEquals(5000, Capacity.fromSession(30, 70, 2000.0, 2))
    }
}
