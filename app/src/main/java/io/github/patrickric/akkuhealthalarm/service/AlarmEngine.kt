package io.github.patrickric.akkuhealthalarm.service

import android.content.Context
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings

enum class AlarmType { HIGH, LOW, HOT, COLD }

/**
 * Which alarms may fire again. An alarm fires once and is re-armed only after the situation is over.
 * The *At values are the limits an active alarm fired at (null: none, or saved by an older version).
 */
data class EngineState(
    val highArmed: Boolean = true,
    val lowArmed: Boolean = true,
    val hotArmed: Boolean = true,
    val coldArmed: Boolean = true,
    val highAt: Int? = null,
    val lowAt: Int? = null,
    val hotAt: Int? = null,
    val coldAt: Int? = null,
)

data class EngineResult(
    val state: EngineState,
    val fire: List<AlarmType>,
    /** Alarms whose notification can go away because the situation is over. */
    val clear: List<AlarmType>,
)

/** Pure decision logic, without Android dependencies, so it can be unit-tested. */
object AlarmEngine {
    /**
     * Percent the level must move back before the same alarm can fire again while still plugged/unplugged.
     * Only against a wobbling level: when the user moves the limit past the current value, the alarm ends at once.
     */
    const val LEVEL_HYSTERESIS = 3
    const val TEMP_HYSTERESIS = 2

    fun evaluate(level: Int, plugged: Boolean, tempC: Float, s: AlarmSettings, st: EngineState): EngineResult {
        val fire = mutableListOf<AlarmType>()
        val clear = mutableListOf<AlarmType>()
        var high = st.highArmed
        var low = st.lowArmed
        var hot = st.hotArmed
        var cold = st.coldArmed
        var highAt = st.highAt
        var lowAt = st.lowAt
        var hotAt = st.hotAt
        var coldAt = st.coldAt
        // The user changed the limit since the alarm fired.
        fun moved(at: Int?, limit: Int) = at != null && at != limit

        if (!s.enabled) {
            if (!high) clear += AlarmType.HIGH
            if (!low) clear += AlarmType.LOW
            high = true
            low = true
        } else {
            if (!high && (!plugged || level <= s.high - LEVEL_HYSTERESIS || (moved(highAt, s.high) && level < s.high))) {
                high = true
                clear += AlarmType.HIGH
            }
            if (!low && (plugged || level >= s.low + LEVEL_HYSTERESIS || (moved(lowAt, s.low) && level > s.low))) {
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
            if (!hot && (tempC <= s.tempHigh - TEMP_HYSTERESIS || (moved(hotAt, s.tempHigh) && tempC < s.tempHigh))) {
                hot = true
                clear += AlarmType.HOT
            }
            if (!cold && (!coldMatters || tempC >= s.tempLow + TEMP_HYSTERESIS || (moved(coldAt, s.tempLow) && tempC > s.tempLow))) {
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
        highAt = if (high) null else highAt ?: s.high
        lowAt = if (low) null else lowAt ?: s.low
        hotAt = if (hot) null else hotAt ?: s.tempHigh
        coldAt = if (cold) null else coldAt ?: s.tempLow
        return EngineResult(EngineState(high, low, hot, cold, highAt, lowAt, hotAt, coldAt), fire, clear)
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
            highAt = p.intOrNull("highAt"),
            lowAt = p.intOrNull("lowAt"),
            hotAt = p.intOrNull("hotAt"),
            coldAt = p.intOrNull("coldAt"),
        )
    }

    fun save(context: Context, st: EngineState) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("high", st.highArmed)
            .putBoolean("low", st.lowArmed)
            .putBoolean("hot", st.hotArmed)
            .putBoolean("cold", st.coldArmed)
            .putIntOrRemove("highAt", st.highAt)
            .putIntOrRemove("lowAt", st.lowAt)
            .putIntOrRemove("hotAt", st.hotAt)
            .putIntOrRemove("coldAt", st.coldAt)
            .apply()
    }

    private fun android.content.SharedPreferences.intOrNull(key: String) = if (contains(key)) getInt(key, 0) else null

    private fun android.content.SharedPreferences.Editor.putIntOrRemove(key: String, value: Int?) =
        if (value == null) remove(key) else putInt(key, value)
}
