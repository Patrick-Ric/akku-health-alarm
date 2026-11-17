package io.github.patrickric.akkuhealthalarm

import io.github.patrickric.akkuhealthalarm.data.AlarmDiag
import io.github.patrickric.akkuhealthalarm.data.AlarmDiag.Clip
import io.github.patrickric.akkuhealthalarm.data.AlarmDiag.End
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmDiagTest {
    private fun diag(vararg clips: Clip) = AlarmDiag(
        screenOn = false, volume = 2, volumeMax = 7, alarmStream = false,
        focusGranted = true, doNotDisturb = false, clips = clips.toList(),
    )

    @Test
    fun encodeAndParseGiveTheSame() {
        val d = diag(Clip(End.OK, 1380, 1400), Clip(End.ERROR, 0, 0, "1/-1004"))
        assertEquals(d, AlarmDiag.parse(d.encode()))
        assertEquals(diag(), AlarmDiag.parse(diag().encode()))
    }

    @Test
    fun unreadableTextGivesNull() {
        assertNull(AlarmDiag.parse(null))
        assertNull(AlarmDiag.parse(""))
        assertNull(AlarmDiag.parse("s=1;v=x"))
    }

    @Test
    fun completeWhenEveryClipReachedItsEnd() {
        val d = diag(Clip(End.OK, 1380, 1400), Clip(End.OK, 1150, 1230), Clip(End.OK, 2100, 0))
        assertNull(d.problem)
        assertFalse(d.failed)
        assertEquals(4630, d.playedMs)
    }

    @Test
    fun failedWhenAClipStoppedEarlyOrNothingPlayed() {
        assertTrue(diag().failed)
        assertTrue(diag(Clip(End.OK, 300, 1400)).failed)
        assertTrue(diag(Clip(End.OK, 1400, 1400), Clip(End.TIMEOUT, 6000, 1230)).failed)
        assertEquals(End.EXCEPTION, diag(Clip(End.EXCEPTION, 0, 0, "IOException")).problem?.end)
    }

    @Test
    fun aNewerAlarmTakingOverIsNoFailure() {
        val d = diag(Clip(End.OK, 1400, 1400), Clip(End.STOPPED, 500, 1230))
        assertEquals(End.STOPPED, d.problem?.end)
        assertFalse(d.failed)
    }
}
