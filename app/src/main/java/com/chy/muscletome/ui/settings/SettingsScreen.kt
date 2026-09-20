package com.chy.muscletome.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
            Text("Units")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = user?.weightUnit == unit,
                        onClick = { viewModel.setUnit(unit) },
                        label = { Text(unit.name) },
                    )
                }
            }

            Text("Exercise catalog")
            val importState by viewModel.importProgress.collectAsStateWithLifecycle()
            Button(
                onClick = viewModel::importFromWger,
                enabled = !importState.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (importState.running) "Importing…" else "Import from wger")
            }
            if (importState.message.isNotBlank()) {
                Text(importState.message)
            }
            if (importState.error != null) {
                Text("Error: ${importState.error}")
            }
            Text("Uses wger.de public API. Exercise text is typically CC-BY-SA; attribution is stored on each exercise.")

            Text("Available equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.equipment.forEach { item ->
                    FilterChip(
                        selected = item.id in state.availableEquipmentIds,
                        onClick = { viewModel.toggleEquipment(item.id) },
                        label = { Text(item.name) },
                    )
                }
            }

            Text("Primary match")
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

            Text("Excluded exercises")
            state.exercises.forEach { exercise ->
                FilterChip(
                    selected = exercise.id in state.excludedExerciseIds,
                    onClick = { viewModel.toggleExcluded(exercise.id) },
                    label = { Text(exercise.name) },
                )
            }
        }
    }
}