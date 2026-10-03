package com.chy.muscletome.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.domain.session.PlateMath
import com.chy.muscletome.domain.session.WeightUnits
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles

/**
 * Plate loader sheet: how to build the weight-field target from a bar plus
 * standard plates, per side.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlateCalculatorSheet(
    targetWeight: Double,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onUseLoadable: (Double) -> Unit,
) {
    var barWeight by rememberSaveable(unit) { mutableStateOf(PlateMath.defaultBar(unit)) }
    val breakdown = remember(targetWeight, barWeight, unit) {
        PlateMath.breakdown(targetWeight = targetWeight, barWeight = barWeight, unit = unit)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("PLATE LOADER")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "TARGET",
                        style = MuscleTomeTextStyles.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MonoText(
                        text = WeightUnits.display(targetWeight, unit),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        "BAR",
                        style = MuscleTomeTextStyles.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MonoText(
                        text = WeightUnits.display(barWeight, unit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlateMath.barsFor(unit).forEach { bar ->
                    FilterChip(
                        selected = bar == barWeight,
                        onClick = { barWeight = bar },
                        label = { Text("${WeightUnits.displayText(bar)} BAR", style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            if (breakdown.plates.isEmpty() && breakdown.isAchievable) {
                Text(
                    "BAR ONLY — NO PLATES REQUIRED",
                    style = MuscleTomeTextStyles.systemMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    breakdown.plates.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlateDisc(
                                    weight = pair.weight,
                                    maxWeight = breakdown.plates.first().weight,
                                )
                                MonoText(
                                    text = WeightUnits.displayText(pair.weight),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            MonoText(
                                text = "× ${pair.countPerSide} / SIDE",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "PER SIDE: ${breakdown.perSideLabel.uppercase()}",
                        style = MuscleTomeTextStyles.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                breakdown.isAchievable -> Unit
                breakdown.leftoverPerSide < 0 -> Text(
                    "TARGET IS BELOW BAR WEIGHT",
                    style = MuscleTomeTextStyles.systemMessage,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Text(
                    "NO PLATE FOR REMAINING ${WeightUnits.display(breakdown.leftoverPerSide, unit)} PER SIDE",
                    style = MuscleTomeTextStyles.systemMessage,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "LOADS AS",
                        style = MuscleTomeTextStyles.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MonoText(
                        text = WeightUnits.display(breakdown.loadableWeight, unit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (!breakdown.isAchievable && (breakdown.leftoverPerSide > 0)) {
                    AccentButton(
                        text = "USE ${WeightUnits.displayText(breakdown.loadableWeight)}",
                        onClick = { onUseLoadable(breakdown.loadableWeight) },
                        minHeight = 44.dp,
                    )
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PlateDisc(
    weight: Double,
    maxWeight: Double,
    modifier: Modifier = Modifier,
) {
    val fraction = (weight / maxWeight.coerceAtLeast(1.0)).toFloat().coerceIn(0.25f, 1f)
    val diameter = (26 + 22 * fraction).dp
    Box(
        modifier = modifier
            .size(diameter)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}
