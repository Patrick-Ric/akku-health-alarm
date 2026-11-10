package io.github.patrickric.akkuhealthalarm.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.createMultiProcessCoordinator
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class VoiceMode { OFF, BUILTIN, SYSTEM }

enum class ToneMode { OFF, BUILTIN, SYSTEM }

/** App design. Android before 10 has no system dark mode, so there the app starts dark. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        val default get() = if (android.os.Build.VERSION.SDK_INT >= 29) SYSTEM else DARK
    }
}

data class AlarmSettings(
    val enabled: Boolean = true,
    val low: Int = 20,
    val high: Int = 80,
    /** The two editable quick choices; the first one (20–80) is fixed. */
    val quick2Low: Int = QuickChoice.DEFAULT_2.first,
    val quick2High: Int = QuickChoice.DEFAULT_2.second,
    val quick3Low: Int = QuickChoice.DEFAULT_3.first,
    val quick3High: Int = QuickChoice.DEFAULT_3.second,
    /** The hint "hold a button to change it" disappears after the first change. */
    val quickHintDone: Boolean = false,
    /** Repeat an alarm while the situation lasts, every this many seconds; 0 = once. */
    val repeatSeconds: Int = 30,
    val voiceMode: VoiceMode = VoiceMode.BUILTIN,
    val voicePercent: Boolean = false,
    val toneMode: ToneMode = ToneMode.BUILTIN,
    val toneUri: String? = null,
    val vibrate: Boolean = true,
    val loudWhenSilent: Boolean = false,
    /** Show a notification for each alarm; off = only tone, voice and vibration. */
    val alarmNotification: Boolean = true,
    val quietEnabled: Boolean = true,
    /** Minutes after midnight. */
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 7 * 60,
    val tempEnabled: Boolean = false,
    val tempLow: Int = 10,
    val tempHigh: Int = 40,
    val coldOnlyCharging: Boolean = true,
    val showCurrent: Boolean = false,
    val showTemp: Boolean = false,
    val showVoltage: Boolean = false,
    val percentIcon: Boolean = false,
    /** How long the history is kept, in days; 0 = forever. */
    val keepDays: Int = 365,
    /** Rated capacity entered by the user in mAh; 0 = read it from the phone. */
    val designCapacityMah: Int = 0,
    /** The user confirmed the extra steps for the phone maker. */
    val vendorHintDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.default,
)

/** Quick choices on the start page: 20–80 fixed, two editable with these defaults. */
object QuickChoice {
    val STANDARD = 20 to 80
    val DEFAULT_2 = 25 to 70
    val DEFAULT_3 = 30 to 60

    /** Choices for repeating an alarm, in seconds (0 = once). */
    val REPEAT_STEPS = listOf(0, 10, 15, 30, 60, 180, 300, 600, 900, 1800, 3600, 10800, 21600, 43200)
}

fun AlarmSettings.quickChoices(): List<Pair<Int, Int>> =
    listOf(QuickChoice.STANDARD, quick2Low to quick2High, quick3Low to quick3High)

enum class TempPreset(val low: Int, val high: Int) {
    DANGER(0, 45),
    NORMAL(10, 40),
    STRICT(15, 35),
    ;

    companion object {
        fun of(low: Int, high: Int) = entries.firstOrNull { it.low == low && it.high == high }
    }
}

/**
 * The app and the watcher run in two processes and both read and write the settings, so the
 * store must coordinate between processes. Same file as the former single-process store.
 */
private object SettingsStore {
    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> = instance ?: synchronized(this) {
        instance ?: run {
            val file = context.applicationContext.preferencesDataStoreFile("settings")
            MultiProcessDataStoreFactory.create(
                storage = OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = PreferencesSerializer,
                    coordinatorProducer = { _, _ -> createMultiProcessCoordinator(Dispatchers.IO, file) },
                    producePath = { file.absolutePath.toPath() },
                ),
            ).also { instance = it }
        }
    }
}

