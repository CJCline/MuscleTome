package com.chy.muscletome.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.model.MovementType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseScreen(
    onBack: () -> Unit,
    viewModel: AddExerciseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New exercise") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                isError = state.nameTaken,
                supportingText = if (state.nameTaken) {
                    { Text("An exercise with this name already exists") }
                } else {
                    null
                },
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
            )

            Text("Primary muscle")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.muscles.forEach { muscle ->
                    FilterChip(
                        selected = state.primaryMuscleGroupId == muscle.id,
                        onClick = { viewModel.onPrimaryMuscleSelected(muscle.id) },
                        label = { Text(muscle.name) },
                    )
                }
            }

            Text("Movement type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MovementType.entries.forEach { type ->
                    FilterChip(
                        selected = state.movementType == type,
                        onClick = { viewModel.onMovementTypeSelected(type) },
                        label = { Text(type.name) },
                    )
                }
            }

            Text("Equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.equipment.forEach { item ->
                    FilterChip(
                        selected = item.id in state.selectedEquipmentIds,
                        onClick = { viewModel.toggleEquipment(item.id) },
                        label = { Text(item.name) },
                    )
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}
