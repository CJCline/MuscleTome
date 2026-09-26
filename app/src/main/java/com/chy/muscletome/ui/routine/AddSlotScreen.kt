package com.chy.muscletome.ui.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.chy.muscletome.ui.components.SectionHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.model.TargetMovementType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSlotScreen(
    onBack: () -> Unit,
    viewModel: AddSlotViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isTargetMode) "Add target slot" else "Add exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.isTargetMode,
                    onClick = { viewModel.setTargetMode(false) },
                    label = { Text("Fixed exercise") },
                )
                FilterChip(
                    selected = state.isTargetMode,
                    onClick = { viewModel.setTargetMode(true) },
                    label = { Text("Target muscle") },
                )
            }


            if (state.isTargetMode) {
                SectionHeader("Movement preference", modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetMovementType.entries.forEach { type ->
                        FilterChip(
                            selected = state.targetMovement == type,
                            onClick = { viewModel.setTargetMovement(type) },
                            label = { Text(type.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (state.isTargetMode) "Search muscles" else "Search exercises") },
                singleLine = true,
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (state.isTargetMode) {
                    items(state.muscleGroups, key = { it.id }) { muscle ->
                        ListItem(
                            headlineContent = { Text(muscle.name) },
                            leadingContent = {
                                Checkbox(
                                    checked = state.selectedMuscleIds.contains(muscle.id),
                                    onCheckedChange = { viewModel.toggleMuscle(muscle.id) },
                                )
                            },
                            modifier = Modifier.clickable { viewModel.toggleMuscle(muscle.id) },
                        )
                    }
                } else {
                    items(state.exercises, key = { it.id }) { exercise ->
                        ListItem(
                            headlineContent = { Text(exercise.name) },
                            supportingContent = { Text(exercise.primaryMuscleGroupId) },
                            leadingContent = {
                                Checkbox(
                                    checked = state.selectedExerciseIds.contains(exercise.id),
                                    onCheckedChange = { viewModel.onExerciseSelected(exercise.id) },
                                )
                            },
                            modifier = Modifier.clickable { viewModel.onExerciseSelected(exercise.id) },
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                val count = if (state.isTargetMode) state.selectedMuscleIds.size else state.selectedExerciseIds.size
                Text(if (count > 1) "Add $count exercises" else "Add to day")
            }
        }
    }
}
