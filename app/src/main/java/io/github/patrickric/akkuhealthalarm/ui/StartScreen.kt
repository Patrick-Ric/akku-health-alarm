package io.github.patrickric.akkuhealthalarm.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.Schedule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.patrickric.akkuhealthalarm.R
import io.github.patrickric.akkuhealthalarm.app
import io.github.patrickric.akkuhealthalarm.data.RemainingTime
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings
import io.github.patrickric.akkuhealthalarm.data.BatteryReading
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import io.github.patrickric.akkuhealthalarm.data.QuickChoice
import io.github.patrickric.akkuhealthalarm.data.quickChoices
import io.github.patrickric.akkuhealthalarm.data.Vendor
import io.github.patrickric.akkuhealthalarm.data.ToneMode
import io.github.patrickric.akkuhealthalarm.data.VoiceMode
import io.github.patrickric.akkuhealthalarm.service.MonitorService
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

typealias Update = ((AlarmSettings) -> AlarmSettings) -> Unit

enum class Screen { START, SETTINGS, HISTORY, HEALTH }

@Composable
fun StartScreen(settings: AlarmSettings, reading: BatteryReading?, update: Update, onSettings: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        // The bottom navigation bar below takes care of the system bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GaugeCard(settings, reading, remainingText(settings, reading))
            SetupCard(settings, update, onSettings)
            AlarmRangeCard(settings, update)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun GaugeCard(settings: AlarmSettings, reading: BatteryReading?, remaining: String?) {
    // Smaller ring on short screens, so the start page fits without scrolling.
    val maxRing = (LocalConfiguration.current.screenHeightDp * 0.3f).dp.coerceIn(150.dp, 250.dp)
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BatteryGauge(
                level = reading?.level ?: 0,
                low = settings.low,
                high = settings.high,
                charging = reading?.isCharging == true,
                statusText = reading?.let { stringResource(Format.statusRes(it)) } ?: "",
                modifier = Modifier.widthIn(max = maxRing).fillMaxWidth().layout { measurable, constraints ->
                    // The ring is open at the bottom; give that empty strip back to the layout.
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, (placeable.height * 0.86f).toInt()) { placeable.place(0, 0) }
                }.aspectRatio(1f),
            )
            if (remaining != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(remaining, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallMetric(Icons.Rounded.Bolt, reading?.currentMa?.let(Format::milliAmps), R.string.metric_current)
                    SmallMetric(Icons.Rounded.Thermostat, reading?.let { Format.celsius(it.tempC) }, R.string.metric_temp)
                    SmallMetric(
                        Icons.Rounded.ElectricalServices,
                        reading?.voltageMv?.takeIf { it > 0 }?.let(Format::volts),
                        R.string.metric_voltage,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallMetric(icon: ImageVector, value: String?, label: Int) {
    val description = stringResource(label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$description ${value ?: ""}" },
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(value ?: "–", style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun SetupCard(settings: AlarmSettings, update: Update, onSettings: () -> Unit) {
    val context = LocalContext.current
    val (setup, refresh) = rememberSetupState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
        // Re-post the status notification now that it may be shown.
        MonitorService.start(context)
    }
    AnimatedVisibility(visible = !setup.complete) {
        val extra = LocalExtraColors.current
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = extra.cautionContainer, contentColor = extra.onCautionContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(R.string.setup_text), style = MaterialTheme.typography.bodyMedium)
                if (setup.needsNotifications) {
                    Button(
                        onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = extra.caution, contentColor = extra.onCaution),
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.setup_notifications))
                    }
                }
                if (setup.needsBatteryOptimization) {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = extra.caution, contentColor = extra.onCaution),
                        onClick = {
                            openBatteryOptimization(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.setup_battery_opt))
                    }
                }
            }
        }
    }
    // Extra settings of some phone makers; shown until the user says they are done.
    val vendor = remember { Vendor.current() }
    AnimatedVisibility(visible = setup.complete && vendor.needsExtraSteps && !settings.vendorHintDone) {
        val extra = LocalExtraColors.current
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = extra.cautionContainer, contentColor = extra.onCautionContainer),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.setup_vendor, vendor.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = { update { it.copy(vendorHintDone = true) } }) { Text(stringResource(R.string.setup_vendor_done)) }
                    Button(
                        onClick = onSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = extra.caution, contentColor = extra.onCaution),
                    ) { Text(stringResource(R.string.setup_vendor_show)) }
                }
            }
        }
    }
}

