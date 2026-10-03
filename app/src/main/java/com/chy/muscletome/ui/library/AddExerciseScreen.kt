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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.BrutalistOutlinedButton
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

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
                title = { Text(if (state.isEdit) "EDIT EXERCISE" else "NEW EXERCISE", style = MuscleTomeTextStyles.heading) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "BACK")
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
            SelectOnFocusOutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("NAME", style = MuscleTomeTextStyles.label) },
                singleLine = true,
                isError = state.nameTaken,
                supportingText = if (state.nameTaken) {
                    { Text("EXERCISE WITH THIS NAME ALREADY EXISTS", style = MuscleTomeTextStyles.systemMessage) }
                } else null,
            )
            SelectOnFocusOutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("DESCRIPTION", style = MuscleTomeTextStyles.label) },
            )

            SelectOnFocusOutlinedTextField(
                value = state.instructionsText,
                onValueChange = viewModel::onInstructionsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("FORM STEPS (ONE PER LINE)", style = MuscleTomeTextStyles.label) },
                minLines = 3,
            )
            SelectOnFocusOutlinedTextField(
                value = state.mediaUri,
                onValueChange = viewModel::onMediaUriChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OPTIONAL MEDIA URI", style = MuscleTomeTextStyles.label) },
                singleLine = true,
            )

            SectionHeader("PRIMARY MUSCLE")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.muscles.forEach { muscle ->
                    FilterChip(
                        selected = state.primaryMuscleGroupId == muscle.id,
                        onClick = { viewModel.onPrimaryMuscleSelected(muscle.id) },
                        label = { Text(muscle.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            SectionHeader("SECONDARY MUSCLE TARGETS")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.muscles.filter { it.id != state.primaryMuscleGroupId }.forEach { muscle ->
                    FilterChip(
                        selected = muscle.id in state.secondaryMuscleIds,
                        onClick = { viewModel.toggleSecondaryMuscle(muscle.id) },
                        label = { Text(muscle.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            SectionHeader("MOVEMENT TYPE")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MovementType.entries.forEach { type ->
                    FilterChip(
                        selected = state.movementType == type,
                        onClick = { viewModel.onMovementTypeSelected(type) },
                        label = { Text(type.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            SectionHeader("MOVEMENT PATTERN")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MovementPattern.entries.forEach { pattern ->
                    FilterChip(
                        selected = state.movementPattern == pattern,
                        onClick = { viewModel.onMovementPatternSelected(pattern) },
                        label = { Text(pattern.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            SectionHeader("DIFFICULTY")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = state.difficulty == difficulty,
                        onClick = { viewModel.onDifficultySelected(difficulty) },
                        label = { Text(difficulty.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("UNILATERAL", style = MuscleTomeTextStyles.label, modifier = Modifier.weight(1f))
                Switch(checked = state.unilateral, onCheckedChange = viewModel::onUnilateralChange)
            }

            SectionHeader("MOVEMENT FAMILY")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.families.forEach { family ->
                    FilterChip(
                        selected = state.selectedFamilyId == family.id,
                        onClick = {
                            viewModel.onFamilySelected(
                                if (state.selectedFamilyId == family.id) null else family.id,
                            )
                        },
                        label = { Text(family.displayName.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }
            SelectOnFocusOutlinedTextField(
                value = state.newFamilyName,
                onValueChange = viewModel::onNewFamilyNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("NEW FAMILY...", style = MuscleTomeTextStyles.label) },
                singleLine = true,
                isError = state.newFamilyError != null,
                supportingText = state.newFamilyError?.let { error ->
                    { Text(error.uppercase(Locale.US), style = MuscleTomeTextStyles.systemMessage) }
                },
            )
            BrutalistOutlinedButton(
                text = "CREATE FAMILY",
                onClick = viewModel::createFamily,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("EQUIPMENT")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.equipment.forEach { item ->
                    FilterChip(
                        selected = item.id in state.selectedEquipmentIds,
                        onClick = { viewModel.toggleEquipment(item.id) },
                        label = { Text(item.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            AccentButton(
                text = "SAVE EXERCISE",
                onClick = viewModel::save,
                enabled = state.canSave,
                minHeight = 56.dp,
            )
        }
    }
}
