package com.chy.muscletome.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.model.DefaultRepPreference
import com.chy.muscletome.domain.model.EffortScale
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.WeightStep
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.BrutalistOutlinedButton
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val user = state.user
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackup) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.uppercase(Locale.US)) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SETTINGS", style = MuscleTomeTextStyles.heading) }) },
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
            SectionHeader("UNITS")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = user?.weightUnit == unit,
                        onClick = { viewModel.setUnit(unit) },
                        label = { Text(unit.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }
            Text(
                "SWITCHING CONVERTS ALL LOGGED SETS TO THE NEW UNIT.",
                style = MuscleTomeTextStyles.systemMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("EFFORT SCALE")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffortScale.entries.forEach { scale ->
                    FilterChip(
                        selected = user?.effortScale == scale,
                        onClick = { viewModel.setEffortScale(scale) },
                        label = {
                            Text(
                                when (scale) {
                                    EffortScale.RPE -> "RPE (1–10)"
                                    EffortScale.RIR -> "RIR (0–4)"
                                },
                                style = MuscleTomeTextStyles.tag,
                            )
                        },
                    )
                }
            }

            SectionHeader("DEFAULT REPS")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultRepPreference.entries.forEach { pref ->
                    FilterChip(
                        selected = user?.defaultRepPreference == pref,
                        onClick = { viewModel.setDefaultRepPreference(pref) },
                        label = {
                            Text(
                                when (pref) {
                                    DefaultRepPreference.MINIMUM -> "MINIMUM"
                                    DefaultRepPreference.MAXIMUM -> "MAXIMUM"
                                },
                                style = MuscleTomeTextStyles.tag,
                            )
                        },
                    )
                }
            }

            SectionHeader("WEIGHT STEP")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightStep.entries.forEach { step ->
                    FilterChip(
                        selected = user?.weightStep == step,
                        onClick = { viewModel.setWeightStep(step) },
                        label = { Text(step.label(user?.weightUnit ?: WeightUnit.LB).uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            SectionHeader("EXERCISE CATALOG")
            val importState by viewModel.importProgress.collectAsStateWithLifecycle()
            AccentButton(
                text = if (importState.running) "IMPORTING..." else "IMPORT FROM WGER (ENGLISH)",
                onClick = viewModel::importFromWger,
                enabled = !importState.running,
            )
            if (importState.running || importState.fraction > 0f) {
                LinearProgressIndicator(
                    progress = { importState.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (importState.message.isNotBlank()) {
                Text(importState.message.uppercase(Locale.US), style = MuscleTomeTextStyles.systemMessage)
            }

            SectionHeader("EXERCISE IMAGES")
            val mediaCache by viewModel.mediaCacheState.collectAsStateWithLifecycle()
            Text(
                "CACHED IMAGES: ${mediaCache.cacheBytes / (1024 * 1024)} MB",
                style = MuscleTomeTextStyles.systemMessage,
            )
            if (state.families.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.families.forEach { family ->
                        FilterChip(
                            selected = false,
                            enabled = !mediaCache.downloading,
                            onClick = { viewModel.downloadFamilyMedia(family.id) },
                            label = { Text("DOWNLOAD: ${family.displayName.uppercase(Locale.US)}", style = MuscleTomeTextStyles.tag) },
                        )
                    }
                }
            }
            if (mediaCache.downloading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            BrutalistOutlinedButton(
                text = "CLEAR CACHED IMAGES",
                onClick = viewModel::clearMediaCache,
                enabled = !mediaCache.downloading && mediaCache.cacheBytes > 0,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("BACKUP & RESTORE")
            AccentButton(
                text = if (backupState.running) "WORKING..." else "EXPORT BACKUP",
                onClick = { exportPicker.launch("muscletome-backup.json") },
                enabled = !backupState.running,
            )
            BrutalistOutlinedButton(
                text = "IMPORT BACKUP",
                onClick = { importPicker.launch(arrayOf("application/json")) },
                enabled = !backupState.running,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("AVAILABLE EQUIPMENT")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.equipment.forEach { item ->
                    FilterChip(
                        selected = item.id in state.availableEquipmentIds,
                        onClick = { viewModel.toggleEquipment(item.id) },
                        label = { Text(item.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            SectionHeader("PRIMARY MATCH")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MatchStrictness.entries.forEach { value ->
                    FilterChip(
                        selected = user?.primaryMatchStrictness == value,
                        onClick = { viewModel.setStrictness(value) },
                        label = { Text(value.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PREFER COMPOUND EARLY", style = MuscleTomeTextStyles.label)
                Switch(
                    checked = user?.preferCompoundEarly ?: true,
                    onCheckedChange = viewModel::setPreferCompoundEarly,
                )
            }

            SectionHeader("EXCLUDED EXERCISES")
            val excludeQuery by viewModel.excludeSearchQuery.collectAsStateWithLifecycle()
            val excludeResults by viewModel.excludeSearchResults.collectAsStateWithLifecycle()

            if (state.excludedExercises.isEmpty()) {
                Text(
                    "NO EXCLUDED EXERCISES",
                    style = MuscleTomeTextStyles.systemMessage,
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
                            label = { Text(exercise.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "REMOVE EXCLUSION")
                            },
                        )
                    }
                }
            }

            SelectOnFocusOutlinedTextField(
                value = excludeQuery,
                onValueChange = viewModel::onExcludeSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("SEARCH EXERCISES TO EXCLUDE", style = MuscleTomeTextStyles.label) },
            )
            excludeResults.forEach { exercise ->
                ListItem(
                    modifier = Modifier.fillMaxWidth(),
                    headlineContent = { Text(exercise.name.uppercase(Locale.US), style = MuscleTomeTextStyles.button) },
                    supportingContent = {
                        Text(
                            "${exercise.movementType.name.uppercase(Locale.US)} · ${exercise.difficulty.name.uppercase(Locale.US)} · ${exercise.primaryMuscleGroupId.uppercase(Locale.US)}",
                            style = MuscleTomeTextStyles.tag,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.toggleExcluded(exercise.id) }) {
                            Icon(Icons.Default.Add, contentDescription = "EXCLUDE ${exercise.name.uppercase(Locale.US)}")
                        }
                    },
                )
            }
        }
    }
}
