package io.github.patrickric.akkuhealthalarm.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

/** One snapshot of the battery, taken from ACTION_BATTERY_CHANGED plus BatteryManager. */
data class BatteryReading(
    val level: Int,
    val plugged: Int,
    val status: Int,
    val health: Int,
    val voltageMv: Int,
    val tempDeciC: Int,
    val technology: String?,
    /** Always positive; the direction follows from [isPlugged]. Null if the phone does not report it. */
    val currentMa: Int?,
    val chargeCounterMah: Int?,
) {
    val isPlugged get() = plugged != 0
    val tempC get() = tempDeciC / 10f
    val isCharging get() = status == BatteryManager.BATTERY_STATUS_CHARGING
    val isFull get() = status == BatteryManager.BATTERY_STATUS_FULL

    companion object {
        /** Set once a value in µA was seen; phones that report mA never get there. Kept per device. */
        @Volatile
        private var reportsMicroAmps: Boolean? = null

        fun fromIntent(context: Context, intent: Intent): BatteryReading? {
            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (rawLevel < 0 || scale <= 0) return null
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            return BatteryReading(
                level = (rawLevel * 100f / scale).roundToInt().coerceIn(0, 100),
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
                voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
                tempDeciC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
                technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
                currentMa = readCurrentMa(context, bm),
                chargeCounterMah = counter.takeIf { it > 0 && it != Int.MIN_VALUE }?.let { it / 1000 },
            )
        }

        fun current(context: Context): BatteryReading? {
            val intent = ContextCompat.registerReceiver(
                context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED,
            ) ?: return null
            return fromIntent(context, intent)
        }

        /** Android documents µA, but some phones report mA, and the sign differs between makers. */
        private fun readCurrentMa(context: Context, bm: BatteryManager): Int? {
            val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (raw == 0 || raw == Int.MIN_VALUE) return null
            val value = abs(raw)
            val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
            val micro = reportsMicroAmps ?: prefs.getBoolean("micro_amps", false).also { reportsMicroAmps = it }
            if (!micro && value >= 20_000) {
                // Over 20 A is impossible, so this phone reports µA; remember it for good.
                reportsMicroAmps = true
                prefs.edit().putBoolean("micro_amps", true).apply()
            }
            return if (reportsMicroAmps == true) value / 1000 else value
        }
    }
}
