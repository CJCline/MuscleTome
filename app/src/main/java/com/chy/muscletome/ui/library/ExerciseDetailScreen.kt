package com.chy.muscletome.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            !state.loaded -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.exercise == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Exercise not found")
            }
            else -> ExerciseDetailContent(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
internal fun ExerciseDetailContent(
    state: ExerciseDetailUiState,
    modifier: Modifier = Modifier,
) {
    val exercise = checkNotNull(state.exercise)
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseInfoContent(
            exercise = exercise,
            equipment = state.equipment,
            secondaryMuscles = state.secondaryMuscles,
            primaryMuscleName = state.primaryMuscleName,
        )

        // History ledger block — numbers are monospace, the ledger way.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("History")
            val history = state.history
            if (history.lastSet == null) {
                Text(
                    "No sets logged yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 13.dp),
                ) {
                    MonoText(
                        text = "Last set: ${history.lastSet.weight} × ${history.lastSet.reps}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MonoText(
                        text = "Best weight: ${history.bestWeight ?: 0.0}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MonoText(
                        text = "Logged in ${history.sessionCount} session" +
                            if (history.sessionCount == 1) "" else "s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ExerciseDetailContentPreview() {
    MaterialTheme {
        ExerciseDetailContent(
            state = ExerciseDetailUiState(
                exercise = ExerciseEntity(
                    id = "squat",
                    name = "Barbell Back Squat",
                    description = "A compound lower-body strength exercise.",
                    movementPattern = MovementPattern.SQUAT,
                    movementType = MovementType.COMPOUND,
                    primaryMuscleGroupId = "quads",
                    difficulty = Difficulty.INTERMEDIATE,
                ),
                equipment = listOf(
                    EquipmentEntity("barbell", "Barbell"),
                    EquipmentEntity("rack", "Rack"),
                ),
                secondaryMuscles = listOf(
                    MuscleGroupEntity("glutes", "Glutes", "legs"),
                    MuscleGroupEntity("hamstrings", "Hamstrings", "legs"),
                ),
                history = ExerciseHistory(
                    lastSet = SetLogEntity(
                        id = "set-1",
                        sessionSlotResultId = "result-1",
                        setNumber = 3,
                        weight = 100.0,
                        reps = 8,
                        completedAtEpochMs = 0L,
                    ),
                    bestWeight = 105.0,
                    sessionCount = 4,
                ),
                primaryMuscleName = "Quads",
                loaded = true,
            ),
        )
    }
}
