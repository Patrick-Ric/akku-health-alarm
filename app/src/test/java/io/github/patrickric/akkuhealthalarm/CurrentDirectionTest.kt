package io.github.patrickric.akkuhealthalarm

import io.github.patrickric.akkuhealthalarm.data.CurrentDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentDirectionTest {
    @Test
    fun unpluggedIsAlwaysDischarge() {
        val d = CurrentDirection()
        assertEquals(-350, d.apply(350, plugged = false))
        assertEquals(-350, d.apply(-350, plugged = false))
    }

    /** moto G30 on a 500 mA USB port, never unplugged: idle +170 mA, CPU load −630 mA – right from the start. */
    @Test
    fun androidSignWorksWithoutLearning() {
        val d = CurrentDirection()
        assertEquals(170, d.apply(170, plugged = true, level = 76))
        assertEquals(-630, d.apply(-630, plugged = true, level = 76))
        assertEquals(170, d.apply(170, plugged = true, level = 76))
    }

    @Test
    fun androidSignStaysAfterUnplugging() {
        val d = CurrentDirection()
        repeat(5) { d.apply(-300, plugged = false) }
        assertEquals(-1, d.dischargeSign)
        assertTrue(d.signed)
        assertEquals(450, d.apply(450, plugged = true))
        assertEquals(-650, d.apply(-650, plugged = true))
    }

    @Test
    fun invertedSignIsLearnedUnplugged() {
        val d = CurrentDirection()
        repeat(3) { d.apply(300, plugged = false) }
        assertEquals(1, d.dischargeSign)
        assertEquals(450, d.apply(-450, plugged = true))
        assertEquals(-650, d.apply(650, plugged = true))
    }

    /** Inverted phone that stays on the charger: level steps up teach the direction. */
    @Test
    fun invertedSignIsLearnedFromLevelSteps() {
        val d = CurrentDirection()
        assertEquals(-300, d.apply(-300, plugged = true, level = 70)) // wrong until learned
        d.apply(-320, plugged = true, level = 71)
        d.apply(-310, plugged = true, level = 72)
        d.apply(-330, plugged = true, level = 73)
        assertEquals(1, d.dischargeSign)
        assertEquals(300, d.apply(-300, plugged = true, level = 73))
        assertEquals(-600, d.apply(600, plugged = true, level = 73))
    }

    /** A phone without a sign must not show a normal charge as discharge. */
    @Test
    fun phoneWithoutSignStaysCharging() {
        val d = CurrentDirection()
        repeat(5) { d.apply(300, plugged = false) }
        repeat(5) { assertEquals(450, d.apply(450, plugged = true)) }
        assertFalse(d.signed)
    }

    @Test
    fun phoneWithoutSignMixedVotes() {
        val d = CurrentDirection()
        repeat(4) { d.apply(300, plugged = false, level = 50) }
        (50..55).forEach { assertEquals(400, d.apply(400, plugged = true, level = it)) }
    }

    @Test
    fun smallValuesDoNotTeach() {
        val d = CurrentDirection()
        repeat(5) { d.apply(5, plugged = false) }
        assertEquals(-1, d.dischargeSign)
        assertTrue(d.signed)
    }

    @Test
    fun savedStateIsUsed() {
        val d = CurrentDirection(dischargeSign = 1, signed = true)
        assertEquals(-500, d.apply(500, plugged = true))
        assertEquals(500, d.apply(-500, plugged = true))
    }
}
