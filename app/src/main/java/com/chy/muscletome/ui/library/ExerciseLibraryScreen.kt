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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.ExerciseSources
import com.chy.muscletome.domain.model.MovementFamilies
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.LedgerDivider
import com.chy.muscletome.ui.components.LedgerIndex
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenImportReview: () -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expandedFamilies = rememberSaveable(saver = mapSaver(
        save = { map -> map.mapValues { it.value } },
        restore = { restored -> mutableStateMapOf<String, Boolean>().apply {
            restored.forEach { (key, value) -> this[key] = value as Boolean }
        } },
    )) { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("EXERCISE LIBRARY", style = MuscleTomeTextStyles.heading) }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExercise,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.Add, contentDescription = "ADD EXERCISE")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenImportReview) {
                    Text("IMPORT REVIEW", style = MuscleTomeTextStyles.button)
                }
            }

            SelectOnFocusOutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("SEARCH", style = MuscleTomeTextStyles.label) },
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedMuscleId == null,
                        onClick = { viewModel.onMuscleSelected(null) },
                        label = { Text("ALL", style = MuscleTomeTextStyles.tag) },
                    )
                }
                itemsIndexed(state.muscleGroups, key = { _, muscle -> muscle.id }) { _, muscle ->
                    FilterChip(
                        selected = state.selectedMuscleId == muscle.id,
                        onClick = {
                            val next = if (state.selectedMuscleId == muscle.id) null else muscle.id
                            viewModel.onMuscleSelected(next)
                        },
                        label = { Text(muscle.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
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
                                title = "NO EXERCISES FOUND",
                                body = if (state.query.isBlank() && state.selectedMuscleId == null) {
                                    "THE LIBRARY FILLS FROM THE SEED CATALOG AND IMPORTS."
                                } else {
                                    "NO EXERCISES MATCH YOUR SEARCH."
                                },
                            )
                            if (state.query.isBlank() && state.selectedMuscleId == null) {
                                AccentButton(
                                    text = "CREATE CUSTOM EXERCISE",
                                    onClick = onAddExercise,
                                )
                            }
                        }
                    }
                }
                val familyGroups = groupExerciseFamilies(state.libraryRows)
                var rowIndex = 0
                familyGroups.forEach { group ->
                    val familyId = group.familyId
                    val members = group.rows.map { it.exercise }
                    val exercise = members.first()
                    if (familyId == null) {
                        item(key = "exercise:${exercise.id}") {
                            ExerciseRow(exercise, rowIndex++, onClick = { onOpenExercise(exercise.id) })
                            LedgerDivider()
                        }
                    } else {
                        val familyLabel = MovementFamilies.label(familyId) ?: familyId
                        val matchesSearch = state.query.isNotBlank() && members.any {
                            it.name.contains(state.query, ignoreCase = true)
                        }
                        val expanded = matchesSearch || expandedFamilies[familyId] == true
                        item(key = "family:$familyId") {
                            ExpandableExerciseFamily(
                                familyLabel = familyLabel,
                                members = members,
                                expanded = expanded,
                                onToggle = { expandedFamilies[familyId] = !expanded },
                                onOpenExercise = onOpenExercise,
                                firstRowIndex = rowIndex,
                            )
                            rowIndex += members.size
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExerciseRow(
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
        LedgerIndex(index = index + 1)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                exercise.name.uppercase(Locale.US),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${exercise.movementType.name.uppercase(Locale.US)} · ${exercise.difficulty.name.uppercase(Locale.US)} · ${exercise.primaryMuscleGroupId.uppercase(Locale.US)}",
                style = MuscleTomeTextStyles.tag,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (exercise.isCustom) {
            MicroTag(text = "CUSTOM")
        } else {
            ExerciseSources.displayLabel(exercise.source)?.let { label ->
                MicroTag(text = label)
            }
        }
    }
}
