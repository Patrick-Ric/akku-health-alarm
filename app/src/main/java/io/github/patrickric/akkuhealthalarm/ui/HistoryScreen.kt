package io.github.patrickric.akkuhealthalarm.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Check
import android.os.BatteryManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import io.github.patrickric.akkuhealthalarm.data.LogDao
import io.github.patrickric.akkuhealthalarm.data.RecordingGap
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import io.github.patrickric.akkuhealthalarm.BuildConfig
import io.github.patrickric.akkuhealthalarm.data.AlarmDiag
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.patrickric.akkuhealthalarm.R
import io.github.patrickric.akkuhealthalarm.app
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings
import io.github.patrickric.akkuhealthalarm.data.CurrentDirection
import io.github.patrickric.akkuhealthalarm.data.EventEntry
import io.github.patrickric.akkuhealthalarm.data.EventType
import io.github.patrickric.akkuhealthalarm.data.ReadingEntry
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** Extra value drawn together with the charge level in the chart. */
enum class Overlay { TEMP, VOLTAGE, CURRENT }

@Composable
fun HistoryScreen(settings: AlarmSettings, update: Update, request: Overlay? = null, onRequestHandled: () -> Unit = {}) {
    val dao = LocalContext.current.app.db.dao()
    var showChart by rememberSaveable { mutableStateOf(false) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    // Opened from a value tile on the health page: show the chart with that value.
    LaunchedEffect(request) {
        if (request != null) {
            showChart = true
            overlay = request
            onRequestHandled()
        }
    }

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.history_title)) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // Landscape on a phone: less height, so the rows of the list are a little flatter.
        val compact = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
        val twelveHour = !Format.is24Hour(LocalContext.current)
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val layout = rememberRowLayout(maxWidth, twelveHour, electrical = settings.logElectrical, dense = compact)
            Column(Modifier.fillMaxSize()) {
                // One line for the switch list / chart and, in the list, the switch "V · mA".
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val label = MaterialTheme.typography.labelLarge
                    SingleChoiceSegmentedButtonRow(if (compact) Modifier.width(320.dp) else Modifier.weight(1f)) {
                        SegmentedButton(
                            selected = !showChart,
                            onClick = { showChart = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(stringResource(R.string.view_list), maxLines = 1, softWrap = false, autoSize = fit(label, 10.sp)) },
                        )
                        SegmentedButton(
                            selected = showChart,
                            onClick = { showChart = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = { Icon(Icons.Rounded.Timeline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(stringResource(R.string.view_chart), maxLines = 1, softWrap = false, autoSize = fit(label, 10.sp)) },
                        )
                    }
                    // Only when the screen is too narrow for all columns: voltage and current on request.
                    if (!showChart && layout.toggle) {
                        Spacer(Modifier.width(12.dp))
                        ElectricalChip(settings, update)
                    }
                }
                if (showChart) ChartView(settings, dao, overlay) { overlay = it } else LogList(dao, layout)
            }
        }
    }
}

private enum class ChartRange(val days: Int, val label: Int) {
    DAY(1, R.string.range_day),
    WEEK(7, R.string.range_week),
    MONTH(30, R.string.range_month),
    ALL(0, R.string.range_all),
}

