package io.github.patrickric.akkuhealthalarm.ui

import android.os.BatteryManager
import io.github.patrickric.akkuhealthalarm.R
import io.github.patrickric.akkuhealthalarm.data.BatteryReading
import java.text.NumberFormat
import java.util.Locale

object Format {
    /**
     * 12 or 24 hours like the phone's own clock, not like the app language: the phone setting, or if that
     * is "automatic" the convention of the phone's language. An English app on a German phone shows 14:05.
     */
    fun is24Hour(context: android.content.Context): Boolean {
        val config = android.content.res.Configuration(context.resources.configuration)
        // From Android 13 even the system resources of the app carry the app language; ask for the phone's.
        val phoneLocales = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(android.app.LocaleManager::class.java)?.systemLocales
        } else {
            null
        } ?: android.content.res.Resources.getSystem().configuration.locales
        config.setLocales(phoneLocales)
        return android.text.format.DateFormat.is24HourFormat(context.createConfigurationContext(config))
    }

    /** Hours and minutes in the app language, 12 or 24 hours like the phone's clock. */
    fun timeFormat(context: android.content.Context): java.text.DateFormat {
        val locale = context.resources.configuration.locales[0]
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, if (is24Hour(context)) "Hm" else "hm")
        return java.text.SimpleDateFormat(pattern, locale)
    }

    /** Flag for DateUtils so it follows the phone's clock too. */
    fun clockFlag(context: android.content.Context): Int =
        if (is24Hour(context)) android.text.format.DateUtils.FORMAT_24HOUR else android.text.format.DateUtils.FORMAT_12HOUR

    fun statusRes(r: BatteryReading): Int = when {
        r.isPlugged && r.isFull -> R.string.status_full
        r.isCharging -> R.string.status_charging
        r.isPlugged -> R.string.status_plugged
        else -> R.string.status_discharging
    }

    fun healthRes(health: Int): Int = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> R.string.health_good
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.health_overheat
        BatteryManager.BATTERY_HEALTH_COLD -> R.string.health_cold
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.health_over_voltage
        BatteryManager.BATTERY_HEALTH_DEAD -> R.string.health_dead
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.health_failure
        else -> R.string.health_unknown
    }

    fun milliAmps(ma: Int): String = NumberFormat.getIntegerInstance().format(ma) + " mA"

    fun number(n: Int): String = NumberFormat.getIntegerInstance().format(n)

    fun milliAmpHours(mah: Int): String = NumberFormat.getIntegerInstance().format(mah) + " mAh"

    fun celsius(c: Float): String = String.format(Locale.getDefault(), "%.1f °C", c)

    fun volts(mv: Int): String = String.format(Locale.getDefault(), "%.2f V", mv / 1000f)

    /** "22" for full hours, otherwise "22:30". */
    fun shortClock(minutes: Int): String =
        if (minutes % 60 == 0) String.format(Locale.getDefault(), "%02d", minutes / 60) else clock(minutes)

    fun clock(minutes: Int): String = String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)
}
