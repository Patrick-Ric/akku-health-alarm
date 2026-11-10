package io.github.patrickric.akkuhealthalarm.data

import kotlin.math.abs

/**
 * Decides whether the recording was interrupted between two readings.
 *
 * Time alone is not enough: in deep sleep Android may send no battery update for an hour while
 * the app runs fine. But a running app stores every single percent change, so a long pause with
 * a jump of two or more percent means the app was not running.
 */
object RecordingGap {
    const val MIN_GAP_MS = 25 * 60 * 1000L
    const val MIN_LEVEL_JUMP = 2

    fun isGap(olderTime: Long, olderLevel: Int, newerTime: Long, newerLevel: Int): Boolean =
        newerTime - olderTime > MIN_GAP_MS && abs(newerLevel - olderLevel) >= MIN_LEVEL_JUMP
}
