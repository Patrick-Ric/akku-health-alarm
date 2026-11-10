package io.github.patrickric.akkuhealthalarm.data

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Estimates how long it takes to reach a target level, from the speed of the last changes
 * in the current charging or discharging session.
 */
object RemainingTime {
    /** A level point: time in ms and charge level. */
    data class Point(val time: Long, val level: Int, val plugged: Boolean)

    private const val MIN_CHANGE = 2
    private const val MIN_SPAN_MS = 4 * 60 * 1000L
    private const val CHARGE_WINDOW_MS = 30 * 60 * 1000L
    private const val DISCHARGE_WINDOW_MS = 3 * 60 * 60 * 1000L
    private const val MAX_RESULT_MS = 48 * 60 * 60 * 1000L

    /**
     * [points] sorted by time (oldest first); the last one is the current state.
     * Returns milliseconds until [target] is reached, or null if there is not enough data
     * or the target lies in the wrong direction.
     */
    fun estimate(points: List<Point>, target: Int): Long? {
        val now = points.lastOrNull() ?: return null
        val charging = now.plugged
        if (charging && target <= now.level) return null
        if (!charging && target >= now.level) return null

        // Only the current session counts: stop at the last plug change.
        val session = points.takeLastWhile { it.plugged == charging }
        // Charging: prefer the last 30 minutes; if that shows too little change (slow charger), look further back.
        val windows = if (charging) listOf(CHARGE_WINDOW_MS, DISCHARGE_WINDOW_MS) else listOf(DISCHARGE_WINDOW_MS)
        val recent = windows.map { w -> session.filter { it.time >= now.time - w } }
            .firstOrNull { pts -> pts.size >= 2 && abs(pts.last().level - pts.first().level) >= MIN_CHANGE }
            ?: return null

        // Measure between the first moments the levels were reached, so waiting time at the
        // current level does not distort the speed.
        val first = recent.first()
        val lastChange = recent.firstOrNull { it.level == now.level } ?: return null
        val change = lastChange.level - first.level
        val span = lastChange.time - first.time
        if (abs(change) < MIN_CHANGE || span < MIN_SPAN_MS) return null
        if (charging && change < 0 || !charging && change > 0) return null

        val msPerPercent = span.toDouble() / abs(change)
        val fromLastChange = (abs(target - lastChange.level) * msPerPercent).roundToLong()
        val result = fromLastChange - (now.time - lastChange.time)
        return result.coerceAtLeast(60_000L).takeIf { it <= MAX_RESULT_MS }
    }
}
