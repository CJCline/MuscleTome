package com.chy.muscletome.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.ui.components.ExerciseDemoHero
import com.chy.muscletome.ui.components.LedgerDivider
import com.chy.muscletome.ui.components.SectionHeader

/**
 * The wger-sourced reference info for an exercise: demo image, description,
 * movement/difficulty, muscles, and equipment. Shared between the exercise
 * detail screen and the in-workout info sheet.
 */
@Composable
internal fun ExerciseInfoContent(
    exercise: ExerciseEntity,
    equipment: List<EquipmentEntity>,
    secondaryMuscles: List<MuscleGroupEntity>,
    primaryMuscleName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fixed 4:3 frame: the hero holds its size while the bitmap
        // streams in, and every exercise with art reads consistently.
        ExerciseDemoHero(
            uri = exercise.demoUri,
            contentDescription = exercise.name,
        )

        LedgerInfoBlock(label = "About") {
            Text(
                listOfNotNull(
                    exercise.description.takeIf { it.isNotBlank() },
                    "Movement: ${exercise.movementType.name.lowercase()} " +
                        "(${exercise.movementPattern.name.lowercase()})",
                    "Difficulty: ${exercise.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    if (exercise.isCustom) "Custom exercise" else null,
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LedgerInfoBlock(label = "Muscles") {
            val secondary = secondaryMuscles.joinToString { it.name }
            Text(
                if (secondary.isBlank()) primaryMuscleName
                else "$primaryMuscleName (also: $secondary)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LedgerInfoBlock(label = "Equipment") {
            Text(
                if (equipment.isEmpty()) "None"
                else equipment.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A ledger entry block: section header, steel body, hairline below. */
@Composable
private fun LedgerInfoBlock(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(label)
        Row(modifier = Modifier.padding(start = 13.dp)) { content() }
        Spacer(Modifier.padding(top = 0.dp))
        LedgerDivider()
    }
}
