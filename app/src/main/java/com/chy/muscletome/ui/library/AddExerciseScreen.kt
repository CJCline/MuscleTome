package com.chy.muscletome.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.ui.components.SectionHeader

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
                title = { Text(if (state.isEdit) "Edit exercise" else "New exercise") },
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
            com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
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
            com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
            )

            com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                value = state.instructionsText,
                onValueChange = viewModel::onInstructionsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Form steps (one per line)") },
                minLines = 3,
            )
            com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                value = state.mediaUri,
                onValueChange = viewModel::onMediaUriChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Optional media URI") },
                singleLine = true,
            )

            SectionHeader("Primary muscle")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.muscles.forEach { muscle ->
                    FilterChip(
                        selected = state.primaryMuscleGroupId == muscle.id,
                        onClick = { viewModel.onPrimaryMuscleSelected(muscle.id) },
                        label = { Text(muscle.name) },
                    )
                }
            }

            SectionHeader("Secondary muscle targets")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.muscles.filter { it.id != state.primaryMuscleGroupId }.forEach { muscle ->
                    FilterChip(
                        selected = muscle.id in state.secondaryMuscleIds,
                        onClick = { viewModel.toggleSecondaryMuscle(muscle.id) },
                        label = { Text(muscle.name) },
                    )
                }
            }

            SectionHeader("Movement type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MovementType.entries.forEach { type ->
                    FilterChip(
                        selected = state.movementType == type,
                        onClick = { viewModel.onMovementTypeSelected(type) },
                        label = { Text(type.name) },
                    )
                }
            }

            SectionHeader("Movement pattern")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MovementPattern.entries.forEach { pattern ->
                    FilterChip(
                        selected = state.movementPattern == pattern,
                        onClick = { viewModel.onMovementPatternSelected(pattern) },
                        label = { Text(pattern.name) },
                    )
                }
            }

            SectionHeader("Difficulty")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = state.difficulty == difficulty,
                        onClick = { viewModel.onDifficultySelected(difficulty) },
                        label = { Text(difficulty.name) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Unilateral", modifier = Modifier.weight(1f))
                Switch(checked = state.unilateral, onCheckedChange = viewModel::onUnilateralChange)
            }

            SectionHeader("Movement family")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.families.forEach { family ->
                    FilterChip(
                        selected = state.selectedFamilyId == family.id,
                        onClick = {
                            // Tap again to clear — family is optional.
                            viewModel.onFamilySelected(
                                if (state.selectedFamilyId == family.id) null else family.id,
                            )
                        },
                        label = { Text(family.displayName) },
                    )
                }
            }
            com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                value = state.newFamilyName,
                onValueChange = viewModel::onNewFamilyNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New family…") },
                singleLine = true,
                isError = state.newFamilyError != null,
                supportingText = state.newFamilyError?.let { error ->
                    { Text(error) }
                },
            )
            Button(onClick = viewModel::createFamily, modifier = Modifier.fillMaxWidth()) {
                Text("Create family")
            }

            SectionHeader("Equipment")
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
