package io.github.patrickric.akkuhealthalarm

import io.github.patrickric.akkuhealthalarm.data.RecordingGap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingGapTest {
    private val min = 60_000L

    @Test
    fun quietNightIsNoGap() {
        // One hour without updates, only one percent less: deep sleep, the app was running.
        assertFalse(RecordingGap.isGap(0, 60, 60 * min, 59))
    }

    @Test
    fun jumpAfterLongPauseIsGap() {
        assertTrue(RecordingGap.isGap(0, 60, 150 * min, 48))
        assertTrue(RecordingGap.isGap(0, 40, 90 * min, 70))
    }

    @Test
    fun shortPauseIsNoGap() {
        assertFalse(RecordingGap.isGap(0, 60, 10 * min, 55))
    }
}
