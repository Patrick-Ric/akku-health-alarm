package io.github.patrickric.akkuhealthalarm

import io.github.patrickric.akkuhealthalarm.data.EventEntry
import io.github.patrickric.akkuhealthalarm.data.EventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRowMergeTest {
    private val t0 = 1_000_000_000L
    private fun row(type: String, time: Long = t0, lastRepeat: Long? = null) =
        EventEntry(time = time, type = type, level = 29, tempDeciC = 230, lastRepeat = lastRepeat)

    /** G30, 19.11. 11:10: the low alarm started again three times within 11 s – one row, not three. */
    @Test
    fun quickRestartContinuesRow() {
        val first = row(EventType.ALARM_LOW, lastRepeat = t0 + 4 * 60_000L)
        assertTrue(EventType.continuesRow(first, EventType.ALARM_LOW, t0 + 5 * 60_000L))
    }

    @Test
    fun longAfterIsNewRow() {
        assertFalse(EventType.continuesRow(row(EventType.ALARM_LOW), EventType.ALARM_LOW, t0 + 11 * 60_000L))
    }

    @Test
    fun otherTypeOrPlugEventIsNewRow() {
        assertFalse(EventType.continuesRow(row(EventType.ALARM_HIGH), EventType.ALARM_LOW, t0 + 1000))
        assertFalse(EventType.continuesRow(row(EventType.PLUGGED), EventType.ALARM_LOW, t0 + 1000))
        assertFalse(EventType.continuesRow(row(EventType.PLUGGED), EventType.PLUGGED, t0 + 1000))
        assertFalse(EventType.continuesRow(null, EventType.ALARM_LOW, t0))
    }
}
