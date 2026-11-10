package io.github.patrickric.akkuhealthalarm.ui

import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.patrickric.akkuhealthalarm.R
import io.github.patrickric.akkuhealthalarm.app
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings
import io.github.patrickric.akkuhealthalarm.data.BatteryReading
import io.github.patrickric.akkuhealthalarm.data.Capacity
import io.github.patrickric.akkuhealthalarm.data.ChargeSession
import io.github.patrickric.akkuhealthalarm.service.SessionTracker
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay

@Composable
fun HealthScreen(reading: BatteryReading?, settings: AlarmSettings, update: Update, onShowChart: (Overlay) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val sessions by remember { context.app.db.dao().sessions() }.collectAsStateWithLifecycle(emptyList())
    val systemDesign = remember { Capacity.designFromSystem(context) }
    val design = settings.designCapacityMah.takeIf { it > 0 } ?: systemDesign
    val measured = sessions.filter { it.capacityMah != null }
    val basis = Capacity.basis(sessions)
    val estimate = Capacity.estimate(basis)

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.health_title)) },
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConditionCard(estimate, design, reading, counterFromLevel = basis.any { it.counterFromLevel })
            if (measured.size >= 2) {
                SectionCard(stringResource(R.string.capacity_trend), Icons.Rounded.Timeline) {
                    CapacityChart(measured.reversed(), design, Modifier.fillMaxWidth().height(160.dp))
                }
            }
            RunningSessionCard()
            DesignCard(settings, systemDesign, update)
            if (sessions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        Icons.Rounded.Loop,
                        String.format(LocalConfiguration.current.locales[0], "%.1f", Capacity.cycles(sessions)),
                        stringResource(R.string.cycles_title),
                        Modifier.weight(1f),
                    )
                    MetricTile(
                        Icons.Rounded.BatteryChargingFull,
                        sessions.size.toString(),
                        stringResource(R.string.sessions_count),
                        Modifier.weight(1f),
                    )
                }
                SpeedCard(sessions)
            }
            SessionsCard(sessions.take(10))
            MeasuresCard(reading, onShowChart)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConditionCard(estimate: Capacity.Estimate?, design: Int?, reading: BatteryReading?, counterFromLevel: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current
    val percent = if (estimate != null && design != null) estimate.mah * 100 / design else null
    val percentPm = if (estimate != null && design != null) (estimate.plusMinusMah * 100 / design).coerceAtLeast(1) else null
    val good = percent == null || percent >= 80
    val container = if (good) scheme.primaryContainer else extra.cautionContainer
    val content = if (good) scheme.onPrimaryContainer else extra.onCautionContainer
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.MonitorHeart, container = content.copy(alpha = 0.12f), content = content, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.health_state), style = MaterialTheme.typography.labelLarge)
                    Text(
                        when {
                            percent != null -> "$percent ± $percentPm %"
                            estimate != null -> Format.milliAmpHours(estimate.mah)
                            else -> stringResource(R.string.health_measuring)
                        },
                        style = if (estimate != null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (estimate != null) {
                val measuredText = Format.number(estimate.mah) + " ± " + Format.milliAmpHours(estimate.plusMinusMah)
                Text(
                    if (design != null) {
                        stringResource(R.string.health_capacity_of, measuredText, Format.milliAmpHours(design))
                    } else {
                        measuredText
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    pluralStringResource(R.plurals.health_from_sessions, estimate.sessions, estimate.sessions, estimate.totalPercent),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.health_estimate_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(stringResource(R.string.health_need_charge), style = MaterialTheme.typography.bodyMedium)
            }
            if (counterFromLevel) {
                Text(
                    stringResource(R.string.health_counter_from_level),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.health_android_says,
                    stringResource(Format.healthRes(reading?.health ?: BatteryManager.BATTERY_HEALTH_UNKNOWN)),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun CapacityChart(sessions: List<ChargeSession>, design: Int?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val values = sessions.mapNotNull { it.capacityMah }
    val top = maxOf(design ?: 0, values.max()) * 1.15f
    val bottom = minOf(values.min() * 0.85f, (design ?: values.min()) * 0.6f)
    Canvas(modifier) {
        val left = 44.dp.toPx()
        val w = size.width - left - 8.dp.toPx()
        val h = size.height - 8.dp.toPx()
        fun y(v: Float) = 4.dp.toPx() + h * (1f - (v - bottom) / (top - bottom))
        fun x(i: Int) = left + if (sessions.size == 1) w / 2 else w * i / (sessions.size - 1)
        design?.let { d ->
            drawLine(scheme.outline, Offset(left, y(d.toFloat())), Offset(left + w, y(d.toFloat())), strokeWidth = 1.dp.toPx())
            drawLine(scheme.error.copy(alpha = 0.6f), Offset(left, y(d * 0.8f)), Offset(left + w, y(d * 0.8f)), strokeWidth = 1.dp.toPx())
            listOf(d to "100 %", (d * 0.8f).toInt() to "80 %").forEach { (v, label) ->
                val m = measurer.measure(label, labelStyle)
                drawText(m, topLeft = Offset(left - m.size.width - 6.dp.toPx(), y(v.toFloat()) - m.size.height / 2f))
            }
        }
        val path = Path()
        sessions.forEachIndexed { i, s ->
            val p = Offset(x(i), y(s.capacityMah!!.toFloat()))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, scheme.primary.copy(alpha = 0.5f), style = Stroke(2.dp.toPx()))
        sessions.forEachIndexed { i, s ->
            val c = s.capacityMah!!.toFloat()
            // Error bar: about 95 % range of this single charge.
            val e = (2 * Capacity.sessionError(s) * c).toFloat()
            drawLine(scheme.primary.copy(alpha = 0.6f), Offset(x(i), y(c - e)), Offset(x(i), y(c + e)), strokeWidth = 2.dp.toPx())
            drawCircle(scheme.primary, 4.dp.toPx(), Offset(x(i), y(c)))
        }
    }
}

@Composable
private fun DesignCard(settings: AlarmSettings, systemDesign: Int?, update: Update) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val manual = settings.designCapacityMah > 0
    val value = if (manual) settings.designCapacityMah else systemDesign
    SectionCard(
        stringResource(R.string.design_title),
        Icons.Rounded.BatteryChargingFull,
        action = { TextButton(onClick = { editing = true }) { Text(stringResource(R.string.design_edit)) } },
    ) {
        if (value != null) {
            Text(Format.milliAmpHours(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(if (manual) R.string.design_manual else R.string.design_auto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.design_unknown), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (editing) {
        var text by rememberSaveable { mutableStateOf(value?.toString() ?: "") }
        val parsed = text.toIntOrNull()?.takeIf { it in 500..30_000 }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.design_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(5) },
                    suffix = { Text("mAh") },
                    supportingText = { Text(stringResource(R.string.design_dialog_hint)) },
                    singleLine = true,
                    isError = text.isNotEmpty() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = parsed != null,
                    onClick = {
                        update { it.copy(designCapacityMah = parsed ?: 0) }
                        editing = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        update { it.copy(designCapacityMah = 0) }
                        editing = false
                    }) { Text(stringResource(R.string.design_reset)) }
                    TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

private fun plugIcon(plugged: Int) = when (plugged) {
    BatteryManager.BATTERY_PLUGGED_USB -> Icons.Rounded.Usb
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> Icons.Rounded.WifiTethering
    else -> Icons.Rounded.Power
}

private fun plugLabel(plugged: Int) = when (plugged) {
    BatteryManager.BATTERY_PLUGGED_USB -> R.string.plug_usb
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.plug_wireless
    else -> R.string.plug_ac
}

/** Average speed per kind of charger, so USB port, charger and wireless pad can be compared. */
@Composable
private fun SpeedCard(sessions: List<ChargeSession>) {
    SectionCard(stringResource(R.string.speed_title), Icons.Rounded.Speed) {
        sessions.groupBy { it.plugged }.forEach { (plugged, group) ->
            val hours = group.sumOf { (it.endTime - it.startTime).toDouble() } / 3_600_000.0
            val percent = group.sumOf { it.endLevel - it.startLevel }
            val avgMa = if (hours > 0) {
                (group.sumOf { it.avgCurrentMa.toDouble() * (it.endTime - it.startTime) } / (hours * 3_600_000.0)).toInt()
            } else {
                0
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(plugIcon(plugged), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(plugLabel(plugged)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    stringResource(
                        R.string.speed_row,
                        Format.milliAmps(avgMa),
                        if (hours > 0) (percent / hours).toInt().toString() else "–",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SessionsCard(sessions: List<ChargeSession>) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.sessions_title), Icons.Rounded.BatteryChargingFull) {
        if (sessions.isEmpty()) {
            Text(stringResource(R.string.sessions_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        sessions.forEachIndexed { index, s ->
            if (index > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(plugIcon(s.plugged), container = scheme.primaryContainer, content = scheme.onPrimaryContainer, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${s.startLevel} → ${s.endLevel} %" + if (s.endedFull) " ✓ " + stringResource(R.string.session_full) else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        android.text.format.DateUtils.formatDateTime(
                            context,
                            s.startTime,
                            android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME or
                                android.text.format.DateUtils.FORMAT_ABBREV_MONTH or Format.clockFlag(context),
                        ) + " · " + durationText(((s.endTime - s.startTime) / 60_000L).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        "Ø " + Format.milliAmps(s.avgCurrentMa) + " · " +
                            stringResource(R.string.session_cycles, String.format(LocalConfiguration.current.locales[0], "%.2f", (s.endLevel - s.startLevel) / 100.0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                s.capacityMah?.let {
                    Text("≈ " + Format.milliAmpHours(it), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                } ?: noCapacityText(s.noCapacityReason)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 110.dp))
                }
            }
        }
    }
}

/** Current values; the first three open the history chart with that value. */
@Composable
private fun MeasuresCard(reading: BatteryReading?, onShowChart: (Overlay) -> Unit) {
    SectionCard(stringResource(R.string.measures_title), Icons.Rounded.Straighten) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                Icons.Rounded.Thermostat,
                reading?.let { Format.celsius(it.tempC) } ?: "–",
                stringResource(R.string.metric_temp),
                Modifier.weight(1f),
                onClick = { onShowChart(Overlay.TEMP) },
            )
            MetricTile(
                Icons.Rounded.ElectricalServices,
                reading?.voltageMv?.takeIf { it > 0 }?.let(Format::volts) ?: "–",
                stringResource(R.string.metric_voltage),
                Modifier.weight(1f),
                onClick = { onShowChart(Overlay.VOLTAGE) },
            )
            MetricTile(
                Icons.Rounded.Bolt,
                reading?.currentMa?.let(Format::milliAmps) ?: "–",
                stringResource(R.string.metric_current),
                Modifier.weight(1f),
                onClick = { onShowChart(Overlay.CURRENT) },
            )
        }
        Text(
            stringResource(R.string.measures_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                Icons.Rounded.Science,
                reading?.technology?.takeIf { it.isNotBlank() } ?: "–",
                stringResource(R.string.health_technology),
                Modifier.weight(1f),
            )
            MetricTile(
                Icons.Rounded.DataUsage,
                reading?.chargeCounterMah?.let(Format::milliAmpHours) ?: "–",
                stringResource(R.string.health_charge_counter),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun noCapacityText(reason: String?): String? = when (reason) {
    "SHORT" -> stringResource(R.string.no_capacity_short)
    "BROKEN" -> stringResource(R.string.no_capacity_broken)
    "IMPLAUSIBLE" -> stringResource(R.string.no_capacity_implausible)
    else -> null
}

/** The charge that is running right now; the capacity is measured when it ends. */
@Composable
private fun RunningSessionCard() {
    val context = LocalContext.current
    val running by produceState<SessionTracker.Running?>(null) {
        while (true) {
            value = SessionTracker.readRunning(context)
            delay(3_000)
        }
    }
    val r = running ?: return
    val gained = r.level - r.startLevel
    SectionCard(stringResource(R.string.running_title), Icons.Rounded.BatteryChargingFull) {
        Text(
            "${r.startLevel} → ${r.level}\u00a0% · " + stringResource(R.string.running_since, Format.timeFormat(context).format(java.util.Date(r.start))),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.running_mah, Format.milliAmpHours(r.mah.toInt())),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            when {
                r.broken -> stringResource(R.string.no_capacity_broken)
                gained < Capacity.MIN_LEVEL_CHANGE -> stringResource(R.string.running_need, Capacity.MIN_LEVEL_CHANGE - gained)
                else -> stringResource(R.string.running_ready)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
