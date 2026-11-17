package io.github.patrickric.akkuhealthalarm.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.patrickric.akkuhealthalarm.MainActivity
import io.github.patrickric.akkuhealthalarm.R
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings
import io.github.patrickric.akkuhealthalarm.data.BatteryReading
import io.github.patrickric.akkuhealthalarm.ui.Format

object Notifications {
    /**
     * Normal importance (without sound), so the status shows as its own card in the shade and on the
     * lock screen. The former channel "status" had low importance (only a small icon on the lock
     * screen); an app cannot raise that later, hence the new channel.
     */
    const val CH_STATUS = "battery_status"
    private const val CH_STATUS_OLD = "status"
    const val CH_ALARM = "alarm"
    const val CH_HINTS = "hints"
    private const val ID_CAPACITY_HINT = 30
    const val ID_STATUS = 1

    private fun alarmId(type: AlarmType) = 10 + type.ordinal

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Refused while a foreground service still shows a notification of it; then next time.
        runCatching { nm.deleteNotificationChannel(CH_STATUS_OLD) }
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply {
                        setShowBadge(false)
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    },
                // The app plays tone and voice itself, so the channel stays silent.
                NotificationChannel(CH_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                    },
                NotificationChannel(CH_HINTS, context.getString(R.string.channel_hints), NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Text parts of the status notification; also used to skip needless updates.
     * [text] is the line shown when collapsed (also on the lock screen), [details] the extra line when expanded.
     */
    data class StatusContent(val level: Int?, val title: String, val text: String, val details: String = "")

    /**
     * Title "66 % · Charging · Alarm at 67 %". Line (also collapsed and on the lock screen): time until the
     * alarm and repeat rhythm, "in about 1 h 5 min · every 30 s". Expanded also "331 mA · 27.5 °C · 4.01 V".
     * Split like this so the collapsed card is not cut off on narrow phones with a large font.
     * [etaMinutes]: time until the limit is reached, if it can be estimated.
     */
    fun statusContent(context: Context, r: BatteryReading?, s: AlarmSettings, etaMinutes: Int? = null): StatusContent {
        if (r == null) return StatusContent(null, context.getString(R.string.app_name), "")
        val state = context.getString(Format.statusRes(r))
        // Charging: the upper limit; on battery: the lower one.
        val limit = if (r.isPlugged) s.high else s.low
        val alarm = if (s.enabled) context.getString(R.string.notif_alarm_at, limit) else context.getString(R.string.notif_alarm_off)
        val title = "${r.level} % · $state · $alarm"
        val text = if (!s.enabled) {
            ""
        } else {
            listOfNotNull(
                etaMinutes?.let { context.getString(R.string.notif_eta_in, Format.duration(context, it)) },
                if (s.repeatSeconds == 0) {
                    context.getString(R.string.notif_repeat_once)
                } else {
                    context.getString(R.string.notif_repeat_every, repeatText(context, s.repeatSeconds))
                },
            ).joinToString(" · ")
        }
        val details = buildList {
            if (s.showCurrent) r.currentMa?.let { add(Format.milliAmps(it)) }
            if (s.showTemp) add(Format.celsius(r.tempC))
            if (s.showVoltage && r.voltageMv > 0) add(Format.volts(r.voltageMv))
        }.joinToString(" · ")
        // Alarm off: the measured values move up into the visible line.
        return if (text.isEmpty()) {
            StatusContent(if (s.percentIcon) r.level else null, title, details)
        } else {
            StatusContent(if (s.percentIcon) r.level else null, title, text, details)
        }
    }

    /** "30 s", "5 min", "1 h" like the choice on the start page. */
    private fun repeatText(context: Context, seconds: Int): String =
        if (seconds < 60) context.getString(R.string.duration_sec, seconds) else Format.duration(context, seconds / 60)

    fun status(context: Context, content: StatusContent): Notification {
        val builder = NotificationCompat.Builder(context, CH_STATUS)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            // From Android 8 the channel decides sound and pop-up; the priority only sorts cards of the same
            // importance. High puts the status before other quiet cards, so it gets one of the few places on the
            // lock screen (Moto: 3). Android 7 has no channels: there high could pop up, so normal priority.
            .setPriority(if (Build.VERSION.SDK_INT >= 26) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (content.details.isEmpty()) content.text else content.text + "\n" + content.details),
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(ContextCompat.getColor(context, R.color.brand))
        if (content.level != null) {
            builder.setSmallIcon(IconCompat.createWithBitmap(percentBitmap(context, content.level)))
        } else {
            builder.setSmallIcon(R.drawable.ic_stat_battery)
        }
        return builder.build()
    }

    fun updateStatus(context: Context, content: StatusContent) {
        notify(context, ID_STATUS, status(context, content))
    }

    /** [nextAt]: wall-clock time of the next repeat; the notification then counts down to it. */
    fun showAlarm(context: Context, type: AlarmType, r: BatteryReading, nextAt: Long? = null) {
        val (title, text) = when (type) {
            AlarmType.HIGH -> context.getString(R.string.alarm_high_title, r.level) to context.getString(R.string.alarm_high_text)
            AlarmType.LOW -> context.getString(R.string.alarm_low_title, r.level) to context.getString(R.string.alarm_low_text)
            AlarmType.HOT -> context.getString(R.string.alarm_hot_title, Format.celsius(r.tempC)) to
                context.getString(R.string.alarm_hot_text)
            AlarmType.COLD -> context.getString(R.string.alarm_cold_title, Format.celsius(r.tempC)) to
                context.getString(R.string.alarm_cold_text)
        }
        val n = NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .apply {
                if (nextAt != null) setWhen(nextAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            }
            .build()
        notify(context, alarmId(type), n)
    }

    fun showCapacityHint(context: Context, estimateMah: Int, designMah: Int) {
        val percent = estimateMah * 100 / designMah
        val n = NotificationCompat.Builder(context, CH_HINTS)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(context.getString(R.string.hint_capacity_title, percent))
            .setContentText(
                context.getString(R.string.hint_capacity_text, Format.milliAmpHours(estimateMah), Format.milliAmpHours(designMah)),
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.hint_capacity_text, Format.milliAmpHours(estimateMah), Format.milliAmpHours(designMah)),
                ),
            )
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .build()
        notify(context, ID_CAPACITY_HINT, n)
    }

    fun cancelAlarm(context: Context, type: AlarmType) {
        NotificationManagerCompat.from(context).cancel(alarmId(type))
    }

    private fun notify(context: Context, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, n)
    }

    /** White number on transparent ground, as Android expects for status bar icons. */
    private fun percentBitmap(context: Context, level: Int): Bitmap {
        val size = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val text = level.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * if (text.length >= 3) 0.62f else 0.82f
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, size / 2f, y, paint)
        return bitmap
    }
}