@Composable
private fun AlarmRangeCard(settings: AlarmSettings, update: Update) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    // Local copy while dragging; saved when the finger lifts.
    var range by remember(settings.low, settings.high) { mutableStateOf(settings.low.toFloat()..settings.high.toFloat()) }
    val low = range.start.roundToInt()
    val high = range.endInclusive.roundToInt()
    val quick = settings.quickChoices()
    val selected = quick.indexOf(low to high).takeIf { it >= 0 }
    var editSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    val scheme = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    if (settings.enabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    container = if (settings.enabled) scheme.primaryContainer else scheme.surfaceContainerHighest,
                    content = if (settings.enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.enabled) {
                            stringResource(R.string.alarm_on_summary, settings.low, settings.high)
                        } else {
                            stringResource(R.string.alarm_off_summary)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.range_info))
                }
                Switch(checked = settings.enabled, onCheckedChange = { on -> update { it.copy(enabled = on) } })
            }
            Spacer(Modifier.height(12.dp))
            QuickChoiceRow(quick, selected, onPick = { i -> setRange(update, quick[i].first, quick[i].second) }, onEdit = { editSlot = it })
            Text(
                (selected?.let { quickName(it, quick[it]) + " · " } ?: "") + stringResource(R.string.usable, high - low),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (!settings.quickHintDone) {
                Text(
                    stringResource(R.string.quick_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RangeSlider(
                value = range,
                onValueChange = { r ->
                    // The lower limit must stay below the upper one.
                    range = if (r.endInclusive - r.start >= 1f) r else range
                },
                valueRange = 1f..100f,
                // Read the state at call time: a quick tap or flick ends before the next frame,
                // and low/high of this composition would still hold the old values.
                onValueChangeFinished = { setRange(update, range.start.roundToInt(), range.endInclusive.roundToInt()) },
            )
            Row(verticalAlignment = Alignment.Top) {
                ValueStepper(
                    stringResource(R.string.alarm_below),
                    low,
                    "%",
                    scheme.error,
                    min = 1,
                    max = high - 1,
                    onChange = { setRange(update, it, high) },
                    modifier = Modifier.weight(1f),
                    hint = stringResource(R.string.recommendation, "10–40 %"),
                )
                ValueStepper(
                    stringResource(R.string.alarm_above),
                    high,
                    "%",
                    extra.caution,
                    min = low + 1,
                    max = 100,
                    onChange = { setRange(update, low, it) },
                    modifier = Modifier.weight(1f),
                    alignEnd = true,
                    hint = stringResource(R.string.recommendation, "50–80 %"),
                )
            }
            RepeatRow(settings, update)
        }
    }

    editSlot?.let { slot -> QuickEditDialog(slot, settings, low to high, update) { editSlot = null } }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = { Icon(Icons.Rounded.Eco, contentDescription = null) },
            title = { Text(stringResource(R.string.range_info)) },
            text = { Text(stringResource(R.string.range_info_text)) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** Saves the limits. Slider and steppers never change a quick choice. */
private fun setRange(update: Update, low: Int, high: Int) = update { it.copy(low = low, high = high) }

/** "Standard" for the fixed one; the editable ones keep their gentle names until changed. */
@Composable
private fun quickName(index: Int, range: Pair<Int, Int>): String = when {
    index == 0 -> stringResource(R.string.preset_standard)
    index == 1 && range == QuickChoice.DEFAULT_2 -> stringResource(R.string.preset_gentle)
    index == 2 && range == QuickChoice.DEFAULT_3 -> stringResource(R.string.preset_very_gentle)
    else -> stringResource(R.string.quick_slot, index + 1)
}

/** Three quick choices: tap to use; hold one of the two right ones to change it. */
@Composable
private fun QuickChoiceRow(quick: List<Pair<Int, Int>>, selected: Int?, onPick: (Int) -> Unit, onEdit: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outline
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(50))
            .border(1.dp, outline, RoundedCornerShape(50)),
    ) {
        quick.forEachIndexed { i, (lo, hi) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(outline))
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (selected == i) scheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .combinedClickable(
                        onClick = { onPick(i) },
                        onLongClick = if (i > 0) ({ onEdit(i) }) else null,
                        onLongClickLabel = if (i > 0) stringResource(R.string.quick_edit_title, i + 1) else null,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$lo–$hi",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected == i) scheme.onSecondaryContainer else scheme.onSurface,
                )
                // The pencil always marks the two buttons that can be changed by holding them.
                if (i > 0) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(14.dp),
                    )
                }
            }
        }
    }
}

