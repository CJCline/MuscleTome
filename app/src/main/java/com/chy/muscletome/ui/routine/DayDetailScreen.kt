package com.chy.muscletome.ui.routine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    onBack: () -> Unit,
    onAddSlot: () -> Unit,
    onStartWorkout: (String) -> Unit,
    viewModel: DayDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel) {
        viewModel.startSessionId.collect { sessionId ->
            onStartWorkout(sessionId)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.errorMessage.collect { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) {
            focusManager.clearFocus()
            snackbarHostState.showSnackbar("Changes saved")
        }
    }

    Scaffold(
        topBar = {
             TopAppBar(
                title = { Text(state.day?.name ?: "Day") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::startWorkout,
                        enabled = state.slots.isNotEmpty() && !state.hasUnsavedChanges,
                    ) {
                        Text("Start")
                    }
                    if (state.hasUnsavedChanges) {
                        TextButton(
                            onClick = viewModel::saveChanges,
                            enabled = state.canSave,
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSlot) {
                Icon(Icons.Default.Add, contentDescription = "Add exercise")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.slots, key = { it.slot.id }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = {
                            val title = if (row.slot.type.name == "TARGET") {
                                "Target slot"
                            } else {
                                row.exerciseName
                            }
                            Text(title)
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteSlot(row.slot.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove slot")
                            }
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SlotMetricField(
                            label = "Sets",
                            value = row.draftSets,
                            onValueChange = { viewModel.onSlotFieldChange(row.slot.id, SlotField.SETS, it) },
                            modifier = Modifier.weight(1f),
                        )
                        SlotMetricField(
                            label = "Rep min",
                            value = row.draftRepMin,
                            onValueChange = { viewModel.onSlotFieldChange(row.slot.id, SlotField.REP_MIN, it) },
                            modifier = Modifier.weight(1f),
                        )
                        SlotMetricField(
                            label = "Rep max",
                            value = row.draftRepMax,
                            onValueChange = { viewModel.onSlotFieldChange(row.slot.id, SlotField.REP_MAX, it) },
                            modifier = Modifier.weight(1f),
                        )
                        SlotMetricField(
                            label = "Rest (s)",
                            value = row.draftRestSeconds,
                            onValueChange = { viewModel.onSlotFieldChange(row.slot.id, SlotField.REST, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AnimatedVisibility(visible = row.hasUnsavedChanges) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!row.isDraftValid) {
                                Text(
                                    "Enter valid numbers",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            TextButton(onClick = { viewModel.discardChanges(row.slot.id) }) {
                                Text("Discard")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotMetricField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                onValueChange(newValue)
            }
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
    )
}
