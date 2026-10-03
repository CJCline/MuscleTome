package com.chy.muscletome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chy.muscletome.ui.theme.CastIron
import com.chy.muscletome.ui.theme.Ink
import com.chy.muscletome.ui.theme.MutedGrayBorder
import com.chy.muscletome.ui.theme.MonoFont
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles

/**
 * Brutalist Primitive Composable 1: BrutalistCard
 * Hard 1px border (#5A5A5A or accent), sharp 0-2dp corner radius, OLED near-black surface.
 * Optional spine bar on the left.
 */
@Composable
fun BrutalistCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MutedGrayBorder,
    backgroundColor: Color = CastIron,
    spineColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor)
            .then(clickableModifier),
    ) {
        if (spineColor != null) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Max)
                    .background(spineColor),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
        ) {
            content()
        }
    }
}

/**
 * Brutalist Primitive Composable 2: LedgerRow
 * Compact, immutable logged-set or list row with monospace index ("01"),
 * monospaced data values, bracket tags, and 1px hard divider line.
 */
@Composable
fun LedgerRow(
    index: Int,
    primaryText: String,
    modifier: Modifier = Modifier,
    dataText: String? = null,
    bracketTag: String? = null,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = index.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFont),
                    color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dataText != null) {
                    Text(
                        text = dataText,
                        style = MuscleTomeTextStyles.dataValue,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (bracketTag != null) {
                    Text(
                        text = MuscleTomeTextStyles.formatBracket(bracketTag),
                        style = MuscleTomeTextStyles.dataBracket,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        LedgerDivider()
    }
}

/**
 * Brutalist Primitive Composable 3: AccentButton
 * Solid hazard yellow block (#FFD400) with bold black uppercase text.
 * Primary action button anchored in thumb zone (height >= 56dp by default).
 */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 56.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Ink,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(minHeight),
    ) {
        Text(
            text = text.uppercase(),
            style = MuscleTomeTextStyles.button,
            color = contentColor,
        )
    }
}

/**
 * Brutalist Primitive Composable 4: BrutalistOutlinedButton
 * Hard 1px steel border button with uppercase text and sharp corners.
 */
@Composable
fun BrutalistOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
    borderColor: Color = MutedGrayBorder,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.height(minHeight),
    ) {
        Text(
            text = text.uppercase(),
            style = MuscleTomeTextStyles.button,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