private val Context.dataStore: DataStore<Preferences> get() = SettingsStore.get(this)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val LOW = intPreferencesKey("low")
        val HIGH = intPreferencesKey("high")
        val QUICK2_LOW = intPreferencesKey("quick2_low")
        val QUICK2_HIGH = intPreferencesKey("quick2_high")
        val QUICK3_LOW = intPreferencesKey("quick3_low")
        val QUICK3_HIGH = intPreferencesKey("quick3_high")
        val QUICK_HINT_DONE = booleanPreferencesKey("quick_hint_done")
        val REPEAT_SECONDS = intPreferencesKey("repeat_seconds")
        val VOICE_MODE = stringPreferencesKey("voice_mode")
        val VOICE_PERCENT = booleanPreferencesKey("voice_percent")
        val TONE_MODE = stringPreferencesKey("tone_mode")
        val TONE_URI = stringPreferencesKey("tone_uri")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val LOUD_WHEN_SILENT = booleanPreferencesKey("loud_when_silent")
        val ALARM_NOTIFICATION = booleanPreferencesKey("alarm_notification")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val TEMP_ENABLED = booleanPreferencesKey("temp_enabled")
        val TEMP_LOW = intPreferencesKey("temp_low")
        val TEMP_HIGH = intPreferencesKey("temp_high")
        val COLD_ONLY_CHARGING = booleanPreferencesKey("cold_only_charging")
        val SHOW_CURRENT = booleanPreferencesKey("show_current")
        val SHOW_TEMP = booleanPreferencesKey("show_temp")
        val SHOW_VOLTAGE = booleanPreferencesKey("show_voltage")
        val PERCENT_ICON = booleanPreferencesKey("percent_icon")
        val KEEP_DAYS = intPreferencesKey("keep_days")
        val DESIGN_CAPACITY = intPreferencesKey("design_capacity")
        val VENDOR_HINT_DONE = booleanPreferencesKey("vendor_hint_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AlarmSettings> = context.dataStore.data.map(::read)

    suspend fun update(transform: (AlarmSettings) -> AlarmSettings) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): AlarmSettings {
        val d = AlarmSettings()
        return AlarmSettings(
            enabled = p[Keys.ENABLED] ?: d.enabled,
            low = p[Keys.LOW] ?: d.low,
            high = p[Keys.HIGH] ?: d.high,
            quick2Low = p[Keys.QUICK2_LOW] ?: d.quick2Low,
            quick2High = p[Keys.QUICK2_HIGH] ?: d.quick2High,
            quick3Low = p[Keys.QUICK3_LOW] ?: d.quick3Low,
            quick3High = p[Keys.QUICK3_HIGH] ?: d.quick3High,
            quickHintDone = p[Keys.QUICK_HINT_DONE] ?: d.quickHintDone,
            repeatSeconds = p[Keys.REPEAT_SECONDS] ?: d.repeatSeconds,
            voiceMode = p[Keys.VOICE_MODE]?.let { runCatching { VoiceMode.valueOf(it) }.getOrNull() } ?: d.voiceMode,
            voicePercent = p[Keys.VOICE_PERCENT] ?: d.voicePercent,
            toneMode = p[Keys.TONE_MODE]?.let { runCatching { ToneMode.valueOf(it) }.getOrNull() } ?: d.toneMode,
            toneUri = p[Keys.TONE_URI],
            vibrate = p[Keys.VIBRATE] ?: d.vibrate,
            loudWhenSilent = p[Keys.LOUD_WHEN_SILENT] ?: d.loudWhenSilent,
            alarmNotification = p[Keys.ALARM_NOTIFICATION] ?: d.alarmNotification,
            quietEnabled = p[Keys.QUIET_ENABLED] ?: d.quietEnabled,
            quietStart = p[Keys.QUIET_START] ?: d.quietStart,
            quietEnd = p[Keys.QUIET_END] ?: d.quietEnd,
            tempEnabled = p[Keys.TEMP_ENABLED] ?: d.tempEnabled,
            tempLow = p[Keys.TEMP_LOW] ?: d.tempLow,
            tempHigh = p[Keys.TEMP_HIGH] ?: d.tempHigh,
            coldOnlyCharging = p[Keys.COLD_ONLY_CHARGING] ?: d.coldOnlyCharging,
            showCurrent = p[Keys.SHOW_CURRENT] ?: d.showCurrent,
            showTemp = p[Keys.SHOW_TEMP] ?: d.showTemp,
            showVoltage = p[Keys.SHOW_VOLTAGE] ?: d.showVoltage,
            percentIcon = p[Keys.PERCENT_ICON] ?: d.percentIcon,
            keepDays = p[Keys.KEEP_DAYS] ?: d.keepDays,
            designCapacityMah = p[Keys.DESIGN_CAPACITY] ?: d.designCapacityMah,
            vendorHintDone = p[Keys.VENDOR_HINT_DONE] ?: d.vendorHintDone,
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
        )
    }

    private fun write(p: MutablePreferences, s: AlarmSettings) {
        p[Keys.ENABLED] = s.enabled
        p[Keys.LOW] = s.low
        p[Keys.HIGH] = s.high
        p[Keys.QUICK2_LOW] = s.quick2Low
        p[Keys.QUICK2_HIGH] = s.quick2High
        p[Keys.QUICK3_LOW] = s.quick3Low
        p[Keys.QUICK3_HIGH] = s.quick3High
        p[Keys.QUICK_HINT_DONE] = s.quickHintDone
        p[Keys.REPEAT_SECONDS] = s.repeatSeconds
        p[Keys.VOICE_MODE] = s.voiceMode.name
        p[Keys.VOICE_PERCENT] = s.voicePercent
        p[Keys.TONE_MODE] = s.toneMode.name
        if (s.toneUri != null) p[Keys.TONE_URI] = s.toneUri else p.remove(Keys.TONE_URI)
        p[Keys.VIBRATE] = s.vibrate
        p[Keys.LOUD_WHEN_SILENT] = s.loudWhenSilent
        p[Keys.ALARM_NOTIFICATION] = s.alarmNotification
        p[Keys.QUIET_ENABLED] = s.quietEnabled
        p[Keys.QUIET_START] = s.quietStart
        p[Keys.QUIET_END] = s.quietEnd
        p[Keys.TEMP_ENABLED] = s.tempEnabled
        p[Keys.TEMP_LOW] = s.tempLow
        p[Keys.TEMP_HIGH] = s.tempHigh
        p[Keys.COLD_ONLY_CHARGING] = s.coldOnlyCharging
        p[Keys.SHOW_CURRENT] = s.showCurrent
        p[Keys.SHOW_TEMP] = s.showTemp
        p[Keys.SHOW_VOLTAGE] = s.showVoltage
        p[Keys.PERCENT_ICON] = s.percentIcon
        p[Keys.KEEP_DAYS] = s.keepDays
        p[Keys.DESIGN_CAPACITY] = s.designCapacityMah
        p[Keys.VENDOR_HINT_DONE] = s.vendorHintDone
        p[Keys.THEME_MODE] = s.themeMode.name
    }
}
