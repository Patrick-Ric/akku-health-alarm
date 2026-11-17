package io.github.patrickric.akkuhealthalarm.data

import kotlin.math.abs
import kotlin.math.max
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
    private const val SHORT_WINDOW_MS = 30 * 60 * 1000L
    private const val LONG_WINDOW_MS = 3 * 60 * 60 * 1000L
    private const val MAX_RESULT_MS = 48 * 60 * 60 * 1000L

    /**
     * On battery while the app is open (screen on): time from the current drain, [currentMa] averaged over
     * the last minute, and the battery capacity. Follows the present use at once, unlike the level history.
     */
    fun fromCurrent(level: Int, target: Int, currentMa: Double, capacityMah: Int): Long? {
        if (target >= level || currentMa < MIN_CURRENT_MA || capacityMah <= 0) return null
        val hours = (level - target) / 100.0 * capacityMah / currentMa
        return (hours * 60 * 60 * 1000).roundToLong().coerceAtLeast(60_000L).takeIf { it <= MAX_RESULT_MS }
    }

    /** Below this the reading is not a real drain (or the phone reports nonsense); use the level history. */
    private const val MIN_CURRENT_MA = 20.0

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
        // Prefer the last 30 minutes (follows a change of use, e.g. the screen turned on); if that shows
        // too little change (slow charger, phone resting), look back up to 3 hours.
        val recent = listOf(SHORT_WINDOW_MS, LONG_WINDOW_MS).map { w -> session.filter { it.time >= now.time - w } }
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

        // Waiting at the current level longer than the measured speed allows means the battery is slower
        // now (e.g. screen off after heavy use). Then the waiting time counts as well, as if the next
        // percent were just about to come. Before, the waiting time was only subtracted: after 2 hours
        // at 51 % this gave "35 % in about 1 min".
        val waited = now.time - lastChange.time
        val msPerPercent = max(span.toDouble() / abs(change), (now.time - first.time).toDouble() / (abs(change) + 1))
        val steps = abs(target - now.level)
        val result = ((msPerPercent - waited).coerceAtLeast(0.0) + (steps - 1) * msPerPercent).roundToLong()
        return result.coerceAtLeast(60_000L).takeIf { it <= MAX_RESULT_MS }
    }
}
