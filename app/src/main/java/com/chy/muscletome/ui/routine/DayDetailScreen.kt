package com.chy.muscletome.ui.routine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.dragReorderItem
import com.chy.muscletome.ui.components.rememberDragReorderState
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.domain.model.EffortScale

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
    var showBackConfirm by rememberSaveable { mutableStateOf(value = false) }
    var deletingSlot by remember { mutableStateOf<RoutineSlotEntity?>(null) }

    // Drag-reorder: hold a local order while dragging, persist on drop.
    // Rows expose slot ids for the reorder call; drafts ride along untouched.
    var slotRows by remember { mutableStateOf(state.slots) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.slots) { if (!dragging) slotRows = state.slots }
    val slotListState = rememberLazyListState()
    val dragState = rememberDragReorderState(
        listState = slotListState,
        onMove = { from, to ->
            dragging = true
            slotRows = slotRows.toMutableList().apply { add(to, removeAt(from)) }
        },
        onDrop = {
            dragging = false
            viewModel.reorderSlots(slotRows.map { it.slot.id })
        },
    )

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
                    IconButton(onClick = {
                        if (state.hasUnsavedChanges) {
                            showBackConfirm = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::startWorkout,
                        enabled = state.slots.isNotEmpty() && !state.hasUnsavedChanges,
                    ) {
                        Text(
                            "Start".uppercase(),
                            color = if (state.slots.isNotEmpty() && !state.hasUnsavedChanges) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    // Always visible so it is discoverable; disabled when there is
                    // nothing to save, and hidden while a Back-confirm dialog is up.
                    if (!showBackConfirm) {
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
            FloatingActionButton(
                onClick = onAddSlot,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add exercise")
            }
        },
    ) { innerPadding ->
        if (state.slots.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EmptyState(
                    title = "Blank page",
                    body = "Add exercises to fill this day of the tome.",
                )
                Button(
                    onClick = onAddSlot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text("Add first exercise".uppercase()) }
            }
        } else {
            LazyColumn(
                state = slotListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(slotRows, key = { _, row -> row.slot.id }) { index, row ->
                    // Amber spine while the row has unsaved edits — dirty pages glow.
                    val spineColor = if (row.hasUnsavedChanges) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .dragReorderItem(dragState, row.slot.id)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(IntrinsicSize.Max)
                                .background(spineColor),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MonoText(
                                    text = (index + 1).toString().padStart(2, '0'),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    val title = if (row.slot.type.name == "TARGET") {
                                        "Target slot"
                                    } else {
                                        row.exerciseName
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (row.slot.supersetGroupId != null &&
                                            row.slot.supersetGroupId != slotRows.getOrNull(index - 1)?.slot?.supersetGroupId
                                        ) {
                                            MicroTag(
                                                text = "Superset",
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp),
                                            )
                                        }
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                    MonoText(
                                        text = "${row.draftSets} × " +
                                            "${row.draftRepMin}-${row.draftRepMax} · " +
                                            "${row.draftRestSeconds}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // Superset membership follows the group id, never
                                // adjacency — the icon shows the slot's state;
                                // drag-reordering a group's rows apart keeps it.
                                IconButton(
                                    onClick = {
                                        if (row.slot.supersetGroupId == null) {
                                            viewModel.groupSlotWithNext(row.slot.id)
                                        } else {
                                            viewModel.ungroupSlot(row.slot.id)
                                        }
                                    },
                                ) {
                                    if (row.slot.supersetGroupId == null) {
                                        Icon(
                                            Icons.Default.Link,
                                            contentDescription = "Group with next exercise",
                                            tint = MaterialTheme.colorScheme.outline,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.LinkOff,
                                            contentDescription = "Remove from superset",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                IconButton(onClick = { deletingSlot = row.slot }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove slot",
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SlotMetricField(
                                    label = "Sets",
                                    value = row.draftSets,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(row.slot.id, SlotField.SETS, it)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SlotMetricField(
                                    label = "Rep min",
                                    value = row.draftRepMin,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(row.slot.id, SlotField.REP_MIN, it)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SlotMetricField(
                                    label = "Rep max",
                                    value = row.draftRepMax,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(row.slot.id, SlotField.REP_MAX, it)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SlotMetricField(
                                    label = "Rest (s)",
                                    value = row.draftRestSeconds,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(row.slot.id, SlotField.REST, it)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SlotMetricField(
                                    label = when (state.effortScale) {
                                        EffortScale.RPE -> "Target RPE"
                                        EffortScale.RIR -> "Target RIR"
                                    },
                                    value = row.draftTargetEffort,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(
                                            row.slot.id,
                                            SlotField.TARGET_EFFORT,
                                            it,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    allowDecimal = state.effortScale == EffortScale.RPE,
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
                                        MicroTag(
                                            text = "Enter valid numbers",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
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
    }

    if (showBackConfirm) {
        AlertDialog(
            onDismissRequest = { showBackConfirm = false },
            title = { Text("Unsaved changes") },
            text = { Text("You have unsaved edits to this day. Save them before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showBackConfirm = false
                    viewModel.saveChanges()
                    onBack()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackConfirm = false
                    onBack()
                }) {
                    Text("Discard and leave")
                }
            },
        )
    }
    deletingSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { deletingSlot = null },
            title = { Text("Remove exercise?") },
            text = {
                Text(
                    "Remove \"${slot.exerciseId ?: "this slot"}\" from this day? " +
                        "Your logged workouts are kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSlot(slot.id)
                        deletingSlot = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deletingSlot = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SlotMetricField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** RPE allows half-points (7.5); whole numbers otherwise. */
    allowDecimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val ok = if (allowDecimal) {
                newValue.isEmpty() || newValue.matches(Regex("^\\d{1,2}(\\.\\d?)?$"))
            } else {
                newValue.isEmpty() || newValue.all { it.isDigit() }
            }
            if (ok) onValueChange(newValue)
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
    )
}
