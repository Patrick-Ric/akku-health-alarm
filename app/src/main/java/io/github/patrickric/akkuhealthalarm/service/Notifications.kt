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
    const val CH_STATUS = "status"
    const val CH_ALARM = "alarm"
    const val CH_HINTS = "hints"
    private const val ID_CAPACITY_HINT = 30
    const val ID_STATUS = 1

    private fun alarmId(type: AlarmType) = 10 + type.ordinal

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
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

    /** Text parts of the status notification; also used to skip needless updates. */
    data class StatusContent(val level: Int?, val title: String, val text: String)

    fun statusContent(context: Context, r: BatteryReading?, s: AlarmSettings): StatusContent {
        if (r == null) return StatusContent(null, context.getString(R.string.app_name), "")
        val state = context.getString(Format.statusRes(r))
        val title = "${r.level} % · $state"
        val target = when {
            !s.enabled -> context.getString(R.string.notif_alarm_off)
            r.isPlugged -> context.getString(R.string.notif_alarm_at, s.high)
            else -> context.getString(R.string.notif_alarm_at, s.low)
        }
        val parts = buildList {
            add(target)
            if (s.showCurrent) r.currentMa?.let { add(Format.milliAmps(it)) }
            if (s.showTemp) add(Format.celsius(r.tempC))
            if (s.showVoltage && r.voltageMv > 0) add(Format.volts(r.voltageMv))
        }
        return StatusContent(if (s.percentIcon) r.level else null, title, parts.joinToString(" · "))
    }

    fun status(context: Context, content: StatusContent): Notification {
        val builder = NotificationCompat.Builder(context, CH_STATUS)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
