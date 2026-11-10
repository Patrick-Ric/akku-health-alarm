package io.github.patrickric.akkuhealthalarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Surface
import androidx.compose.ui.res.stringArrayResource
import io.github.patrickric.akkuhealthalarm.data.Vendor
import io.github.patrickric.akkuhealthalarm.data.ThemeMode
import androidx.compose.material.icons.rounded.Palette
import io.github.patrickric.akkuhealthalarm.service.MonitorService
import io.github.patrickric.akkuhealthalarm.BuildConfig
import io.github.patrickric.akkuhealthalarm.R
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings
import io.github.patrickric.akkuhealthalarm.data.TempPreset
import io.github.patrickric.akkuhealthalarm.data.ToneMode
import io.github.patrickric.akkuhealthalarm.data.VoiceMode
import io.github.patrickric.akkuhealthalarm.service.AlarmPlayer
import io.github.patrickric.akkuhealthalarm.service.AlarmType
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import io.github.patrickric.akkuhealthalarm.AppLanguage

@Composable
fun SettingsScreen(settings: AlarmSettings, update: Update, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { AlarmPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val reliabilityDone = rememberReliabilityDone(settings)
            if (!reliabilityDone) ReliabilityCard(settings, update, done = false)
            VoiceCard(settings, update, onTest = { type -> player.play(type, if (type == AlarmType.HIGH) settings.high else settings.low, settings, force = true) })
            ToneCard(settings, update, onPreview = { s -> player.previewTone(s) })
            QuietCard(settings, update)
            TemperatureCard(settings, update)
            NotificationCard(settings, update)
            KeepCard(settings, update)
            LanguageCard()
            ThemeCard(settings, update)
            if (reliabilityDone) ReliabilityCard(settings, update, done = true)
            SectionCard(stringResource(R.string.about_title), Icons.Rounded.Info) {
                Text(
                    stringResource(R.string.about_text, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                // One line keeps all buttons the same height. Less side padding gives "Handy-Stimme" room;
                // longer words in other languages get a slightly smaller font instead of a second line.
                contentPadding = PaddingValues(horizontal = 4.dp),
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
                    )
                },
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun VoiceCard(settings: AlarmSettings, update: Update, onTest: (AlarmType) -> Unit) {
    SectionCard(stringResource(R.string.voice_title), Icons.Rounded.RecordVoiceOver) {
        Segmented(
            listOf(
                VoiceMode.OFF to stringResource(R.string.voice_off),
                VoiceMode.BUILTIN to stringResource(R.string.voice_builtin),
                VoiceMode.SYSTEM to stringResource(R.string.voice_system),
            ),
            settings.voiceMode,
        ) { mode -> update { it.copy(voiceMode = mode) } }
        when (settings.voiceMode) {
            VoiceMode.BUILTIN -> Hint(stringResource(R.string.voice_builtin_hint))
            VoiceMode.SYSTEM -> {
                Hint(stringResource(R.string.voice_system_hint))
                SwitchRow(
                    stringResource(R.string.voice_percent),
                    settings.voicePercent,
                    { on -> update { it.copy(voicePercent = on) } },
                )
            }
            VoiceMode.OFF -> Unit
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.test_hint), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onTest(AlarmType.HIGH) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.test_charged))
            }
            FilledTonalButton(onClick = { onTest(AlarmType.LOW) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.test_low))
            }
        }
    }
}

