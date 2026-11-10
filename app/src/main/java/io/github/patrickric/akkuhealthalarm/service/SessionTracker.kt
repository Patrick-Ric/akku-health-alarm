package io.github.patrickric.akkuhealthalarm.service

import android.content.Context
import android.content.SharedPreferences
import io.github.patrickric.akkuhealthalarm.data.BatteryReading
import io.github.patrickric.akkuhealthalarm.data.Capacity
import io.github.patrickric.akkuhealthalarm.data.ChargeSession
import io.github.patrickric.akkuhealthalarm.data.RecordingGap
import kotlin.math.roundToInt

/**
 * Follows one charging session from plugging in to unplugging and adds up the charge that
 * flows into the battery. The state is kept in preferences so a restart of the service
 * does not lose a running session.
 */
class SessionTracker(private val context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    /** Smallest and largest counter * 100 / level so far, to spot a counter that only follows the level. */
    private fun SharedPreferences.Editor.trackRatio(r: BatteryReading): SharedPreferences.Editor {
        val counter = r.chargeCounterMah ?: return this
        if (counter <= 0 || r.level <= 0) return this
        val ratio = counter * 100f / r.level
        putFloat("ratioMin", minOf(prefs.getFloat("ratioMin", Float.MAX_VALUE), ratio))
        putFloat("ratioMax", maxOf(prefs.getFloat("ratioMax", 0f), ratio))
        return this
    }

    /** Snapshot of the running session for the app process (it cannot read this process's preferences). */
    private val runningFile = java.io.File(context.filesDir, RUNNING_FILE)

    private fun writeRunning() {
        if (!prefs.getBoolean("active", false)) return
        val text = listOf(
            prefs.getLong("start", 0), prefs.getInt("startLevel", 0), prefs.getInt("lastLevel", 0),
            prefs.getFloat("mah", 0f), prefs.getBoolean("broken", false),
        ).joinToString(";")
        val tmp = java.io.File(runningFile.path + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(runningFile)
    }

    /** Feeds a new reading; returns the finished session when the charger was removed. */
    fun update(r: BatteryReading, now: Long): ChargeSession? {
        val active = prefs.getBoolean("active", false)
        if (!r.isPlugged) {
            return if (active) finish(now) else null
        }
        if (!active) {
            prefs.edit()
                .putBoolean("active", true)
                .putLong("start", now)
                .putInt("startLevel", r.level)
                .putInt("startCounter", r.chargeCounterMah ?: -1)
                .putInt("plugged", r.plugged)
                .putFloat("mah", 0f)
                .putFloat("currentTime", 0f)
                .putLong("lastTime", now)
                .putInt("lastCurrent", chargingCurrent(r))
                .putInt("maxCurrent", chargingCurrent(r))
                .putInt("maxTemp", r.tempDeciC)
                .putInt("lastLevel", r.level)
                .putInt("lastCounter", r.chargeCounterMah ?: -1)
                .putBoolean("broken", false)
                .putBoolean("full", r.isFull)
                .remove("ratioMin")
                .remove("ratioMax")
                .trackRatio(r)
                .apply()
            writeRunning()
            return null
        }
        val lastTime = prefs.getLong("lastTime", now)
        val dt = (now - lastTime).coerceAtLeast(0)
        val current = chargingCurrent(r)
        val lastCurrent = prefs.getInt("lastCurrent", current)
        // Trapezoid rule between two samples; mA * ms -> mAh.
        val added = (current + lastCurrent) / 2.0 * dt / 3_600_000.0
        prefs.edit()
            .putFloat("mah", (prefs.getFloat("mah", 0f) + added).toFloat())
            .putFloat("currentTime", (prefs.getFloat("currentTime", 0f) + current.toDouble() * dt).toFloat())
            .putLong("lastTime", now)
            .putInt("lastCurrent", current)
            .putInt("maxCurrent", maxOf(prefs.getInt("maxCurrent", 0), current))
            .putInt("maxTemp", maxOf(prefs.getInt("maxTemp", 0), r.tempDeciC))
            .putInt("lastLevel", r.level)
            .putInt("lastCounter", r.chargeCounterMah ?: prefs.getInt("lastCounter", -1))
            // A pause with a jump in level means the service was not running; the sum is then incomplete.
            // A long pause alone is normal (slow charger, phone asleep between percent steps).
            .putBoolean(
                "broken",
                prefs.getBoolean("broken", false) ||
                    RecordingGap.isGap(lastTime, prefs.getInt("lastLevel", r.level), now, r.level),
            )
            .putBoolean("full", prefs.getBoolean("full", false) || r.isFull)
            .trackRatio(r)
            .apply()
        writeRunning()
        return null
    }

    private fun finish(now: Long): ChargeSession? {
        val start = prefs.getLong("start", now)
        val startLevel = prefs.getInt("startLevel", 0)
        val endLevel = prefs.getInt("lastLevel", startLevel)
        val mah = prefs.getFloat("mah", 0f).toDouble()
        val startCounter = prefs.getInt("startCounter", -1)
        val endCounter = prefs.getInt("lastCounter", -1)
        val counterDelta = if (startCounter >= 0 && endCounter >= 0) endCounter - startCounter else null
        val duration = (prefs.getLong("lastTime", now) - start).coerceAtLeast(1)
        val broken = prefs.getBoolean("broken", false)
        val fromLevel = counterDelta != null && Capacity.counterFollowsLevel(
            prefs.getFloat("ratioMin", 0f), prefs.getFloat("ratioMax", 0f), Capacity.designFromSystem(context),
        )
        val session = ChargeSession(
            startTime = start,
            endTime = now,
            startLevel = startLevel,
            endLevel = endLevel,
            plugged = prefs.getInt("plugged", 0),
            chargedMah = mah,
            counterDeltaMah = counterDelta,
            avgCurrentMa = (prefs.getFloat("currentTime", 0f) / duration).roundToInt(),
            maxCurrentMa = prefs.getInt("maxCurrent", 0),
            maxTempDeciC = prefs.getInt("maxTemp", 0),
            capacityMah = if (broken) null else Capacity.fromSession(startLevel, endLevel, mah, counterDelta.takeUnless { fromLevel }),
            endedFull = prefs.getBoolean("full", false),
            counterFromLevel = fromLevel,
        ).let {
            it.copy(
                noCapacityReason = when {
                    it.capacityMah != null -> null
                    endLevel - startLevel < Capacity.MIN_LEVEL_CHANGE -> "SHORT"
                    broken -> "BROKEN"
                    else -> "IMPLAUSIBLE"
                },
            )
        }
        prefs.edit().clear().apply()
        runningFile.delete()
        // Very short plug-ins without gain are not worth a row.
        return session.takeIf { endLevel > startLevel && now - start >= 60_000L }
    }

    /** Current counts only while the battery is really charging. */
    private fun chargingCurrent(r: BatteryReading) = if (r.isCharging) r.currentMa ?: 0 else 0

    companion object {
        const val RUNNING_FILE = "running_session.txt"

        /** Reads the snapshot written by the watcher: start time, start level, level now, mAh so far, broken. */
        fun readRunning(context: Context): Running? = runCatching {
            val f = java.io.File(context.filesDir, RUNNING_FILE)
            if (!f.exists()) return null
            val p = f.readText().split(";")
            Running(p[0].toLong(), p[1].toInt(), p[2].toInt(), p[3].toDouble(), p[4].toBoolean())
        }.getOrNull()
    }

    data class Running(val start: Long, val startLevel: Int, val level: Int, val mah: Double, val broken: Boolean)
}
