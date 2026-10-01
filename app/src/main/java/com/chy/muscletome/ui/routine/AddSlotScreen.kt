package com.chy.muscletome.ui.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.chy.muscletome.ui.components.SectionHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.ContentScale
import com.chy.muscletome.data.local.dao.ExerciseLibraryRow
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.TargetMovementType
import com.chy.muscletome.domain.model.MovementFamilies
import com.chy.muscletome.ui.components.ExerciseDemoImage
import com.chy.muscletome.ui.components.LedgerDivider
import com.chy.muscletome.ui.library.groupExerciseFamilies

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSlotScreen(
    onBack: () -> Unit,
    viewModel: AddSlotViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Family expansion survives rotation but not leaving the picker — the
    // library screen's saver pattern, scoped to this session's choices.
    val expandedFamilies = rememberSaveable(
        saver = mapSaver(
            save = { map -> map.mapValues { it.value } },
            restore = { restored ->
                mutableStateMapOf<String, Boolean>().apply {
                    restored.forEach { (key, value) -> this[key] = value as Boolean }
                }
            },
        ),
    ) { mutableStateMapOf<String, Boolean>() }

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

            // What each mode actually does at session time — the difference
            // is invisible until the workout starts otherwise.
            Text(
                if (state.isTargetMode) {
                    "We'll pick the exercise when you start: something that hits your " +
                        "selected muscles, fits your equipment, and you haven't done " +
                        "lately. A fresh pick every session — auto-rotate for accessories."
                } else {
                    "The exact exercise you pick, every time this day comes up — " +
                        "best for the heavy stuff you always want in the same slot."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
            } else if (state.selectedExerciseIds.size > 1) {
                // Multi-pick: offer to insert everything as one round-robin
                // group instead of separate slots.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Checkbox(
                        checked = state.asSuperset,
                        onCheckedChange = viewModel::setAsSuperset,
                    )
                    Text(
                        "Add as superset (train them round-robin)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
                    // Same family grouping as the Exercise Library: variations
                    // collapse under an expandable family header; a search hit
                    // inside a family auto-expands it so matches stay visible.
                    val familyGroups = groupExerciseFamilies(state.libraryRows)
                    familyGroups.forEach { group ->
                        val familyId = group.familyId
                        val members = group.rows.map(ExerciseLibraryRow::exercise)
                        if (familyId == null) {
                            val exercise = members.first()
                            item(key = "exercise:${exercise.id}") {
                                SlotExerciseRow(
                                    exercise = exercise,
                                    selected = exercise.id in state.selectedExerciseIds,
                                    onToggle = { viewModel.onExerciseSelected(exercise.id) },
                                )
                            }
                        } else {
                            item(key = "family:$familyId") {
                                val matchesSearch = state.query.isNotBlank() && members.any {
                                    it.name.contains(state.query, ignoreCase = true)
                                }
                                val expanded = matchesSearch || expandedFamilies[familyId] == true
                                val allSelected = members.all { it.id in state.selectedExerciseIds }
                                val someSelected = members.any { it.id in state.selectedExerciseIds }
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedFamilies[familyId] = !expanded }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Header checkbox: checked = every variation
                                        // picked, indeterminate = partial, unchecked
                                        // = none; a tap selects all or clears all.
                                        Checkbox(
                                            checked = allSelected,
                                            onCheckedChange = {
                                                viewModel.toggleFamily(members.map { it.id })
                                            },
                                            modifier = Modifier.semantics {
                                                if (someSelected && !allSelected) {
                                                    toggleableState = ToggleableState.Indeterminate
                                                }
                                            },
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                MovementFamilies.label(familyId) ?: familyId,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                "${members.size} variations",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            if (expanded) "−" else "+",
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                    }
                                    LedgerDivider()
                                    if (expanded) {
                                        members.forEach { variation ->
                                            SlotExerciseRow(
                                                exercise = variation,
                                                selected = variation.id in state.selectedExerciseIds,
                                                onToggle = {
                                                    viewModel.onExerciseSelected(variation.id)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
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

/**
 * One exercise row of the picker: checkbox + demo thumbnail, tap toggles.
 * Duplicates are impossible — results are keyed by exercise id, so picking
 * the same exercise twice (row tap or family select-all) just keeps one.
 */
@Composable
private fun SlotExerciseRow(
    exercise: ExerciseEntity,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = { Text(exercise.primaryMuscleGroupId) },
        leadingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                )
                if (!exercise.demoUri.isNullOrBlank()) {
                    ExerciseDemoImage(
                        uri = exercise.demoUri,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onToggle),
    )
}