@Composable
private fun ToneCard(settings: AlarmSettings, update: Update, onPreview: (AlarmSettings) -> Unit) {
    val context = LocalContext.current
    val pickTitle = stringResource(R.string.tone_pick)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            update { it.copy(toneUri = uri?.toString(), toneMode = if (uri != null) ToneMode.SYSTEM else ToneMode.BUILTIN) }
        }
    }
    val openPicker = {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pickTitle)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, settings.toneUri?.let(Uri::parse))
        runCatching { picker.launch(intent) }
    }
    val toneTitle = remember(settings.toneUri) {
        settings.toneUri?.let { uri -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull() }
    }

    SectionCard(stringResource(R.string.tone_title), Icons.Rounded.MusicNote) {
        Segmented(
            listOf(
                ToneMode.OFF to stringResource(R.string.tone_off),
                ToneMode.BUILTIN to stringResource(R.string.tone_builtin),
                ToneMode.SYSTEM to stringResource(R.string.tone_system),
            ),
            settings.toneMode,
        ) { mode ->
            update { it.copy(toneMode = mode) }
            when {
                mode == ToneMode.SYSTEM && settings.toneUri == null -> openPicker()
                mode != ToneMode.OFF -> onPreview(settings.copy(toneMode = mode))
            }
        }
        AnimatedVisibility(settings.toneMode != ToneMode.OFF) {
            FilledTonalButton(onClick = { onPreview(settings) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.tone_listen))
            }
        }
        AnimatedVisibility(settings.toneMode == ToneMode.SYSTEM) {
            OutlinedButton(onClick = { openPicker() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(toneTitle ?: stringResource(R.string.tone_none_selected), maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(stringResource(R.string.vibration_title), settings.vibrate, { on -> update { it.copy(vibrate = on) } })
        SwitchRow(
            stringResource(R.string.loud_title),
            settings.loudWhenSilent,
            { on -> update { it.copy(loudWhenSilent = on) } },
            summary = stringResource(R.string.loud_summary),
        )
        SwitchRow(
            stringResource(R.string.alarm_notif_title),
            settings.alarmNotification,
            { on -> update { it.copy(alarmNotification = on) } },
            summary = stringResource(R.string.alarm_notif_summary),
        )
    }
}

@Composable
private fun QuietCard(settings: AlarmSettings, update: Update) {
    var editing by rememberSaveable { mutableIntStateOf(0) } // 0 = none, 1 = start, 2 = end
    SectionCard(
        stringResource(R.string.quiet_title),
        Icons.Rounded.Bedtime,
        action = { Switch(checked = settings.quietEnabled, onCheckedChange = { on -> update { it.copy(quietEnabled = on) } }) },
    ) {
        Hint(stringResource(R.string.quiet_summary))
        AnimatedVisibility(settings.quietEnabled) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = 1 }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.quiet_from, Format.clock(settings.quietStart)))
                }
                OutlinedButton(onClick = { editing = 2 }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.quiet_to, Format.clock(settings.quietEnd)))
                }
            }
        }
    }
    if (editing != 0) {
        TimeDialog(
            initial = if (editing == 1) settings.quietStart else settings.quietEnd,
            onDismiss = { editing = 0 },
            onConfirm = { minutes ->
                val which = editing
                update { if (which == 1) it.copy(quietStart = minutes) else it.copy(quietEnd = minutes) }
                editing = 0
            },
        )
    }
}

