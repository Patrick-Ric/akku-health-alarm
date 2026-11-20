package io.github.patrickric.akkuhealthalarm.service

import android.app.AlarmManager
import kotlinx.coroutines.withContext
import io.github.patrickric.akkuhealthalarm.data.RemainingTime
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import io.github.patrickric.akkuhealthalarm.AppLanguage
import io.github.patrickric.akkuhealthalarm.BuildConfig
import io.github.patrickric.akkuhealthalarm.app
import io.github.patrickric.akkuhealthalarm.data.AlarmDiag
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings
import io.github.patrickric.akkuhealthalarm.data.BatteryReading
import io.github.patrickric.akkuhealthalarm.data.Capacity
import io.github.patrickric.akkuhealthalarm.data.EventEntry
import io.github.patrickric.akkuhealthalarm.data.EventType
import io.github.patrickric.akkuhealthalarm.data.ReadingEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs in the foreground and reacts to every battery change at once, so an alarm comes
 * exactly at the set percentage and not minutes later.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: AlarmPlayer
    private lateinit var sessions: SessionTracker
    private var sampler: Job? = null
    private var settings: AlarmSettings? = null
    private var last: BatteryReading? = null
    private var lastLogTime = 0L
    private var lastPrune = 0L
    private var lastStatus: Notifications.StatusContent? = null

    /** Readings of the last hours for the remaining time in the status notification. */
    private var etaPoints: List<RemainingTime.Point> = emptyList()
    private var etaKey: Pair<Int, Boolean>? = null
    private var etaLoadedAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            BatteryReading.fromIntent(context, intent)?.let(::onReading)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this)
        sessions = SessionTracker(this)
        scope.launch(Dispatchers.IO) { repairCounterSessions() }
        scope.launch {
            app.settings.settings.collect { s ->
                settings = s
                // Switched off while an alarm is shown: take it away at once.
                if (!s.alarmNotification) AlarmType.entries.forEach { Notifications.cancelAlarm(this@MonitorService, it) }
                prune(s, force = true)
                last?.let { r ->
                    updateStatus(r, s)
                    evaluate(r, s)
                }
            }
        }
        // BATTERY_CHANGED is a protected system broadcast. RECEIVER_NOT_EXPORTED would make
        // AndroidX add a permission on Android 12 and older that blocks it.
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() call must be answered with startForeground().
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REPEAT) {
            AlarmType.entries.getOrNull(intent.getIntExtra(EXTRA_TYPE, -1))?.let(::repeatAlarm)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(): Boolean {
        val content = lastStatus
            ?: Notifications.statusContent(this, BatteryReading.current(this), settings ?: AlarmSettings())
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        return try {
            ServiceCompat.startForeground(this, Notifications.ID_STATUS, Notifications.status(this, content), type)
            true
        } catch (e: Exception) {
            // Android 12+ refuses a start from the background in some cases; the app starts it again when opened.
            false
        }
    }

    private fun onReading(r: BatteryReading) {
        val prev = last
        last = r
        log(prev, r)
        trackSession(r)
        val s = settings ?: return
        prune(s, force = false)
        loadEtaPoints(r)
        updateStatus(r, s)
        evaluate(r, s)
    }

    /** Reloads the recent readings when the level or the plug changed, at most every 2 minutes otherwise. */
    private fun loadEtaPoints(r: BatteryReading) {
        val key = r.level to r.isPlugged
        val now = System.currentTimeMillis()
        if (key == etaKey && now - etaLoadedAt < 2 * 60_000L) return
        etaKey = key
        etaLoadedAt = now
        scope.launch {
            val rows = withContext(Dispatchers.IO) { app.db.dao().readingsBetween(now - 4 * 60 * 60_000L, now) }
            etaPoints = rows.map { RemainingTime.Point(it.time, it.level, it.plugged != 0) }
            val s = settings ?: return@launch
            last?.let { updateStatus(it, s) }
        }
    }

    private fun updateStatus(r: BatteryReading, s: AlarmSettings) {
        val target = if (r.isPlugged) s.high else s.low
        // Only while charging: on battery the time depends on screen on or off, which the lock screen cannot say.
        val eta = if (s.enabled && r.isPlugged) {
            RemainingTime.estimate(etaPoints + RemainingTime.Point(System.currentTimeMillis(), r.level, r.isPlugged), target)
        } else {
            null
        }
        val content = Notifications.statusContent(this, r, s, eta?.let { (it / 60_000L).toInt() })
        if (content == lastStatus) return
        lastStatus = content
        Notifications.updateStatus(this, content)
    }

    private fun evaluate(r: BatteryReading, s: AlarmSettings) {
        val before = EngineStore.load(this)
        val result = AlarmEngine.evaluate(r.level, r.isPlugged, r.tempC, s, before)
        if (result.state != before) EngineStore.save(this, result.state)
        result.clear.forEach {
            Notifications.cancelAlarm(this, it)
            stopRepeat(it)
        }
        // Only the first alarm makes a sound; the others are shown and logged as notifications.
        val delivery = result.fire.firstOrNull()?.let { type ->
            player.play(type, r.level, s) { saveDiag(type, it, repeat = false) }
        }
        result.fire.forEachIndexed { i, type ->
            scheduleRepeat(type, s, r.isPlugged)
            if (s.alarmNotification) Notifications.showAlarm(this, type, r, repeats[type]?.nextAt)
            logEvent(eventType(type), r, if (i == 0) delivery?.name else AlarmPlayer.Delivery.SILENT_BY_SETTINGS.name)
        }
        if (s.repeatSeconds == 0 || !s.enabled) AlarmType.entries.forEach(::stopRepeat)
        syncRepeats(s, r.isPlugged)
    }

    // ---- Repeating an alarm while the situation lasts ----

    private val repeatJobs = mutableMapOf<AlarmType, Job>()
    private var repeatWakeLock: PowerManager.WakeLock? = null

    /** Planned repeats: interval and wall-clock time of the next one, shown as a countdown. */
    private data class Planned(val seconds: Int, val nextAt: Long)
    private val repeats = mutableMapOf<AlarmType, Planned>()

    /**
     * Starts the repeat for alarms that are still active but have none planned with the current interval:
     * the repeat was switched on or changed after the alarm, or the watcher was restarted (update, kill).
     */
    private fun syncRepeats(s: AlarmSettings, plugged: Boolean) {
        if (s.repeatSeconds <= 0 || !s.enabled) return
        AlarmType.entries.forEach { type ->
            if (stillActive(type) && repeats[type]?.seconds != s.repeatSeconds) scheduleRepeat(type, s, plugged)
        }
    }

    private fun plan(type: AlarmType, seconds: Int) {
        repeats[type] = Planned(seconds, System.currentTimeMillis() + seconds * 1000L)
        writeNextRepeat()
    }

    /** Earliest planned repeat for the app screen (other process): "time;type", no file when none. */
    private fun writeNextRepeat() {
        val f = java.io.File(filesDir, NEXT_REPEAT_FILE)
        val next = repeats.minByOrNull { it.value.nextAt }
        runCatching { if (next == null) f.delete() else f.writeText("${next.value.nextAt};${next.key.ordinal}") }
    }

    /** Still a reason for this alarm? The engine re-arms an alarm as soon as the situation is over. */
    private fun stillActive(type: AlarmType): Boolean {
        val st = EngineStore.load(this)
        return when (type) {
            AlarmType.HIGH -> !st.highArmed
            AlarmType.LOW -> !st.lowArmed
            AlarmType.HOT -> !st.hotArmed
            AlarmType.COLD -> !st.coldArmed
        }
    }

    private fun scheduleRepeat(type: AlarmType, s: AlarmSettings, plugged: Boolean) {
        val seconds = s.repeatSeconds
        if (seconds <= 0) return
        stopRepeat(type)
        if (plugged) {
            // On the charger the phone has power: a precise timer that keeps the CPU awake.
            repeatJobs[type] = scope.launch {
                while (true) {
                    plan(type, seconds)
                    holdWake(seconds * 1000L + 5_000)
                    delay(seconds * 1000L)
                    if (!stillActive(type)) break
                    repeatAlarm(type, reschedule = false)
                }
                if (repeats.remove(type) != null) writeNextRepeat()
                releaseWake()
            }
        } else {
            // On battery Android allows a wake-up in deep sleep only every few minutes.
            plan(type, seconds)
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + seconds * 1000L,
                repeatIntent(type),
            )
        }
    }

    private fun repeatAlarm(type: AlarmType, reschedule: Boolean = true) {
        val s = settings ?: return
        val r = last ?: BatteryReading.current(this) ?: return
        if (!s.enabled || s.repeatSeconds <= 0 || !stillActive(type)) {
            stopRepeat(type)
            return
        }
        player.play(type, r.level, s) { saveDiag(type, it, repeat = true) }
        if (reschedule) scheduleRepeat(type, s, r.isPlugged)
        // The loop on the charger plans its next round itself, right after this call.
        val nextAt = if (reschedule) repeats[type]?.nextAt else System.currentTimeMillis() + s.repeatSeconds * 1000L
        if (s.alarmNotification) Notifications.showAlarm(this, type, r, nextAt)
        scope.launch(Dispatchers.IO) { app.db.dao().countRepeat(eventType(type), System.currentTimeMillis()) }
    }

    private fun stopRepeat(type: AlarmType) {
        repeatJobs.remove(type)?.cancel()
        (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(repeatIntent(type))
        if (repeats.remove(type) != null) writeNextRepeat()
        if (repeatJobs.isEmpty()) releaseWake()
    }

    private fun repeatIntent(type: AlarmType): PendingIntent {
        val intent = Intent(this, MonitorService::class.java).setAction(ACTION_REPEAT).putExtra(EXTRA_TYPE, type.ordinal)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(this, 100 + type.ordinal, intent, flags)
        } else {
            PendingIntent.getService(this, 100 + type.ordinal, intent, flags)
        }
    }

    private fun holdWake(ms: Long) {
        val lock = repeatWakeLock ?: (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AkkuHealthAlarm:repeat")
            .apply { setReferenceCounted(false) }
            .also { repeatWakeLock = it }
        lock.acquire(ms)
    }

    private fun releaseWake() {
        repeatWakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun log(prev: BatteryReading?, r: BatteryReading) {
        val now = System.currentTimeMillis()
        val changed = prev == null || prev.level != r.level || prev.plugged != r.plugged || prev.status != r.status
        if (changed || now - lastLogTime >= LOG_INTERVAL_MS) {
            lastLogTime = now
            val entry = ReadingEntry(
                time = now,
                level = r.level,
                plugged = r.plugged,
                status = r.status,
                voltageMv = r.voltageMv,
                tempDeciC = r.tempDeciC,
                currentMa = r.currentMa,
                chargeCounterMah = r.chargeCounterMah,
            )
            scope.launch(Dispatchers.IO) { app.db.dao().insertReading(entry) }
        }
        if (prev != null && prev.isPlugged != r.isPlugged) {
            logEvent(if (r.isPlugged) EventType.PLUGGED else EventType.UNPLUGGED, r)
        }
    }

    /**
     * Charging sessions: while plugged in, the current is sampled every few seconds so the
     * charged mAh can be added up; on unplugging the session is stored.
     */
    /**
     * Sessions measured with a counter that turned out to be only level x rated capacity (older versions
     * did not check this): measure them again from the current. The readings of the charge must still exist.
     */
    private suspend fun repairCounterSessions() {
        val dao = app.db.dao()
        val design = Capacity.designFromSystem(this)
        dao.counterSessions().forEach { s ->
            val ratios = dao.readingsBetween(s.startTime, s.endTime).mapNotNull { r ->
                r.chargeCounterMah?.takeIf { it > 0 && r.level > 0 }?.let { it * 100f / r.level }
            }
            if (ratios.size < 5 || !Capacity.counterFollowsLevel(ratios.min(), ratios.max(), design)) return@forEach
            val capacity = Capacity.fromSession(s.startLevel, s.endLevel, s.chargedMah, null)
            dao.updateSession(
                s.copy(counterFromLevel = true, capacityMah = capacity, noCapacityReason = if (capacity == null) "IMPLAUSIBLE" else null),
            )
        }
    }

    private fun trackSession(r: BatteryReading) {
        sessions.update(r, System.currentTimeMillis())?.let { finished ->
            scope.launch(Dispatchers.IO) {
                app.db.dao().insertSession(finished)
                checkCapacityHint()
            }
        }
        if (r.isPlugged && sampler?.isActive != true) {
            sampler = scope.launch {
                while (true) {
                    delay(SAMPLE_INTERVAL_MS)
                    val now = BatteryReading.current(this@MonitorService) ?: continue
                    if (!now.isPlugged) break
                    sessions.update(now, System.currentTimeMillis())
                }
            }
        } else if (!r.isPlugged) {
            sampler?.cancel()
            sampler = null
        }
    }

    /** One silent hint when the measured capacity falls below 80 % of the rated value. */
    private suspend fun checkCapacityHint() {
        val s = settings ?: return
        val design = s.designCapacityMah.takeIf { it > 0 } ?: Capacity.designFromSystem(this) ?: return
        val recent = app.db.dao().recentCapacitySessions(50)
        val result = Capacity.estimate(Capacity.basis(recent)) ?: return
        // Only when the measurement is clearly below 80 %, not when it merely might be.
        if (result.sessions < 3 || result.mah + result.plusMinusMah >= design * 0.8) return
        val estimate = result.mah
        val prefs = getSharedPreferences("hints", MODE_PRIVATE)
        if (!prefs.getBoolean("capacity_low", false)) {
            prefs.edit().putBoolean("capacity_low", true).apply()
            Notifications.showCapacityHint(this, estimate, design)
        }
    }

    /** Deletes history older than the chosen keeping time. */
    private fun prune(s: AlarmSettings, force: Boolean) {
        val now = System.currentTimeMillis()
        if (s.keepDays <= 0 || (!force && now - lastPrune < PRUNE_INTERVAL_MS)) return
        lastPrune = now
        val before = now - s.keepDays * DAY_MS
        scope.launch(Dispatchers.IO) {
            val dao = app.db.dao()
            dao.deleteReadingsBefore(before)
            dao.deleteEventsBefore(before)
        }
    }

    private fun logEvent(type: String, r: BatteryReading, delivery: String? = null) {
        val entry = EventEntry(
            time = System.currentTimeMillis(),
            type = type,
            level = r.level,
            tempDeciC = r.tempDeciC,
            delivery = delivery,
        )
        scope.launch(Dispatchers.IO) {
            val db = app.db
            db.withTransaction {
                val dao = db.dao()
                if (EventType.continuesRow(dao.newestEvent(), type, entry.time)) {
                    dao.countRepeat(type, entry.time)
                } else {
                    dao.insertEvent(entry)
                }
            }
        }
    }

    /**
     * Stores how the sound really went with the newest alarm row of this type (first alarm or latest
     * repeat). Test builds also keep every single play in a small text log (readable with adb run-as).
     */
    private fun saveDiag(type: AlarmType, diag: AlarmDiag, repeat: Boolean) {
        val text = diag.encode()
        val now = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            val dao = app.db.dao()
            if (repeat) dao.setLastDiag(eventType(type), text) else dao.setDiag(eventType(type), text)
            if (BuildConfig.DEBUG) runCatching {
                val f = java.io.File(filesDir, DIAG_LOG_FILE)
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date(now))
                f.appendText("$stamp ${eventType(type)} ${if (repeat) "repeat" else "first"} $text\n")
                val lines = f.readLines()
                if (lines.size > DIAG_LOG_LINES * 2) f.writeText(lines.takeLast(DIAG_LOG_LINES).joinToString("\n", postfix = "\n"))
            }
        }
    }

    private fun eventType(type: AlarmType) = when (type) {
        AlarmType.HIGH -> EventType.ALARM_HIGH
        AlarmType.LOW -> EventType.ALARM_LOW
        AlarmType.HOT -> EventType.ALARM_HOT
        AlarmType.COLD -> EventType.ALARM_COLD
    }

    companion object {
        private const val LOG_INTERVAL_MS = 10 * 60 * 1000L
        private const val PRUNE_INTERVAL_MS = 60 * 60 * 1000L
        private const val SAMPLE_INTERVAL_MS = 20_000L
        private const val ACTION_REPEAT = "io.github.patrickric.akkuhealthalarm.REPEAT"
        private const val EXTRA_TYPE = "type"
        const val NEXT_REPEAT_FILE = "next_repeat.txt"
        private const val DIAG_LOG_FILE = "alarm_sound.log"
        private const val DIAG_LOG_LINES = 500

        /** Next planned repeat written by the watcher: wall-clock time and alarm type, or null. */
        fun readNextRepeat(context: Context): Pair<Long, AlarmType>? = runCatching {
            val f = java.io.File(context.filesDir, NEXT_REPEAT_FILE)
            if (!f.exists()) return null
            val p = f.readText().split(";")
            p[0].toLong() to AlarmType.entries[p[1].toInt()]
        }.getOrNull()
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Starts the watcher again, e.g. so that its notifications use a newly chosen language. */
        fun restart(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
            start(context)
        }

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            } catch (e: Exception) {
                // Not allowed right now (background start limits); the next app start tries again.
            }
        }
    }
}
