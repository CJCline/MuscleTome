package com.chy.muscletome.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity

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
        if (!exercise.demoUri.isNullOrBlank()) {
            AsyncImage(
                model = exercise.demoUri,
                contentDescription = exercise.name,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("About") },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            exercise.description.takeIf { it.isNotBlank() },
                            "Movement: ${exercise.movementType.name.lowercase()} " +
                                "(${exercise.movementPattern.name.lowercase()})",
                            "Difficulty: ${exercise.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            if (exercise.isCustom) "Custom exercise" else null,
                        ).joinToString("\n"),
                    )
                },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Muscles") },
                supportingContent = {
                    val secondary = secondaryMuscles.joinToString { it.name }
                    Text(
                        if (secondary.isBlank()) primaryMuscleName
                        else "$primaryMuscleName (also: $secondary)",
                    )
                },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Equipment") },
                supportingContent = {
                    Text(
                        if (equipment.isEmpty()) "None"
                        else equipment.joinToString { it.name },
                    )
                },
            )
        }
    }
}
