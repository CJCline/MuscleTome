package com.chy.muscletome.ui.settings

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.ui.components.SectionHeader
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val user = state.user

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("Units")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = user?.weightUnit == unit,
                        onClick = { viewModel.setUnit(unit) },
                        label = { Text(unit.name) },
                    )
                }
            }

            SectionHeader("Exercise catalog")
            val importState by viewModel.importProgress.collectAsStateWithLifecycle()
            Button(
                onClick = viewModel::importFromWger,
                enabled = !importState.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (importState.running) "Importing…" else "Import from wger (English)")
            }
            if (importState.running || importState.fraction > 0f) {
                LinearProgressIndicator(
                    progress = { importState.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (importState.message.isNotBlank()) {
                Text(importState.message)
            }
            if (importState.error != null) {
                Text("Error: ${importState.error}")
            }
            Text(
                "English translations only. Main demo image URL is stored when wger provides one. " +
                    "Text is typically CC-BY-SA; attribution is saved on each exercise.",
            )

            SectionHeader("Available equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.equipment.forEach { item ->
                    FilterChip(
                        selected = item.id in state.availableEquipmentIds,
                        onClick = { viewModel.toggleEquipment(item.id) },
                        label = { Text(item.name) },
                    )
                }
            }

            SectionHeader("Primary match")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MatchStrictness.entries.forEach { value ->
                    FilterChip(
                        selected = user?.primaryMatchStrictness == value,
                        onClick = { viewModel.setStrictness(value) },
                        label = { Text(value.name) },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Prefer compound early")
                Switch(
                    checked = user?.preferCompoundEarly ?: true,
                    onCheckedChange = viewModel::setPreferCompoundEarly,
                )
            }

            SectionHeader("Excluded exercises")
            val excludeQuery by viewModel.excludeSearchQuery.collectAsStateWithLifecycle()
            val excludeResults by viewModel.excludeSearchResults.collectAsStateWithLifecycle()

            if (state.excludedExercises.isEmpty()) {
                Text(
                    "Nothing excluded. Search below to add exercises the variety engine should never pick.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.excludedExercises.forEach { exercise ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.toggleExcluded(exercise.id) },
                            label = { Text(exercise.name) },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove exclusion")
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = excludeQuery,
                onValueChange = viewModel::onExcludeSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search exercises to exclude") },
            )
            excludeResults.forEach { exercise ->
                ListItem(
                    modifier = Modifier.fillMaxWidth(),
                    headlineContent = { Text(exercise.name) },
                    supportingContent = {
                        Text(
                            "${exercise.movementType} · ${exercise.difficulty} · ${exercise.primaryMuscleGroupId}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.toggleExcluded(exercise.id) }) {
                            Icon(Icons.Default.Add, contentDescription = "Exclude ${exercise.name}")
                        }
                    },
                )
            }
            if (excludeQuery.isNotBlank() && excludeResults.isEmpty()) {
                Text(
                    "No matching exercises (already-excluded ones are hidden).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}