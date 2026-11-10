package io.github.patrickric.akkuhealthalarm.service

import android.content.Context
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings

enum class AlarmType { HIGH, LOW, HOT, COLD }

/** Which alarms may fire again. An alarm fires once and is re-armed only after the situation is over. */
data class EngineState(
    val highArmed: Boolean = true,
    val lowArmed: Boolean = true,
    val hotArmed: Boolean = true,
    val coldArmed: Boolean = true,
)

data class EngineResult(
    val state: EngineState,
    val fire: List<AlarmType>,
    /** Alarms whose notification can go away because the situation is over. */
    val clear: List<AlarmType>,
)

/** Pure decision logic, without Android dependencies, so it can be unit-tested. */
object AlarmEngine {
    /** Percent the level must move back before the same alarm can fire again while still plugged/unplugged. */
    const val LEVEL_HYSTERESIS = 3
    const val TEMP_HYSTERESIS = 2

    fun evaluate(level: Int, plugged: Boolean, tempC: Float, s: AlarmSettings, st: EngineState): EngineResult {
        val fire = mutableListOf<AlarmType>()
        val clear = mutableListOf<AlarmType>()
        var high = st.highArmed
        var low = st.lowArmed
        var hot = st.hotArmed
        var cold = st.coldArmed

        if (!s.enabled) {
            if (!high) clear += AlarmType.HIGH
            if (!low) clear += AlarmType.LOW
            high = true
            low = true
        } else {
            if (!high && (!plugged || level <= s.high - LEVEL_HYSTERESIS)) {
                high = true
                clear += AlarmType.HIGH
            }
            if (!low && (plugged || level >= s.low + LEVEL_HYSTERESIS)) {
                low = true
                clear += AlarmType.LOW
            }
            if (high && plugged && level >= s.high) {
                high = false
                fire += AlarmType.HIGH
            }
            if (low && !plugged && level <= s.low) {
                low = false
                fire += AlarmType.LOW
            }
        }

        if (!s.tempEnabled) {
            if (!hot) clear += AlarmType.HOT
            if (!cold) clear += AlarmType.COLD
            hot = true
            cold = true
        } else {
            val coldMatters = plugged || !s.coldOnlyCharging
            if (!hot && tempC <= s.tempHigh - TEMP_HYSTERESIS) {
                hot = true
                clear += AlarmType.HOT
            }
            if (!cold && (!coldMatters || tempC >= s.tempLow + TEMP_HYSTERESIS)) {
                cold = true
                clear += AlarmType.COLD
            }
            if (hot && tempC >= s.tempHigh) {
                hot = false
                fire += AlarmType.HOT
            }
            if (cold && coldMatters && tempC <= s.tempLow) {
                cold = false
                fire += AlarmType.COLD
            }
        }
        return EngineResult(EngineState(high, low, hot, cold), fire, clear)
    }
}

object QuietHours {
    /** True if [minute] (after midnight) lies in [start, end); the span may cross midnight. */
    fun contains(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }
}

/** Keeps the engine state across service restarts, so a restart does not repeat an alarm. */
object EngineStore {
    private const val FILE = "engine"

    fun load(context: Context): EngineState {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return EngineState(
            highArmed = p.getBoolean("high", true),
            lowArmed = p.getBoolean("low", true),
            hotArmed = p.getBoolean("hot", true),
            coldArmed = p.getBoolean("cold", true),
        )
    }

    fun save(context: Context, st: EngineState) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("high", st.highArmed)
            .putBoolean("low", st.lowArmed)
            .putBoolean("hot", st.hotArmed)
            .putBoolean("cold", st.coldArmed)
            .apply()
    }
}