/** Window to change an editable quick choice: two steppers, or take over the current range. */
@Composable
private fun QuickEditDialog(slot: Int, settings: AlarmSettings, current: Pair<Int, Int>, update: Update, onDone: () -> Unit) {
    val start = settings.quickChoices()[slot]
    var lo by rememberSaveable { mutableIntStateOf(start.first) }
    var hi by rememberSaveable { mutableIntStateOf(start.second) }
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.quick_edit_title, slot + 1)) },
        text = {
            Column {
                // One below the other: side by side the two steppers do not fit the dialog width.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ValueStepper(
                        stringResource(R.string.alarm_below), lo, "%", scheme.error, 1, hi - 1, { lo = it },
                        hint = stringResource(R.string.recommendation, "10–40 %"),
                    )
                }
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    ValueStepper(
                        stringResource(R.string.alarm_above), hi, "%", LocalExtraColors.current.caution, lo + 1, 100, { hi = it },
                        hint = stringResource(R.string.recommendation, "50–80 %"),
                    )
                }
                OutlinedButton(
                    onClick = {
                        lo = current.first
                        hi = current.second
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text(stringResource(R.string.quick_take_current)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                update {
                    val changed = if (slot == 1) it.copy(quick2Low = lo, quick2High = hi) else it.copy(quick3Low = lo, quick3High = hi)
                    // Saving also uses the new range right away.
                    changed.copy(low = lo, high = hi, quickHintDone = true)
                }
                onDone()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val d = if (slot == 1) QuickChoice.DEFAULT_2 else QuickChoice.DEFAULT_3
                    lo = d.first
                    hi = d.second
                }) { Text(stringResource(R.string.quick_reset)) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** "80 % in about 25 min" towards the next alarm limit, or null while there is too little data. */
@Composable
private fun remainingText(settings: AlarmSettings, reading: BatteryReading?): String? {
    val dao = LocalContext.current.app.db.dao()
    val readings by remember { dao.readingsSince(System.currentTimeMillis() - 4 * 60 * 60 * 1000L) }
        .collectAsStateWithLifecycle(emptyList())
    if (reading == null) return null
    val target = if (reading.isPlugged) settings.high else settings.low
    val points = readings.map { RemainingTime.Point(it.time, it.level, it.plugged != 0) } +
        RemainingTime.Point(System.currentTimeMillis(), reading.level, reading.isPlugged)
    val ms = RemainingTime.estimate(points, target) ?: return null
    val duration = durationText((ms / 60_000L).toInt())
    return stringResource(R.string.eta, target, duration)
}

/** Asks Android to exempt the app from battery optimization; falls back to the overview list. */
fun openBatteryOptimization(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

/** "Repeat: off / 10 s … 12 h" – repeats the alarm while the situation lasts. */
@Composable
private fun RepeatRow(settings: AlarmSettings, update: Update) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Repeat, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.repeat_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(repeatLabel(settings.repeatSeconds))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                QuickChoice.REPEAT_STEPS.forEach { sec ->
                    DropdownMenuItem(
                        text = { Text(repeatLabel(sec)) },
                        onClick = {
                            update { it.copy(repeatSeconds = sec) }
                            open = false
                        },
                    )
                }
            }
        }
    }
    if (settings.repeatSeconds > 0) RepeatCountdown()
    if (settings.repeatSeconds in 1 until 540) {
        Text(
            stringResource(R.string.repeat_doze_hint),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "Next alarm in 0:07" while a repeat is planned; the watcher (other process) writes the time to a file. */
@Composable
private fun RepeatCountdown() {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    val next = remember(now / 1000) { MonitorService.readNextRepeat(context) } ?: return
    val left = ((next.first - now + 999) / 1000).coerceAtLeast(0)
    val time = if (left >= 3600) {
        "%d:%02d:%02d".format(left / 3600, left / 60 % 60, left % 60)
    } else {
        "%d:%02d".format(left / 60, left % 60)
    }
    Text(
        stringResource(R.string.repeat_next, time),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 4.dp),
    )
}

@Composable
fun repeatLabel(seconds: Int): String = when {
    seconds == 0 -> stringResource(R.string.repeat_off)
    seconds < 60 -> stringResource(R.string.duration_sec, seconds)
    else -> durationText(seconds / 60)
}
