package io.github.patrickric.akkuhealthalarm.data

import android.content.Context
import kotlin.math.roundToInt

object Capacity {
    /** A session must add at least this many percent, otherwise the estimate is too rough. */
    const val MIN_LEVEL_CHANGE = 15

    /** How many recent sessions the displayed capacity is based on. */
    const val SESSIONS_FOR_ESTIMATE = 10

    /** Random error of the measured charge per session: charge counter vs. added-up current (1 sigma). */
    private const val COUNTER_ERROR = 0.01
    private const val CURRENT_ERROR = 0.03

    /**
     * Error that many charges cannot average away, e.g. a miscalibrated current sensor (1 sigma).
     * It sets the best possible accuracy.
     */
    private const val COUNTER_SYSTEMATIC = 0.01
    private const val CURRENT_SYSTEMATIC = 0.02

    /**
     * The shown level is itself an estimate of the battery chip and can be off or jump by a few
     * percent (1 sigma per end point). "Full" is no better: many phones show 100 % before the battery is.
     */
    private const val LEVEL_ERROR_PERCENT = 2.0

    /** Sessions the estimate is based on: the latest measured ones. */
    fun basis(sessions: List<ChargeSession>): List<ChargeSession> =
        sessions.filter { it.capacityMah != null }.sortedByDescending { it.endTime }.take(SESSIONS_FOR_ESTIMATE)

    /**
     * Some phones (e.g. Motorola edge 20 lite) do not count the charge at all: their "charge counter" is just
     * level x rated capacity. Then counter / level stays the same at every reading, while a real counter moves
     * within each percent step. Such a counter would always give the rated capacity and never show wear.
     * [minRatio]/[maxRatio]: smallest and largest counter * 100 / level seen during the charge (mAh).
     */
    fun counterFollowsLevel(minRatio: Float, maxRatio: Float, design: Int?): Boolean {
        if (minRatio <= 0f || maxRatio < minRatio) return false
        val constant = (maxRatio - minRatio) / maxRatio <= 0.005f
        val mid = (minRatio + maxRatio) / 2
        return constant && (design == null || kotlin.math.abs(mid - design) <= design * 0.01f)
    }

    /** Capacity with its uncertainty (about 95 %, i.e. two standard deviations). */
    data class Estimate(val mah: Int, val plusMinusMah: Int, val sessions: Int, val totalPercent: Int)

    /**
     * Full capacity estimated from one charge: charge put in, scaled to 100 %.
     * The phone's own charge counter is preferred when it reports one.
     */
    fun fromSession(startLevel: Int, endLevel: Int, chargedMah: Double, counterDeltaMah: Int?): Int? {
        val change = endLevel - startLevel
        if (change < MIN_LEVEL_CHANGE) return null
        fun scale(mah: Double) = (mah * 100.0 / change).roundToInt().takeIf { it in 500..30_000 }
        val byCurrent = chargedMah.takeIf { it > 0.0 }?.let(::scale)
        val byCounter = counterDeltaMah?.takeIf { it > 0 }?.toDouble()?.let(::scale)
        // Some phones report the counter in other units; trust it only if it roughly agrees with the current.
        return when {
            byCounter != null && (byCurrent == null || byCounter in (byCurrent / 2)..(byCurrent * 2)) -> byCounter
            else -> byCurrent
        }
    }

    /** Relative standard error of one session: rounding of the level plus the charge measurement. */
    fun sessionError(s: ChargeSession): Double {
        val change = (s.endLevel - s.startLevel).coerceAtLeast(1)
        val levelError = kotlin.math.sqrt(2.0) * LEVEL_ERROR_PERCENT / change
        val measure = if (usesCounter(s)) COUNTER_ERROR else CURRENT_ERROR
        return kotlin.math.sqrt(levelError * levelError + measure * measure)
    }

    private fun usesCounter(s: ChargeSession) = !s.counterFromLevel && s.counterDeltaMah != null && s.counterDeltaMah > 0

    /**
     * Weighted mean of the session estimates; long charges count more. The uncertainty shrinks
     * with every charge, but grows again if the sessions scatter more than expected.
     */
    fun estimate(sessions: List<ChargeSession>): Estimate? {
        val valid = sessions.filter { it.capacityMah != null }
        if (valid.isEmpty()) return null
        val sigmas = valid.map { sessionError(it) * it.capacityMah!! }
        val weights = sigmas.map { 1.0 / (it * it) }
        val sumW = weights.sum()
        val mean = valid.indices.sumOf { weights[it] * valid[it].capacityMah!! } / sumW
        val modelSe = kotlin.math.sqrt(1.0 / sumW)
        val n = valid.size
        val scatterSe = if (n >= 3) {
            val variance = valid.indices.sumOf { weights[it] * (valid[it].capacityMah!! - mean).let { d -> d * d } } / sumW
            kotlin.math.sqrt(variance * n / (n - 1) / n)
        } else {
            0.0
        }
        val systematic = mean * if (valid.count(::usesCounter) * 2 >= n) COUNTER_SYSTEMATIC else CURRENT_SYSTEMATIC
        val sigma = kotlin.math.sqrt(maxOf(modelSe, scatterSe).let { it * it } + systematic * systematic)
        return Estimate(
            mah = mean.roundToInt(),
            plusMinusMah = (2 * sigma).roundToInt().coerceAtLeast(1),
            sessions = n,
            totalPercent = valid.sumOf { it.endLevel - it.startLevel },
        )
    }

    /** Median of the given estimates; robust against a single odd charge. */
    fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** Equivalent full cycles: charging 50 % counts as half a cycle. */
    fun cycles(sessions: List<ChargeSession>): Double =
        sessions.sumOf { (it.endLevel - it.startLevel).coerceAtLeast(0) } / 100.0

    /**
     * Rated capacity as the phone's power profile reports it. This is a hidden Android class;
     * if it is blocked or returns the AOSP placeholder, the user can enter the value.
     */
    fun designFromSystem(context: Context): Int? = try {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(context)
        val value = cls.getMethod("getBatteryCapacity").invoke(profile) as Double
        value.roundToInt().takeIf { it in 500..30_000 && it != 1000 }
    } catch (e: Throwable) {
        null
    }
}
