package io.github.patrickric.akkuhealthalarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.patrickric.akkuhealthalarm.R
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp

@Composable
fun IconBadge(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 36.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                action()
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}




@Composable
fun MetricTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    if (onClick != null) {
        // Tappable tiles: coloured frame and a small chart symbol in the corner.
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            color = color,
            border = BorderStroke(if (selected) 2.dp else 1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 1f else 0.6f)),
        ) {
            Box {
                MetricTileContent(icon, value, label)
                Icon(
                    Icons.AutoMirrored.Rounded.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp),
                )
            }
        }
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = color) {
            MetricTileContent(icon, value, label)
        }
    }
}

@Composable
private fun MetricTileContent(icon: ImageVector, value: String, label: String) {
    Column(
        Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "25 min", "3 h 10 min" or "12 h", in the app language. */
@Composable
fun durationText(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.duration_min, minutes)
    minutes % 60 == 0 || minutes >= 600 -> stringResource(R.string.duration_h, (minutes + 30) / 60)
    else -> stringResource(R.string.duration_h_min, minutes / 60, minutes % 60)
}

/**
 * Exact setting of a limit: "−" and "+" change by one (holding repeats), a tap on the number
 * opens the keypad for direct input.
 */
@Composable
fun ValueStepper(
    label: String,
    value: Int,
    unit: String,
    color: Color,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    /** Shown under the input field instead of the allowed range, e.g. a recommendation. */
    hint: String? = null,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    // The label sits centred over the number and its buttons; the whole block keeps to its side.
    Box(modifier, contentAlignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RepeatButton(Icons.Rounded.Remove, stringResource(R.string.step_less), color, value > min) {
                    onChange((value - 1).coerceIn(min, max))
                }
                // Fixed width, so two steppers side by side fit even a narrow phone with a large font;
                // "100 %" then gets a slightly smaller font instead of pushing the buttons apart.
                Text(
                    "$value $unit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
                    modifier = Modifier.width(64.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = label) { editing = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
                RepeatButton(Icons.Rounded.Add, stringResource(R.string.step_more), color, value < max) {
                    onChange((value + 1).coerceIn(min, max))
                }
            }
        }
    }
    if (editing) {
        // The field is active at once and the old number is selected, so typing replaces it.
        var field by remember { mutableStateOf(TextFieldValue(value.toString(), TextRange(0, value.toString().length))) }
        val text = field.text
        val parsed = text.toIntOrNull()?.takeIf { it in min..max }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = field,
                    onValueChange = { new -> field = new.copy(text = new.text.filter { it.isDigit() || it == '-' }.take(4)) },
                    modifier = Modifier.focusRequester(focus),
                    suffix = { Text(unit) },
                    // The allowed range only when the input is outside it; otherwise the hint, if any.
                    supportingText = {
                        Text(
                            when {
                                text.isNotEmpty() && parsed == null -> stringResource(R.string.allowed_range, "$min–$max $unit")
                                hint != null -> hint
                                else -> "$min–$max $unit"
                            },
                        )
                    },
                    singleLine = true,
                    isError = text.isNotEmpty() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = {
                    parsed?.let(onChange)
                    editing = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Round button that steps once on tap and keeps stepping while held down. */
@Composable
private fun RepeatButton(icon: ImageVector, description: String, color: Color, enabled: Boolean, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val active by rememberUpdatedState(enabled)
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = if (enabled) 0.16f else 0.06f),
        modifier = Modifier.size(40.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { if (active) step(); active }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (active) step()
                        val repeat = launch {
                            delay(400)
                            while (isActive && active) {
                                step()
                                delay(70)
                            }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                    }
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color.copy(alpha = if (enabled) 1f else 0.4f))
        }
    }
}
