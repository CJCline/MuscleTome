package com.chy.muscletome.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.model.DefaultRepPreference
import com.chy.muscletome.domain.model.EffortScale
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.ui.components.SectionHeader
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val user = state.user
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()

    // SAF: the user picks where the backup lands (Documents, Drive, whatever) —
    // no storage permission needed.
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackup) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
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
                        label = { Text(unit.name.lowercase()) },
                    )
                }
            }
            Text(
                "Switching converts every logged set to the new unit, so " +
                    "history, PRs and volume stay true.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("Effort scale")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffortScale.entries.forEach { scale ->
                    FilterChip(
                        selected = user?.effortScale == scale,
                        onClick = { viewModel.setEffortScale(scale) },
                        label = {
                            Text(
                                when (scale) {
                                    EffortScale.RPE -> "RPE — 1–10, 10 = max"
                                    EffortScale.RIR -> "RIR — 0–4, 0 = max"
                                },
                            )
                        },
                    )
                }
            }
            Text(
                "Sets store RPE either way; RIR is shown alongside (RIR = 10 − RPE).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("Default reps")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultRepPreference.entries.forEach { pref ->
                    FilterChip(
                        selected = user?.defaultRepPreference == pref,
                        onClick = { viewModel.setDefaultRepPreference(pref) },
                        label = {
                            Text(
                                when (pref) {
                                    DefaultRepPreference.MINIMUM -> "Minimum"
                                    DefaultRepPreference.MAXIMUM -> "Maximum"
                                },
                            )
                        },
                    )
                }
            }
            Text(
                "Sets default reps to the target range's minimum or maximum when starting an exercise.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                style = MaterialTheme.typography.bodySmall,
            )

            SectionHeader("Exercise images")
            val mediaCache by viewModel.mediaCacheState.collectAsStateWithLifecycle()
            Text(
                "Imported exercise images are shown from the network by default. " +
                    "Download a family's images to view them offline. Only images with a " +
                    "license that permits redistribution are cached; the cache is capped " +
                    "at 256 MB and evicted oldest-first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Cached: ${mediaCache.cacheBytes / (1024 * 1024)} MB",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.families.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.families.forEach { family ->
                        FilterChip(
                            selected = false,
                            enabled = !mediaCache.downloading,
                            onClick = { viewModel.downloadFamilyMedia(family.id) },
                            label = { Text("Download: ${family.displayName}") },
                        )
                    }
                }
            }
            if (mediaCache.downloading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(
                onClick = viewModel::clearMediaCache,
                enabled = !mediaCache.downloading && mediaCache.cacheBytes > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear cached images")
            }

            SectionHeader("Backup & restore")
            Text(
                "Everything lives on this device. Export a JSON backup to move devices or keep " +
                    "a copy — import merges it back (routines, logs, catalog edits).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    exportPicker.launch("muscletome-backup.json")
                },
                enabled = !backupState.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (backupState.running) "Working…" else "Export backup")
            }
            OutlinedButton(
                onClick = { importPicker.launch(arrayOf("application/json")) },
                enabled = !backupState.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import backup")
            }

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

            com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
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