package com.chy.muscletome.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.ExerciseSource
import com.chy.muscletome.ui.components.LedgerDivider
import com.chy.muscletome.ui.components.LedgerIndex
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.MicroTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Exercise library") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExercise,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add exercise")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search") },
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedMuscleId == null,
                        onClick = { viewModel.onMuscleSelected(null) },
                        label = { Text("All") },
                    )
                }
                itemsIndexed(state.muscleGroups, key = { _, muscle -> muscle.id }) { _, muscle ->
                    FilterChip(
                        selected = state.selectedMuscleId == muscle.id,
                        onClick = {
                            val next = if (state.selectedMuscleId == muscle.id) null else muscle.id
                            viewModel.onMuscleSelected(next)
                        },
                        label = { Text(muscle.name) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (state.exercises.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            EmptyState(
                                title = "Nothing here yet",
                                body = if (state.query.isBlank() && state.selectedMuscleId == null) {
                                    "The library fills up from the seed catalog and wger imports."
                                } else {
                                    "No exercises match your search."
                                },
                            )
                            if (state.query.isBlank() && state.selectedMuscleId == null) {
                                Button(
                                    onClick = onAddExercise,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) { Text("Create custom exercise".uppercase()) }
                            }
                        }
                    }
                }
                itemsIndexed(state.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                    ExerciseRow(
                        exercise = exercise,
                        index = index,
                        onClick = { onOpenExercise(exercise.id) },
                    )
                    if (index < state.exercises.lastIndex) {
                        LedgerDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseEntity,
    index: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No image previews in the list — art lives on the exercise's own
        // detail card. Rows keep their ledger index.
        LedgerIndex(index = index + 1)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${exercise.movementType} · ${exercise.difficulty} · ${exercise.primaryMuscleGroupId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (exercise.isCustom) {
            MicroTag(text = "Custom")
        } else if (exercise.source == ExerciseSource.WGER) {
            MicroTag(text = "wger")
        }
    }
}
