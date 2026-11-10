package io.github.patrickric.akkuhealthalarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP = 270f

@Composable
fun levelColor(level: Int, low: Int, high: Int): Color = when {
    level <= low -> MaterialTheme.colorScheme.error
    level >= high -> LocalExtraColors.current.caution
    else -> MaterialTheme.colorScheme.primary
}

/** Ring gauge: thick ring = charge level, thin outer ring = the chosen range. */
@Composable
fun BatteryGauge(
    level: Int,
    low: Int,
    high: Int,
    charging: Boolean,
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val progress by animateFloatAsState(level / 100f, animationSpec = tween(900, easing = FastOutSlowInEasing), label = "level")
    val color by animateColorAsState(levelColor(level, low, high), label = "levelColor")
    val track = scheme.surfaceContainerHighest
    val band = scheme.primary.copy(alpha = 0.35f)
    val transition = rememberInfiniteTransition(label = "charging")
    // Position of the light that runs along the ring while charging (0 = start, 1 = tip).
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "flow",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val bandStroke = stroke * 0.32f
            val bandInset = bandStroke / 2
            val ringInset = bandStroke + stroke * 0.55f + stroke / 2
            val ringSize = Size(size.width - 2 * ringInset, size.height - 2 * ringInset)
            val ringTopLeft = Offset(ringInset, ringInset)

            drawArc(
                color = band,
                startAngle = START_ANGLE + SWEEP * low / 100f,
                sweepAngle = SWEEP * (high - low) / 100f,
                useCenter = false,
                topLeft = Offset(bandInset, bandInset),
                size = Size(size.width - 2 * bandInset, size.height - 2 * bandInset),
                style = Stroke(bandStroke, cap = StrokeCap.Round),
            )
            drawArc(track, START_ANGLE, SWEEP, false, ringTopLeft, ringSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress > 0f) {
                if (charging) {
                    // While charging the ring brightens towards the tip (visible even with animations off).
                    val dark = lerp(color, Color.Black, 0.35f)
                    val bright = lerp(color, Color.White, 0.45f)
                    rotate(START_ANGLE, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to dark,
                                (SWEEP * progress / 360f) to bright,
                                1f to dark,
                                center = center,
                            ),
                            startAngle = 0f,
                            sweepAngle = SWEEP * progress,
                            useCenter = false,
                            topLeft = ringTopLeft,
                            size = ringSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                } else {
                    drawArc(color, START_ANGLE, SWEEP * progress, false, ringTopLeft, ringSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                if (charging) {
                    // Soft light running from the start of the ring to the tip, like energy flowing in.
                    val head = SWEEP * progress * flow
                    val tail = minOf(50f, head)
                    if (tail > 1f) {
                        val fadeOut = ((1f - flow) * 6f).coerceIn(0f, 1f)
                        rotate(START_ANGLE + head - tail, pivot = center) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    0f to Color.White.copy(alpha = 0f),
                                    tail / 360f to Color.White.copy(alpha = 0.7f * fadeOut),
                                    center = center,
                                ),
                                startAngle = 0f,
                                sweepAngle = tail,
                                useCenter = false,
                                topLeft = ringTopLeft,
                                size = ringSize,
                                style = Stroke(stroke * 0.6f, cap = StrokeCap.Butt),
                            )
                        }
                    }
                    // Glowing tip while charging.
                    val angle = Math.toRadians((START_ANGLE + SWEEP * progress).toDouble())
                    val r = ringSize.width / 2
                    val c = Offset(size.width / 2 + r * cos(angle).toFloat(), size.height / 2 + r * sin(angle).toFloat())
                    drawCircle(color.copy(alpha = 0.35f * pulse), radius = stroke * 0.95f, center = c)
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = stroke * 0.22f, center = c)
                }
            }
        }
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            // Number and bolt grow with the ring, so they fit on small and large screens.
            val unit = maxWidth.value
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = charging, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = LocalExtraColors.current.caution.copy(alpha = 0.6f + 0.4f * pulse),
                            modifier = Modifier.size((unit * 0.27f).dp),
                        )
                    }
                    Text(
                        "$level",
                        fontSize = (unit * if (charging && level >= 100) 0.21f else 0.25f).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp,
                        color = scheme.onSurface,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        "%",
                        fontSize = (unit * 0.095f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline().padding(start = 2.dp),
                    )
                }
                Text(
                    statusText,
                    fontSize = (unit * 0.07f).sp,
                    fontWeight = FontWeight.Medium,
                    color = if (charging) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