@Composable
private fun TimeDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = Format.is24Hour(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun TemperatureCard(settings: AlarmSettings, update: Update) {
    var range by remember(settings.tempLow, settings.tempHigh) {
        mutableStateOf(settings.tempLow.toFloat()..settings.tempHigh.toFloat())
    }
    val low = range.start.roundToInt()
    val high = range.endInclusive.roundToInt()
    val preset = TempPreset.of(low, high)

    SectionCard(
        stringResource(R.string.temp_title),
        Icons.Rounded.Thermostat,
        action = { Switch(checked = settings.tempEnabled, onCheckedChange = { on -> update { it.copy(tempEnabled = on) } }) },
    ) {
        Hint(stringResource(R.string.temp_summary))
        AnimatedVisibility(settings.tempEnabled) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        TempPreset.DANGER to R.string.temp_danger,
                        TempPreset.NORMAL to R.string.temp_normal,
                        TempPreset.STRICT to R.string.temp_strict,
                    ).forEach { (p, label) ->
                        FilterChip(
                            selected = preset == p,
                            onClick = { update { it.copy(tempLow = p.low, tempHigh = p.high) } },
                            label = { Text(stringResource(label), maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
                Text(
                    stringResource(R.string.temp_range, low, high),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                RangeSlider(
                    value = range,
                    onValueChange = { r -> range = if (r.endInclusive - r.start >= 10f) r else range },
                    valueRange = -10f..60f,
                    onValueChangeFinished = { update { it.copy(tempLow = low, tempHigh = high) } },
                )
                Row(verticalAlignment = Alignment.Top) {
                    ValueStepper(
                        stringResource(R.string.alarm_below),
                        low,
                        "°C",
                        MaterialTheme.colorScheme.tertiary,
                        min = -10,
                        max = high - 10,
                        onChange = { v -> update { it.copy(tempLow = v) } },
                        modifier = Modifier.weight(1f),
                    )
                    ValueStepper(
                        stringResource(R.string.alarm_above),
                        high,
                        "°C",
                        MaterialTheme.colorScheme.error,
                        min = low + 10,
                        max = 60,
                        onChange = { v -> update { it.copy(tempHigh = v) } },
                        modifier = Modifier.weight(1f),
                        alignEnd = true,
                    )
                }
                if (preset == TempPreset.STRICT) Hint(stringResource(R.string.temp_strict_hint))
                SwitchRow(
                    stringResource(R.string.temp_cold_charging),
                    settings.coldOnlyCharging,
                    { on -> update { it.copy(coldOnlyCharging = on) } },
                    summary = stringResource(R.string.temp_cold_charging_summary),
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(settings: AlarmSettings, update: Update) {
    SectionCard(stringResource(R.string.notif_title), Icons.Rounded.Notifications) {
        Text(
            stringResource(R.string.notif_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        CheckRow(stringResource(R.string.notif_current), settings.showCurrent) { on -> update { it.copy(showCurrent = on) } }
        CheckRow(stringResource(R.string.notif_temp), settings.showTemp) { on -> update { it.copy(showTemp = on) } }
        CheckRow(stringResource(R.string.notif_voltage), settings.showVoltage) { on -> update { it.copy(showVoltage = on) } }
        CheckRow(stringResource(R.string.notif_percent_icon), settings.percentIcon) { on -> update { it.copy(percentIcon = on) } }
    }
}

@Composable
private fun KeepCard(settings: AlarmSettings, update: Update) {
    SectionCard(stringResource(R.string.keep_title), Icons.Rounded.History) {
        Hint(stringResource(R.string.keep_summary))
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                1 to R.string.keep_1d,
                7 to R.string.keep_7d,
                30 to R.string.keep_30d,
                365 to R.string.keep_365d,
                0 to R.string.keep_forever,
            ).forEach { (days, label) ->
                FilterChip(
                    selected = settings.keepDays == days,
                    onClick = { update { it.copy(keepDays = days) } },
                    label = { Text(stringResource(label)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
    }
}

/** Everything that keeps the app running in the background, including the maker's extra settings. */
@Composable
private fun rememberReliabilityDone(settings: AlarmSettings): Boolean {
    val vendor = remember { Vendor.current() }
    val (setup, _) = rememberSetupState()
    return setup.complete && (!vendor.needsExtraSteps || settings.vendorHintDone)
}

/** When everything is set up, the card shrinks to one line at the end and opens on tap. */
@Composable
private fun ReliabilityCard(settings: AlarmSettings, update: Update, done: Boolean) {
    var open by rememberSaveable(done) { mutableStateOf(!done) }
    if (!open) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconBadge(
                    Icons.Rounded.VerifiedUser,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 36.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reliability_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.reliability_all_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }
    ReliabilityDetails(settings, update)
}

@Composable
private fun ReliabilityDetails(settings: AlarmSettings, update: Update) {
    val context = LocalContext.current
    val vendor = remember { Vendor.current() }
    val (setup, refresh) = rememberSetupState()
    var openFailed by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
        MonitorService.start(context)
    }
    val linkCopied = stringResource(R.string.link_copied)

    SectionCard(stringResource(R.string.reliability_title), Icons.Rounded.VerifiedUser) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            StatusRow(stringResource(R.string.setup_notifications), done = !setup.needsNotifications) {
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        StatusRow(stringResource(R.string.setup_battery_opt), done = !setup.needsBatteryOptimization) {
            openBatteryOptimization(context)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (vendor.needsExtraSteps) stringResource(R.string.reliability_vendor, vendor.label) else stringResource(R.string.reliability_general),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        stringArrayResource(vendor.steps).forEachIndexed { i, step ->
            Row(Modifier.padding(top = 6.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (vendor.pages.isNotEmpty()) {
            FilledTonalButton(
                onClick = { openFailed = !vendor.openSettings(context) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reliability_open))
            }
            if (openFailed) Hint(stringResource(R.string.reliability_open_failed))
        }
        if (vendor.needsExtraSteps) {
            CheckRow(stringResource(R.string.reliability_vendor_confirmed), settings.vendorHintDone) { on ->
                update { it.copy(vendorHintDone = on) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.reliability_link_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Only copied, never opened: the app itself stays completely offline.
        Surface(
            onClick = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("dontkillmyapp", vendor.link))
                android.widget.Toast.makeText(context, linkCopied, android.widget.Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    vendor.link.removePrefix("https://"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.ContentCopy, contentDescription = linkCopied, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StatusRow(title: String, done: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else LocalExtraColors.current.caution,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (done) {
            Text(stringResource(R.string.reliability_ok), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onFix) { Text(stringResource(R.string.reliability_open)) }
        }
    }
}

/** App language: "like the phone" or one of the translations, each in its own name. */
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppLanguage.current(context)) }
    var open by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.language_title), Icons.Rounded.Language) {
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(current?.let(AppLanguage::nativeName) ?: stringResource(R.string.language_system), Modifier.weight(1f))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                (listOf(null) + AppLanguage.TAGS.sortedWith(compareBy(java.text.Collator.getInstance()) { AppLanguage.nativeName(it) })).forEach { tag ->
                    DropdownMenuItem(
                        text = { Text(tag?.let(AppLanguage::nativeName) ?: stringResource(R.string.language_system)) },
                        onClick = {
                            open = false
                            if (tag == current) return@DropdownMenuItem
                            current = tag
                            AppLanguage.set(context, tag)
                            if (android.os.Build.VERSION.SDK_INT < 33) {
                                // Android 13+ does both by itself.
                                MonitorService.restart(context)
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(settings: AlarmSettings, update: Update) {
    SectionCard(stringResource(R.string.theme_title), Icons.Rounded.Palette) {
        val options = buildList {
            // Android before 10 has no system design to follow.
            if (android.os.Build.VERSION.SDK_INT >= 29) add(ThemeMode.SYSTEM to stringResource(R.string.theme_system))
            add(ThemeMode.LIGHT to stringResource(R.string.theme_light))
            add(ThemeMode.DARK to stringResource(R.string.theme_dark))
        }
        Segmented(options, settings.themeMode) { mode -> update { it.copy(themeMode = mode) } }
    }
}
