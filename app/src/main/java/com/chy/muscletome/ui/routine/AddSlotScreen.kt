package com.chy.muscletome.ui.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.dao.ExerciseLibraryRow
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.MovementFamilies
import com.chy.muscletome.domain.model.TargetMovementType
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.BrutalistOutlinedButton
import com.chy.muscletome.ui.components.ExerciseDemoImage
import com.chy.muscletome.ui.components.LedgerDivider
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField
import com.chy.muscletome.ui.library.groupExerciseFamilies
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSlotScreen(
    onBack: () -> Unit,
    onAddExercise: () -> Unit = {},
    viewModel: AddSlotViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                title = { Text(if (state.isTargetMode) "ADD TARGET SLOT" else "ADD EXERCISE", style = MuscleTomeTextStyles.heading) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "BACK")
                    }
                },
                actions = {
                    if (!state.isTargetMode) {
                        TextButton(onClick = onAddExercise) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("NEW EXERCISE", style = MuscleTomeTextStyles.button)
                        }
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
                    label = { Text("FIXED EXERCISE", style = MuscleTomeTextStyles.tag) },
                )
                FilterChip(
                    selected = state.isTargetMode,
                    onClick = { viewModel.setTargetMode(true) },
                    label = { Text("TARGET MUSCLE", style = MuscleTomeTextStyles.tag) },
                )
            }

            Text(
                if (state.isTargetMode) {
                    "AUTOMATIC EXERCISE SELECTION AT SESSION START BASED ON TARGET MUSCLES AND EQUIPMENT."
                } else {
                    "EXACT FIXED EXERCISE EVERY SESSION."
                },
                style = MuscleTomeTextStyles.systemMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.isTargetMode) {
                SectionHeader("MOVEMENT PREFERENCE", modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetMovementType.entries.forEach { type ->
                        FilterChip(
                            selected = state.targetMovement == type,
                            onClick = { viewModel.setTargetMovement(type) },
                            label = { Text(type.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                        )
                    }
                }
            } else if (state.selectedExerciseIds.size > 1) {
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
                        "ADD AS SUPERSET (ROUND-ROBIN)",
                        style = MuscleTomeTextStyles.label,
                    )
                }
            }

            SelectOnFocusOutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (state.isTargetMode) "SEARCH MUSCLES" else "SEARCH EXERCISES", style = MuscleTomeTextStyles.label) },
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
                            headlineContent = { Text(muscle.name.uppercase(Locale.US), style = MuscleTomeTextStyles.button) },
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
                    val familyGroups = groupExerciseFamilies(state.libraryRows)
                    if (familyGroups.isEmpty()) {
                        item(key = "create_custom_empty_state") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    if (state.query.isNotBlank()) {
                                        "NO EXERCISES MATCHING \"${state.query.uppercase(Locale.US)}\""
                                    } else {
                                        "NO EXERCISES AVAILABLE"
                                    },
                                    style = MuscleTomeTextStyles.systemMessage,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AccentButton(
                                    text = if (state.query.isNotBlank()) {
                                        "CREATE \"${state.query.uppercase(Locale.US)}\" EXERCISE"
                                    } else {
                                        "CREATE NEW EXERCISE"
                                    },
                                    onClick = onAddExercise,
                                )
                            }
                        }
                    } else {
                        item(key = "create_custom_action_header") {
                            BrutalistOutlinedButton(
                                text = "CREATE CUSTOM EXERCISE",
                                onClick = onAddExercise,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )
                        }
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
                                                .semantics {
                                                    contentDescription = "${MovementFamilies.label(familyId) ?: familyId} EXERCISE FAMILY"
                                                    stateDescription = if (expanded) "EXPANDED" else "COLLAPSED"
                                                }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
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
                                                    (MovementFamilies.label(familyId) ?: familyId).uppercase(Locale.US),
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                Text(
                                                    "${members.size} VARIATIONS",
                                                    style = MuscleTomeTextStyles.tag,
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
            }

            val count = if (state.isTargetMode) state.selectedMuscleIds.size else state.selectedExerciseIds.size
            AccentButton(
                text = if (count > 1) "ADD $count EXERCISES" else "ADD TO DAY",
                onClick = viewModel::save,
                enabled = state.canSave,
                minHeight = 56.dp,
            )
        }
    }
}

@Composable
private fun SlotExerciseRow(
    exercise: ExerciseEntity,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(exercise.name.uppercase(Locale.US), style = MuscleTomeTextStyles.button) },
        supportingContent = { Text(exercise.primaryMuscleGroupId.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
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
