package com.chy.muscletome.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.chy.muscletome.data.local.entity.ExerciseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    onAddExercise: () -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Exercise library") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExercise) {
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
                items(state.muscleGroups, key = { it.id }) { muscle ->
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.exercises, key = { it.id }) { exercise ->
                    ExerciseRow(exercise)
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(exercise.name) },
            supportingContent = {
                Text(
                    "${exercise.movementType} · ${exercise.difficulty} · ${exercise.primaryMuscleGroupId}",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                if (exercise.isCustom) {
                    Text("Custom", style = MaterialTheme.typography.labelSmall)
                }
            },
            leadingContent = {
                if (!exercise.demoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = exercise.demoUri,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        )
    }
}
