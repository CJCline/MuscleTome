package com.chy.muscletome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.template.RoutineTemplate
import com.chy.muscletome.ui.theme.MonoFont

/**
 * Ledger primitives — the recurring "book spine" motif: a heavy vertical bar
 * anchoring section headers, stat columns, and empty states. Uppercase labels
 * with wide tracking; monospace numbers for data.
 */

/**
 * Section header: 3dp amber vertical spine + uppercase heavy label.
 * The signature MuscleTome section rule.
 */
@Composable
fun SectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Ledger hairline — 1dp steel rule between sections. */
@Composable
fun LedgerDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * One column of the stats ledger: uppercase micro-label over a large
 * monospace value. Numbers are data — data is monospace.
 */
@Composable
fun StatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = MonoFont),
            color = valueColor,
        )
    }
}

/**
 * Ledger row index — the monospace "01 / 02 / 03" column that makes every
 * list read like a page in a training tome.
 */
@Composable
fun LedgerIndex(
    index: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = index.toString().padStart(2, '0'),
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFont),
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier,
    )
}

/**
 * Empty state: cream headline, steel body. Use for "No routines yet" style
 * screens — the tagline lives here too.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Amber micro-tag — small uppercase status chip (OPEN, REROLLED, +30s…). */
@Composable
fun MicroTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/**
 * Starter-program card for the Home templates banner: a spine card with the
 * template name, blurb, and its day/slot/auto-pick stats. Tapping adds the
 * program to the user's routines.
 */
@Composable
fun TemplateCard(
    template: RoutineTemplate,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            .clickable(enabled = enabled, onClick = onAdd),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(IntrinsicSize.Max)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Icon(Icons.Default.Add, contentDescription = null)
            }
            Text(
                text = template.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (template.days.isNotEmpty()) {
                val slots = template.days.sumOf { it.slots.size }
                val autoPicked = template.days.sumOf { day ->
                    day.slots.count { it.type == SlotType.TARGET }
                }
                MicroTag(
                    text = "${template.days.size} days · $slots slots · $autoPicked auto-picked",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