@Composable
private fun ChartView(settings: AlarmSettings, dao: LogDao, overlay: Overlay?, onOverlay: (Overlay?) -> Unit) {
    var range by rememberSaveable { mutableStateOf(ChartRange.DAY) }
    val now = remember(range) { System.currentTimeMillis() }
    val oldest by remember { dao.oldestReadingTime() }.collectAsStateWithLifecycle(null)
    val from = if (range == ChartRange.ALL) (oldest ?: now - DAY_MS).coerceAtMost(now - DAY_MS) else now - range.days * DAY_MS
    val readings by remember(from) { dao.readingsSince(from) }.collectAsStateWithLifecycle(emptyList())
    // The chart starts at the first value in the range, so it always uses the full width.
    val first = readings.firstOrNull()?.time
    val fullEnd = maxOf(now, readings.lastOrNull()?.time ?: now)
    val fullStart = minOf(if (first != null) maxOf(from, first) else from, fullEnd - MIN_WINDOW_MS.toLong())
    // Visible part of the time axis; changed by pinching and dragging.
    var window by remember(range, fullStart) { mutableStateOf(fullStart.toDouble() to fullEnd.toDouble()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Wraps to a second line on narrow screens or with a large font instead of cutting a chip off.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartRange.entries.forEach { r ->
                FilterChip(
                    selected = range == r,
                    onClick = { range = r },
                    label = { Text(stringResource(r.label), maxLines = 1) },
                )
            }
        }
        // Wraps to a second line on narrow screens or with a large font instead of cutting a chip off.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Triple(Overlay.TEMP, Icons.Rounded.Thermostat, R.string.chart_temp),
                Triple(Overlay.VOLTAGE, Icons.Rounded.ElectricalServices, R.string.metric_voltage),
                Triple(Overlay.CURRENT, Icons.Rounded.Bolt, R.string.metric_current),
            ).forEach { (o, icon, label) ->
                val color = overlayColor(o)
                FilterChip(
                    selected = overlay == o,
                    onClick = { onOverlay(if (overlay == o) null else o) },
                    leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) },
                    label = { Text(stringResource(label), maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.22f)),
                )
            }
        }
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
        ) {
            if (readings.size < 2) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.chart_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val full = fullStart.toDouble() to fullEnd.toDouble()
                LevelChart(
                    readings,
                    settings.low,
                    settings.high,
                    window.first.toLong(),
                    window.second.toLong(),
                    overlay,
                    Modifier.fillMaxSize().padding(12.dp)
                        .pointerInput(range, fullStart) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val (start, end) = window
                                val left = 34.dp.toPx()
                                val width = (size.width - left - 8.dp.toPx()).coerceAtLeast(1f)
                                val fx = ((centroid.x - left) / width).coerceIn(0f, 1f)
                                val fullSpan = full.second - full.first
                                val span = ((end - start) / zoom).coerceIn(MIN_WINDOW_MS, fullSpan)
                                val anchor = start + (end - start) * fx
                                val newStart = (anchor - span * fx - pan.x / width * span)
                                    .coerceIn(full.first, full.second - span)
                                window = newStart to newStart + span
                            }
                        }
                        .pointerInput(range, fullStart) { detectTapGestures(onDoubleTap = { window = full }) },
                )
            }
        }
        if (overlay == Overlay.CURRENT) {
            Row(Modifier.padding(top = 6.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(overlayColor(Overlay.CURRENT), stringResource(R.string.status_charging))
                LegendItem(LocalExtraColors.current.discharge, stringResource(R.string.log_discharging))
            }
        }
        Text(
            stringResource(R.string.chart_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}

private const val MIN_WINDOW_MS = 30 * 60 * 1000.0

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 16.dp, height = 3.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/** One line per change of the charge level, like a logbook. Many lines = a lot happened. */
private sealed interface LogItem {
    data class Day(val label: String) : LogItem
    data class Reading(val entry: ReadingEntry) : LogItem
    data class Alarm(val entry: EventEntry) : LogItem
    data class Gap(val from: Long, val to: Long) : LogItem
}

@Composable
private fun LogList(dao: LogDao, layout: RowLayout) {
    val context = LocalContext.current
    val readings by remember { dao.recentReadings(5_000) }.collectAsStateWithLifecycle(emptyList())
    val alarms by remember { dao.recentAlarms(500) }.collectAsStateWithLifecycle(emptyList())

    val changes = remember(readings) { readings.onlyChanges() }
    val changeCount = changes.size
    val items = remember(changes, alarms) {
        val dayFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL)
        // Pauses in the recording, e.g. when the phone killed the app (readings are newest first).
        val gaps = readings.zipWithNext().filter { (newer, older) ->
            RecordingGap.isGap(older.time, older.level, newer.time, newer.level)
        }.map { (newer, older) -> newer.time to LogItem.Gap(older.time, newer.time) }
        val merged = (changes.map { it.time to LogItem.Reading(it) } + alarms.map { it.time to LogItem.Alarm(it) } + gaps)
            .sortedByDescending { it.first }
        buildList {
            var lastDay = ""
            for ((time, item) in merged) {
                val day = dayFormat.format(java.util.Date(time))
                if (day != lastDay) {
                    add(LogItem.Day(day))
                    lastDay = day
                }
                add(item)
            }
        }
    }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.list_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val timeFormat = Format.timeFormat(context)
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().fastScroller(listState), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
        items.forEach { item ->
            when (item) {
                // Keys and content types let the list reuse rows of the same kind while scrolling.
                is LogItem.Day -> stickyHeader(key = "d" + item.label, contentType = "day") { DayHeader(item.label) }
                is LogItem.Reading -> item(key = "r" + item.entry.id, contentType = "reading") { ReadingRow(item.entry, timeFormat, layout) }
                is LogItem.Alarm -> item(key = "a" + item.entry.id, contentType = "alarm") { AlarmRow(item.entry, timeFormat, layout) }
                is LogItem.Gap -> item(key = "g" + item.from, contentType = "gap") { GapRow(item) }
            }
        }
        // The number of entries is only a side note: at the end of the list, not above it.
        item {
            Text(
                pluralStringResource(R.plurals.entries_count, changeCount, changeCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
    }
}

/**
 * Position bar at the right edge, like in many log apps: appears while the list moves, fades out after a
 * moment. While it is visible, the bar can be dragged to jump through a long history.
 */
@Composable
private fun Modifier.fastScroller(state: LazyListState): Modifier {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val active = state.isScrollInProgress || dragging
    // Fades in at once, stays 1.5 s after the list stopped (time to grab it), then fades out.
    val fade = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            fade.animateTo(1f, tween(150))
        } else {
            delay(1500)
            fade.animateTo(0f, tween(400))
        }
    }
    val alpha by fade.asState()
    val shown = rememberUpdatedState(alpha > 0.05f)
    val color = MaterialTheme.colorScheme.primary

    /** Top and height of the bar in pixels, or null if everything fits on the screen. */
    fun bar(height: Float, minHeight: Float): Pair<Float, Float>? {
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total == 0 || visible.isEmpty() || visible.size >= total) return null
        val first = visible.first()
        val position = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / first.size.coerceAtLeast(1).toFloat()
        val barHeight = (height * visible.size / total).coerceIn(minHeight, height)
        val fraction = (position / (total - visible.size).coerceAtLeast(1)).coerceIn(0f, 1f)
        return (height - barHeight) * fraction to barHeight
    }

    return this
        .pointerInput(state) {
            awaitEachGesture {
                // Looked at before the list, so a drag on the bar does not scroll the list as well.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val minHeight = 48.dp.toPx()
                val start = bar(size.height.toFloat(), minHeight) ?: return@awaitEachGesture
                val slop = 16.dp.toPx()
                val onBar = shown.value && down.position.x >= size.width - 40.dp.toPx() &&
                    down.position.y in (start.first - slop)..(start.first + start.second + slop)
                if (!onBar) return@awaitEachGesture
                down.consume()
                dragging = true
                val grab = down.position.y - start.first
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val (_, barHeight) = bar(size.height.toFloat(), minHeight) ?: break
                        val fraction = ((change.position.y - grab) / (size.height - barHeight)).coerceIn(0f, 1f)
                        val info = state.layoutInfo
                        val target = (fraction * (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)).toInt()
                        scope.launch { state.scrollToItem(target) }
                    }
                } finally {
                    dragging = false
                }
            }
        }
        .drawWithContent {
            drawContent()
            val a = alpha
            if (a <= 0f) return@drawWithContent
            val (top, height) = bar(size.height, 48.dp.toPx()) ?: return@drawWithContent
            val width = (if (dragging) 8.dp else 6.dp).toPx()
            drawRoundRect(
                color.copy(alpha = (if (dragging) 0.9f else 0.6f) * a),
                topLeft = Offset(size.width - width - 3.dp.toPx(), top),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = CornerRadius(width / 2),
            )
        }
}

