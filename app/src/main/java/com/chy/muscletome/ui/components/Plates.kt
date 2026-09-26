package com.chy.muscletome.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chy.muscletome.ui.theme.MonoFont
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.ui.text.TextStyle

/**
 * Weight-plate primitives: the structural silhouettes of the brand. Vertical
 * spine ticks for set progress ("every rep, a page"), a plate-rim ring for the
 * rest timer and radial progress, and a ledger bar for volume.
 */

/**
 * Set progress as a row of 3dp vertical book-spine ticks. Completed sets fill
 * amber; remaining sets are steel outlines. Each tick animates its fill when
 * it completes.
 */
@Composable
fun SetTicks(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.outline,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total.coerceAtLeast(0)) { index ->
            val isDone = index < done
            val color by animateColorAsState(
                targetValue = if (isDone) accent else track,
                animationSpec = tween(durationMillis = 250),
                label = "tick-color",
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (isDone) 22.dp else 16.dp)
                    .background(color),
            )
        }
    }
}

/**
 * Weight-plate ring: a thick plate rim with a sweep arc and a hub hole.
 * Used for the rest timer and radial completion. [progress] in 0..1 —
 * the arc sweeps clockwise as progress increases.
 */
@Composable
fun PlateRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 120.dp,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    strokeWidth: Float = 12f,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "plate-ring-progress",
    )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val ringSize = Size(diameter, diameter)

            // Plate rim (track)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = ringSize,
                style = stroke,
            )

            // Amber sweep
            if (animated > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = ringSize,
                    style = stroke,
                )
            }

            // Hub hole — the plate's center bore
            val holeRadius = diameter * 0.12f
            drawCircle(
                color = trackColor,
                radius = holeRadius,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = strokeWidth / 2f),
            )
        }
        content()
    }
}

/**
 * Ledger volume bar: thin amber fill over a steel track. [fraction] in 0..1.
 * Data-amber doing its job — showing data.
 */
@Composable
fun VolumeBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "volume-bar-fraction",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(trackColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(4.dp)
                .background(fillColor),
        )
    }
}

/** Monospace data text — shorthand for the ledger number style. */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        text = text,
        style = style.copy(fontFamily = MonoFont),
        color = color,
        modifier = modifier,
    )
}

/**
 * Weight-plate chip: a small disc silhouette used for the metric stepper
 * buttons — plus and minus plates. Optional [onLongClick] (e.g. half-plate
 * long-press steps).
 */
@Composable
fun PlateChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .size(48.dp)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
