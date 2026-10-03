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
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

/**
 * The reference info for an exercise: demo image, description,
 * movement/difficulty, muscles, and equipment.
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
        ExerciseDemoHero(
            uri = exercise.demoUri,
            contentDescription = exercise.name.uppercase(Locale.US),
        )

        LedgerInfoBlock(label = "ABOUT") {
            Text(
                listOfNotNull(
                    exercise.description.takeIf { it.isNotBlank() }?.uppercase(Locale.US),
                    "MOVEMENT: ${exercise.movementType.name} (${exercise.movementPattern.name})",
                    "DIFFICULTY: ${exercise.difficulty.name}",
                    if (exercise.isCustom) "CUSTOM EXERCISE" else null,
                ).joinToString("\n"),
                style = MuscleTomeTextStyles.systemMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LedgerInfoBlock(label = "MUSCLES") {
            val secondary = secondaryMuscles.joinToString { it.name }.uppercase(Locale.US)
            Text(
                if (secondary.isBlank()) primaryMuscleName.uppercase(Locale.US)
                else "${primaryMuscleName.uppercase(Locale.US)} (SECONDARY: $secondary)",
                style = MuscleTomeTextStyles.systemMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LedgerInfoBlock(label = "EQUIPMENT") {
            Text(
                if (equipment.isEmpty()) "NONE"
                else equipment.joinToString { it.name }.uppercase(Locale.US),
                style = MuscleTomeTextStyles.systemMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