/** Switch "V · mA": extra columns voltage and current in the list, saved in the settings. */
@Composable
private fun ElectricalChip(settings: AlarmSettings, update: Update) {
    val description = stringResource(R.string.log_electrical)
    FilterChip(
        selected = settings.logElectrical,
        onClick = { update { it.copy(logElectrical = !it.logElectrical) } },
        label = { Text("V\u00a0·\u00a0mA", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) },
        leadingIcon = {
            Icon(
                if (settings.logElectrical) Icons.Rounded.Check else Icons.Rounded.ViewColumn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        modifier = Modifier.semantics { contentDescription = description },
    )
}

/**
 * Column widths and fonts of the log rows, measured for the screen width and the phone's font size.
 * A width of 0 means the column is not shown; [statusText] false = the status only as a symbol.
 */
private class RowLayout(
    val style: TextStyle,
    val label: TextStyle,
    val time: Dp,
    val level: Dp,
    val temp: Dp,
    val volt: Dp,
    val amp: Dp,
    val status: Dp,
    val statusText: Boolean,
    /** Gap between the columns: they spread over the row, but stay together on wide screens. */
    val gap: Dp,
    /** The screen is too narrow for all columns: offer the switch "V · mA". */
    val toggle: Boolean,
    /** Space above and below the text of a row; less in landscape, so more rows fit. */
    val rowPadding: Dp = 7.dp,
    /** The phone uses a 12-hour clock: AM/PM in small letters. */
    val twelveHour: Boolean = false,
)

/** Space of a row outside the text columns: outer padding, colour bar, inner padding, a little air. */
private val ROW_FIXED = 24.dp + 5.dp + 16.dp + 4.dp

/** Smallest gap between two columns. */
private val COLUMN_GAP = 8.dp

/** Largest gap between two columns (landscape: values stay close together instead of far apart). */
private val COLUMN_GAP_MAX = 40.dp

/** Width of the status column when it shows only the symbol. */
private val STATUS_ICON = 18.dp

/**
 * Measures the widest content of each column once. If a row does not fit the screen (narrow phone,
 * large font), all texts of the row get the same smaller font, so every row stays one line.
 */
@Composable
private fun rememberRowLayout(maxWidth: Dp, twelveHour: Boolean, electrical: Boolean, dense: Boolean): RowLayout {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = MaterialTheme.typography.bodyMedium.merge(Tabular)
    val baseLabel = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val statuses = listOf(
        stringResource(R.string.status_charging) + " · USB",
        stringResource(R.string.status_charging) + " · AC",
        stringResource(R.string.log_discharging),
        stringResource(R.string.status_plugged),
    )
    return remember(maxWidth, twelveHour, electrical, dense, base, statuses, density) {
        fun width(text: String, style: TextStyle) =
            with(density) { measurer.measure(text, style, softWrap = false, maxLines = 1).size.width.toDp() } + 2.dp
        val time = width(if (twelveHour) "12:59 PM" else "23:59", base.copy(fontWeight = FontWeight.SemiBold))
        val level = width("100\u00a0%", base.copy(fontWeight = FontWeight.Bold))
        val temp = width(Format.celsius(38.8f), base)
        val volt = width(Format.volts(8888), base)
        val amp = width(Format.milliAmps(-8888), base)
        val status = 20.dp + statuses.maxOf { width(it, baseLabel) }
        val space = maxWidth - ROW_FIXED

        /** Room the columns need, gaps included. */
        fun need(columns: List<Dp>) = columns.fold(0.dp) { sum, w -> sum + w } + COLUMN_GAP * (columns.size - 1)

        // Wide screen (landscape, tablet): everything with its full text, no switch needed.
        val all = listOf(time, level, temp, volt, amp, status)
        val fitsAll = need(all) <= space
        // Narrow screen with "V · mA": voltage and current always; the temperature and the status text
        // only if they still fit. Without the switch: the four columns as before.
        val withTemp = need(listOf(time, level, temp, volt, amp, STATUS_ICON)) <= space
        val withText = withTemp && need(all) <= space
        val showTemp = fitsAll || !electrical || withTemp
        val showElectrical = fitsAll || electrical
        val statusText = fitsAll || !electrical || withText
        val statusWidth = if (statusText) status else STATUS_ICON
        val columns = listOfNotNull(time, level, temp.takeIf { showTemp }, volt.takeIf { showElectrical }, amp.takeIf { showElectrical }, statusWidth)
        // Still too wide (narrow phone, large font): the whole row gets a smaller font.
        val f = ((space - COLUMN_GAP * (columns.size - 1)) / (need(columns) - COLUMN_GAP * (columns.size - 1))).coerceAtMost(1f)
        RowLayout(
            style = base.copy(fontSize = base.fontSize * f),
            label = baseLabel.copy(fontSize = baseLabel.fontSize * f),
            time = time * f,
            level = level * f,
            temp = if (showTemp) temp * f else 0.dp,
            volt = if (showElectrical) volt * f else 0.dp,
            amp = if (showElectrical) amp * f else 0.dp,
            status = if (statusText) status * f else STATUS_ICON,
            statusText = statusText,
            gap = if (f < 1f) COLUMN_GAP else ((space - need(columns)) / (columns.size - 1) + COLUMN_GAP).coerceAtMost(COLUMN_GAP_MAX),
            toggle = !fitsAll,
            rowPadding = if (dense) 4.dp else 7.dp,
            twelveHour = twelveHour,
        )
    }
}

/**
 * Keeps only real changes of level, plug or charging state. The extra samples taken every
 * few minutes stay in the database for the chart, but would only add noise to the list.
 */
private fun List<ReadingEntry>.onlyChanges(): List<ReadingEntry> {
    val out = ArrayList<ReadingEntry>(size)
    for (r in asReversed()) {
        val prev = out.lastOrNull()
        if (prev != null && prev.level == r.level && prev.plugged == r.plugged && prev.status == r.status) continue
        out += r
    }
    return out.asReversed()
}

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun DayHeader(label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

/**
 * Charging in the sense of the log: the status says so, or plugged in and neither full nor stopped – unless the
 * current runs out of the battery (the phone uses more than the charger gives); that row counts as plugged in only.
 */
private fun ReadingEntry.isCharging(): Boolean = (
    status == BatteryManager.BATTERY_STATUS_CHARGING ||
        (plugged != 0 && status != BatteryManager.BATTERY_STATUS_NOT_CHARGING && status != BatteryManager.BATTERY_STATUS_FULL)
    ) && (currentMa ?: 0) > -CurrentDirection.DRAIN_MA

@Composable
private fun ReadingRow(r: ReadingEntry, timeFormat: java.text.DateFormat, layout: RowLayout) {
    val scheme = MaterialTheme.colorScheme
    val plugged = r.plugged != 0
    val charging = r.isCharging()
    // Green = charging, blue = running on battery; the same colour for bar, tint, number and label.
    val accent = when {
        charging -> scheme.primary
        plugged -> scheme.secondary
        else -> scheme.tertiary
    }
    val (icon, label) = when {
        charging -> when (r.plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> Icons.Rounded.Usb to R.string.status_charging
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> Icons.Rounded.WifiTethering to R.string.status_charging
            else -> Icons.Rounded.Power to R.string.status_charging
        }
        plugged -> PLUGGED_ICON to R.string.status_plugged
        else -> Icons.AutoMirrored.Rounded.TrendingDown to R.string.log_discharging
    }
    // Kind of charger in words: "Charging · USB" / "Charging · AC"; wireless only by its symbol.
    val chargerSuffix = when {
        !charging -> ""
        r.plugged == BatteryManager.BATTERY_PLUGGED_USB -> " · USB"
        r.plugged == BatteryManager.BATTERY_PLUGGED_AC -> " · AC"
        else -> ""
    }
    val row = layout.style
    // Speed matters here: hundreds of rows pass by while scrolling. So no automatic font size (it lays out
    // each text several times) and no intrinsic measuring; the colour bar is drawn, not measured.
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .drawBehind {
                drawRect(accent.copy(alpha = 0.10f))
                drawRect(accent, size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height))
            },
    ) {
        // The columns spread over the width with equal gaps (at most COLUMN_GAP_MAX).
        Row(
            Modifier.padding(start = 13.dp, end = 8.dp, top = layout.rowPadding, bottom = layout.rowPadding),
            horizontalArrangement = Arrangement.spacedBy(layout.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 12-hour clocks ("11:01 PM", e.g. English): a small AM/PM.
            val time = timeFormat.format(java.util.Date(r.time))
            val twelveHour = layout.twelveHour
            Text(
                buildAnnotatedString {
                    val split = time.indexOfFirst { !it.isDigit() && it != ':' && it != '.' }.takeIf { twelveHour && it > 0 }
                    if (split == null) {
                        append(time)
                    } else {
                        append(time.substring(0, split))
                        withStyle(SpanStyle(fontSize = 10.sp)) { append(time.substring(split)) }
                    }
                },
                style = row,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(layout.time),
            )
            Text(
                "${r.level}\u00a0%",
                style = row,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(layout.level),
            )
            @Composable
            fun value(text: String, width: Dp) = Text(
                text,
                style = row,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(width),
            )
            if (layout.temp > 0.dp) value(Format.celsius(r.tempDeciC / 10f), layout.temp)
            if (layout.volt > 0.dp) value(r.voltageMv.takeIf { it > 0 }?.let(Format::volts) ?: "–", layout.volt)
            if (layout.amp > 0.dp) value(r.currentMa?.let(Format::milliAmps) ?: "–", layout.amp)
            val statusLabel = stringResource(label) + chargerSuffix
            if (layout.statusText) {
                Row(Modifier.width(layout.status), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    // The column is measured for the longest status of this language, so the text fits.
                    Text(
                        statusLabel,
                        style = layout.label,
                        color = accent,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                // Only the symbol (switch "V · mA" on a narrow screen); screen readers still get the words.
                Icon(icon, contentDescription = statusLabel, tint = accent, modifier = Modifier.size(STATUS_ICON))
            }
        }
    }
}

/**
 * Rows of the log always stay one line, whatever the screen width and the phone's font size:
 * a text that does not fit gets a smaller font instead of a second line.
 */
private fun fit(style: TextStyle, min: TextUnit = 7.sp) = TextAutoSize.StepBased(minFontSize = min, maxFontSize = style.fontSize)

/** Quiet grey line: the app recorded nothing in this time. */
@Composable
private fun GapRow(gap: LogItem.Gap) {
    val context = LocalContext.current
    val range = android.text.format.DateUtils.formatDateRange(
        context,
        gap.from,
        gap.to,
        android.text.format.DateUtils.FORMAT_SHOW_TIME or android.text.format.DateUtils.FORMAT_ABBREV_MONTH or Format.clockFlag(context),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.PauseCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.gap_row, range),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            softWrap = false,
            autoSize = fit(MaterialTheme.typography.bodyMedium),
        )
    }
}

@Composable
private fun AlarmRow(e: EventEntry, timeFormat: java.text.DateFormat, layout: RowLayout) {
    val scheme = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current
    val (icon, label, colors) = when (e.type) {
        EventType.ALARM_HIGH -> Triple(Icons.Rounded.BatteryChargingFull, R.string.event_alarm_high, extra.cautionContainer to extra.onCautionContainer)
        EventType.ALARM_LOW -> Triple(Icons.Rounded.BatteryAlert, R.string.event_alarm_low, scheme.errorContainer to scheme.onErrorContainer)
        EventType.ALARM_HOT -> Triple(Icons.Rounded.Thermostat, R.string.event_alarm_hot, scheme.errorContainer to scheme.onErrorContainer)
        else -> Triple(Icons.Rounded.AcUnit, R.string.event_alarm_cold, scheme.tertiaryContainer to scheme.onTertiaryContainer)
    }
    val first = AlarmDiag.parse(e.diag)
    val last = AlarmDiag.parse(e.lastDiag)
    // Test builds only: a tap shows how the sound really went (alarms from older versions have no details).
    val hasDetails = BuildConfig.DEBUG && (first != null || last != null)
    var expanded by rememberSaveable(e.id) { mutableStateOf(false) }
    Surface(
        color = colors.first,
        contentColor = colors.second,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
            .then(if (hasDetails) Modifier.clickable { expanded = !expanded } else Modifier),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                timeFormat.format(java.util.Date(e.time)),
                style = layout.style,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(layout.time + 8.dp),
            )
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                // Repeats stay in this one row: "Alarm: battery charged · 12×".
                Text(
                    stringResource(label) + if (e.repeats > 0) " · ${e.repeats + 1}×" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = fit(MaterialTheme.typography.bodyMedium),
                )
                val until = e.lastRepeat?.let { stringResource(R.string.repeat_until, timeFormat.format(java.util.Date(it))) }
                val failed = first?.failed == true || last?.failed == true
                val delivery = if (failed && e.delivery == "SOUND") stringResource(R.string.delivery_sound_failed) else deliveryText(e.delivery)
                listOfNotNull(delivery, until).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.second.copy(alpha = 0.8f),
                        maxLines = 1,
                        softWrap = false,
                        autoSize = fit(MaterialTheme.typography.labelSmall),
                    )
                }
                if (expanded) {
                    val lines = listOfNotNull(
                        first?.let { stringResource(R.string.diag_first, diagText(it)) },
                        last?.let { d ->
                            val time = e.lastRepeat?.let { timeFormat.format(java.util.Date(it)) }.orEmpty()
                            stringResource(R.string.diag_last, time, diagText(d))
                        },
                    )
                    lines.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.second.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            if (hasDetails) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text("${e.level}\u00a0%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

/** Screens lower than this (phones in landscape) get the tabs as a rail on the left and no big title. */
const val COMPACT_HEIGHT_DP = 480

@Composable
fun TabTopBar(title: String) {
    // Landscape on a phone: the title would take a sixth of the height; the rail already shows the tab.
    if (LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        return
    }
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun LevelChart(
    readings: List<ReadingEntry>,
    low: Int,
    high: Int,
    from: Long,
    to: Long,
    overlay: Overlay?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val line = scheme.primary
    val overlayLine = overlay?.let { overlayColor(it) } ?: line
    val dischargeLine = LocalExtraColors.current.discharge
    val grid = scheme.outlineVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val span = to - from
    val locale = LocalConfiguration.current.locales[0]
    val showTime = span <= 2 * DAY_MS
    val timeFormat = java.text.SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(
            locale,
            if (Format.is24Hour(context)) "Hm" else "hm",
        ),
        locale,
    )
    val dateFormat = java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)

    Canvas(modifier) {
        val left = 34.dp.toPx()
        val right = if (overlay != null) 48.dp.toPx() else 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = (if (showTime) 36 else 22).dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun x(t: Long) = left + w * ((t - from).toFloat() / span)
        fun y(level: Float) = top + h * (1f - level / 100f)

        drawRect(line.copy(alpha = 0.10f), topLeft = Offset(left, y(high.toFloat())), size = Size(w, y(low.toFloat()) - y(high.toFloat())))
        listOf(0, 50, 100).forEach { v ->
            drawLine(grid, Offset(left, y(v.toFloat())), Offset(left + w, y(v.toFloat())), strokeWidth = 1.dp.toPx())
            val m = measurer.measure("$v", labelStyle)
            drawText(m, topLeft = Offset(left - m.size.width - 6.dp.toPx(), y(v.toFloat()) - m.size.height / 2f))
        }
        // Second value on its own scale; axis on the right.
        val values = overlay?.let { o -> readings.filter { it.time in from..to }.mapNotNull { overlayValue(o, it) } }.orEmpty()
        val oLo: Float
        val oHi: Float
        if (values.isNotEmpty()) {
            // Scale to the bulk of the values; a single odd reading must not squash the line.
            val sorted = values.sorted()
            val cut = if (sorted.size >= 20) sorted.size / 50 else 0
            val vMin = sorted[cut]
            val vMax = sorted[sorted.size - 1 - cut]
            val pad = ((vMax - vMin) * 0.1f).coerceAtLeast(if (overlay == Overlay.VOLTAGE) 0.02f else 1f)
            // The current has a sign (negative = out of the battery): its scale always includes zero.
            oLo = when (overlay) {
                Overlay.TEMP -> vMin - pad
                Overlay.CURRENT -> minOf(vMin - pad, 0f)
                else -> (vMin - pad).coerceAtLeast(0f)
            }
            oHi = if (overlay == Overlay.CURRENT) maxOf(vMax + pad, 0f) else vMax + pad
            // Current on both sides of zero: label the zero line instead of the middle (unless it is too close to an end).
            val zeroAt = if (overlay == Overlay.CURRENT && oLo < 0f && oHi > 0f) -oLo / (oHi - oLo) else null
            val labels = when {
                zeroAt == null -> listOf(oLo, (oLo + oHi) / 2, oHi)
                zeroAt in 0.12f..0.88f -> listOf(oLo, 0f, oHi)
                else -> listOf(oLo, oHi)
            }
            labels.forEach { v ->
                // The current has two colours, so its axis stays neutral.
                val axisColor = if (overlay == Overlay.CURRENT) labelStyle.color else overlayLine
                val m = measurer.measure(formatOverlay(overlay!!, v), labelStyle.copy(color = axisColor))
                drawText(m, topLeft = Offset(left + w + 4.dp.toPx(), (top + h * (1f - (v - oLo) / (oHi - oLo)) - m.size.height / 2f)))
            }
        } else {
            oLo = 0f
            oHi = 1f
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        // Zero line of the current when it has values on both sides: above = charging, below = discharging.
        if (overlay == Overlay.CURRENT && values.isNotEmpty() && oLo < 0f && oHi > 0f) {
            val y0 = top + h * (1f - (0f - oLo) / (oHi - oLo))
            drawLine(labelStyle.color.copy(alpha = 0.5f), Offset(left, y0), Offset(left + w, y0), strokeWidth = 1.dp.toPx())
        }
        listOf(low, high).forEach { v ->
            drawLine(line.copy(alpha = 0.7f), Offset(left, y(v.toFloat())), Offset(left + w, y(v.toFloat())), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
        }
        for (i in 0..3) {
            val t = java.util.Date(from + span * i / 3)
            // Time on top, date below; for longer ranges only the date.
            val lines = if (showTime) listOf(timeFormat.format(t), dateFormat.format(t)) else listOf(dateFormat.format(t))
            var lineTop = top + h + 4.dp.toPx()
            lines.forEach { text ->
                val m = measurer.measure(text, labelStyle)
                val px = (left + w * i / 3f - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
                drawText(m, topLeft = Offset(px, lineTop))
                lineTop += m.size.height
            }
        }

        clipRect(left, top - 6.dp.toPx(), left + w, top + h + 6.dp.toPx()) {
            // Only points near the visible window, and at most about one per pixel.
            val visible = readings.filterIndexed { i, r ->
                r.time in from..to ||
                    (i + 1 < readings.size && readings[i + 1].time >= from && r.time < from) ||
                    (i > 0 && readings[i - 1].time <= to && r.time > to)
            }
            drawSeries(visible, { x(it.time) }, { y(it.level.toFloat()) }, line, fill = true, top = top, bottom = top + h)
            if (overlay != null && values.isNotEmpty()) {
                val withValue = visible.filter { overlayValue(overlay, it) != null }
                // Current: one run per direction, each in its colour; a run ends on the first point
                // of the next one, so the line stays connected.
                val runs = if (overlay == Overlay.CURRENT) splitRuns(withValue) else listOf(withValue)
                runs.forEach { run ->
                    drawSeries(
                        run,
                        { x(it.time) },
                        { top + h * (1f - (overlayValue(overlay, it)!! - oLo) / (oHi - oLo)) },
                        if (overlay == Overlay.CURRENT && (run.first().currentMa ?: 0) < 0) dischargeLine else overlayLine,
                        fill = false,
                        top = top,
                        bottom = top + h,
                    )
                }
            }
            readings.lastOrNull()?.takeIf { it.time in from..to }?.let {
                drawCircle(line, radius = 4.5.dp.toPx(), center = Offset(x(it.time), y(it.level.toFloat())))
            }
        }
    }
}

/** Runs of the current by its sign: into the battery (charge colour) or out of it (discharge colour). */
private fun splitRuns(points: List<ReadingEntry>): List<List<ReadingEntry>> {
    val runs = mutableListOf<MutableList<ReadingEntry>>()
    fun ReadingEntry.out() = (currentMa ?: 0) < 0
    points.forEach { p ->
        val run = runs.lastOrNull()
        if (run == null || run.first().out() != p.out()) {
            run?.add(p)
            runs += mutableListOf(p)
        } else {
            run += p
        }
    }
    return runs
}

/** Draws one line, broken where recording paused; optional soft fill below. */
private fun DrawScope.drawSeries(
    points: List<ReadingEntry>,
    px: (ReadingEntry) -> Float,
    py: (ReadingEntry) -> Float,
    color: Color,
    fill: Boolean,
    top: Float,
    bottom: Float,
) {
    if (points.size < 2) return
    val segments = mutableListOf<MutableList<ReadingEntry>>(mutableListOf(points.first()))
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        if (RecordingGap.isGap(a.time, a.level, b.time, b.level)) segments += mutableListOf<ReadingEntry>()
        segments.last() += points[i]
    }
    val brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f)), startY = top, endY = bottom)
    for (seg in segments) {
        if (seg.size < 2) continue
        val path = Path()
        val area = Path()
        var lastX = Float.NEGATIVE_INFINITY
        seg.forEachIndexed { i, r ->
            val x = px(r)
            val y = py(r)
            if (i == 0) {
                path.moveTo(x, y)
                area.moveTo(x, bottom)
                area.lineTo(x, y)
            } else if (x - lastX >= 1f || i == seg.lastIndex) {
                path.lineTo(x, y)
                area.lineTo(x, y)
            } else {
                return@forEachIndexed
            }
            lastX = x
        }
        if (fill) {
            area.lineTo(lastX, bottom)
            area.close()
            drawPath(area, brush)
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun overlayValue(o: Overlay, r: ReadingEntry): Float? = when (o) {
    Overlay.TEMP -> r.tempDeciC / 10f
    Overlay.VOLTAGE -> r.voltageMv.takeIf { it > 0 }?.let { it / 1000f }
    Overlay.CURRENT -> r.currentMa?.toFloat()
}

private fun formatOverlay(o: Overlay, v: Float): String = when (o) {
    Overlay.TEMP -> String.format(java.util.Locale.getDefault(), "%.0f°", v)
    Overlay.VOLTAGE -> String.format(java.util.Locale.getDefault(), "%.2f V", v)
    Overlay.CURRENT -> "${v.toInt()} mA"
}

@Composable
fun overlayColor(o: Overlay): Color = when (o) {
    Overlay.TEMP -> LocalExtraColors.current.caution
    Overlay.VOLTAGE -> MaterialTheme.colorScheme.tertiary
    Overlay.CURRENT -> LocalExtraColors.current.charge
}

/** "screen off · notification volume 2/7 · played 2.6 s": what happened when the alarm sound played. */
@Composable
private fun diagText(d: AlarmDiag): String {
    fun sec(ms: Long) = String.format(java.util.Locale.getDefault(), "%.1f", ms / 1000.0)
    val p = d.problem
    val result = when {
        d.clips.isEmpty() -> stringResource(R.string.diag_nothing)
        p == null -> stringResource(R.string.diag_played, sec(d.playedMs))
        p.end == AlarmDiag.End.ERROR || p.end == AlarmDiag.End.EXCEPTION -> stringResource(R.string.diag_error, p.code)
        p.end == AlarmDiag.End.TIMEOUT -> stringResource(R.string.diag_timeout)
        p.end == AlarmDiag.End.STOPPED -> stringResource(R.string.diag_stopped, sec(d.playedMs))
        else -> stringResource(R.string.diag_short, sec(p.playedMs), sec(p.lengthMs.toLong()))
    }
    return listOfNotNull(
        stringResource(if (d.screenOn) R.string.diag_screen_on else R.string.diag_screen_off),
        stringResource(if (d.alarmStream) R.string.diag_volume_alarm else R.string.diag_volume_notification, d.volume, d.volumeMax),
        if (d.doNotDisturb) stringResource(R.string.diag_dnd) else null,
        if (!d.focusGranted) stringResource(R.string.diag_no_focus) else null,
        result,
    ).joinToString(" · ")
}

/** How the alarm was given out: with sound, silent in the night rest, or muted phone. */
@Composable
private fun deliveryText(delivery: String?): String? = when (delivery) {
    "SOUND" -> stringResource(R.string.delivery_sound)
    "QUIET_HOURS" -> stringResource(R.string.delivery_quiet)
    "MUTED_VIBRATE" -> stringResource(R.string.delivery_muted_vibrate)
    "MUTED" -> stringResource(R.string.delivery_muted)
    "SILENT_BY_SETTINGS" -> stringResource(R.string.delivery_silent)
    else -> null
}

/**
 * Plugged in, but not charging (full, or held by the phone): the plug with a small pause sign.
 * Its own symbol, so it differs from "charging · AC" also where only the symbol is shown.
 */
private val PLUGGED_ICON: ImageVector by lazy {
    ImageVector.Builder("PluggedPaused", 24.dp, 24.dp, 24f, 24f).apply {
        addGroup(scaleX = 0.8f, scaleY = 0.8f, translationX = -0.5f)
        Icons.Rounded.Power.root.forEach { node ->
            if (node is VectorPath) addPath(node.pathData, fill = SolidColor(Color.Black))
        }
        clearGroup()
        addPath(
            PathData {
                moveTo(15.5f, 14f); lineTo(18f, 14f); lineTo(18f, 23f); lineTo(15.5f, 23f); close()
                moveTo(20.5f, 14f); lineTo(23f, 14f); lineTo(23f, 23f); lineTo(20.5f, 23f); close()
            },
            fill = SolidColor(Color.Black),
        )
    }.build()
}
