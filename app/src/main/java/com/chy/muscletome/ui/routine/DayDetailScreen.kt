package com.chy.muscletome.ui.routine

import androidx.compose.animation.AnimatedVisibility
import com.chy.muscletome.domain.search.ExerciseSearchEngine
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.domain.model.EffortScale
import com.chy.muscletome.domain.routine.TargetSlotLabel
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.BrutalistOutlinedButton
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField
import com.chy.muscletome.ui.components.dragReorderItem
import com.chy.muscletome.ui.components.rememberDragReorderState
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var replacingActiveSlotForRoutineExercise by remember { mutableStateOf<SlotRow?>(null) }

    var slotRows by remember { mutableStateOf(state.slots) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.slots) { if (!dragging) slotRows = state.slots }
    val slotListState = rememberLazyListState()

    var focusedSlotId by remember { mutableStateOf<String?>(null) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, focusedSlotId, slotRows) {
        if (!imeVisible) return@LaunchedEffect
        val id = focusedSlotId ?: return@LaunchedEffect
        val slotIndex = slotRows.indexOfFirst { it.slot.id == id }
        if (slotIndex >= 0) {
            slotListState.animateScrollToItem(slotIndex)
        }
    }
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
                snackbarHostState.showSnackbar(message.uppercase(Locale.US))
            }
        }
    }

    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) {
            focusManager.clearFocus()
            snackbarHostState.showSnackbar("CHANGES SAVED")
        }
    }

    Scaffold(
        topBar = {
             TopAppBar(
                title = { Text((state.day?.name ?: "DAY").uppercase(Locale.US), style = MuscleTomeTextStyles.heading) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.hasUnsavedChanges) {
                            showBackConfirm = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "BACK")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::startWorkout,
                        enabled = state.slots.isNotEmpty() && !state.hasUnsavedChanges,
                    ) {
                        Text(
                            "START",
                            style = MuscleTomeTextStyles.button,
                            color = if (state.slots.isNotEmpty() && !state.hasUnsavedChanges) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (!showBackConfirm) {
                        TextButton(
                            onClick = viewModel::saveChanges,
                            enabled = state.canSave,
                        ) {
                            Text("SAVE", style = MuscleTomeTextStyles.button)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSlot,
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.Add, contentDescription = "ADD EXERCISE")
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
                    title = "BLANK PAGE",
                    body = "ADD EXERCISES TO FILL THIS DAY OF THE TOME.",
                )
                AccentButton(
                    text = "ADD FIRST EXERCISE",
                    onClick = onAddSlot,
                )
            }
        } else {
            LazyColumn(
                state = slotListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = if (imeVisible) 280.dp else 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(slotRows, key = { _, row -> row.slot.id }) { index, row ->
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
                                    val title = (row.targetLabel ?: row.exerciseName).uppercase(Locale.US)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (row.slot.supersetGroupId != null &&
                                            row.slot.supersetGroupId != slotRows.getOrNull(index - 1)?.slot?.supersetGroupId
                                        ) {
                                            MicroTag(
                                                text = "SUPERSET",
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp),
                                            )
                                        }
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        if (row.targetLabel != null) {
                                            MicroTag(
                                                text = TargetSlotLabel.AI_PICK,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    }
                                    MonoText(
                                        text = "${row.draftSets} × " +
                                            "${row.draftRepMin}-${row.draftRepMax} · " +
                                            "${row.draftRestSeconds}S",
                                        style = MuscleTomeTextStyles.tag,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
                                            contentDescription = "GROUP WITH NEXT EXERCISE",
                                            tint = MaterialTheme.colorScheme.outline,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.LinkOff,
                                            contentDescription = "REMOVE FROM SUPERSET",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (state.activeSessionSlots.isNotEmpty()) {
                                    IconButton(onClick = { replacingActiveSlotForRoutineExercise = row }) {
                                        Icon(
                                            Icons.Default.SwapHoriz,
                                            contentDescription = "REPLACE IN ACTIVE WORKOUT",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                IconButton(onClick = { deletingSlot = row.slot }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "REMOVE SLOT",
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
                                    label = "SETS",
                                    value = row.draftSets,
                                    isValueExternal = !row.hasUnsavedChanges,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(row.slot.id, SlotField.SETS, it)
                                    },
                                    onFocused = { focused ->
                                        focusedSlotId = if (focused) row.slot.id else null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SlotMetricField(
                                    label = "MIN",
                                    value = row.draftRepMin,
                                    isValueExternal = !row.hasUnsavedChanges,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(row.slot.id, SlotField.REP_MIN, it)
                                    },
                                    onFocused = { focused ->
                                        focusedSlotId = if (focused) row.slot.id else null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SlotMetricField(
                                    label = "MAX",
                                    value = row.draftRepMax,
                                    isValueExternal = !row.hasUnsavedChanges,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(row.slot.id, SlotField.REP_MAX, it)
                                    },
                                    onFocused = { focused ->
                                        focusedSlotId = if (focused) row.slot.id else null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SlotMetricField(
                                    label = "REST",
                                    value = row.draftRestSeconds,
                                    isValueExternal = !row.hasUnsavedChanges,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(row.slot.id, SlotField.REST, it)
                                    },
                                    onFocused = { focused ->
                                        focusedSlotId = if (focused) row.slot.id else null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SlotMetricField(
                                    label = when (state.effortScale) {
                                        EffortScale.RPE -> "RPE"
                                        EffortScale.RIR -> "RIR"
                                    },
                                    value = row.draftTargetEffort,
                                    isValueExternal = !row.hasUnsavedChanges,
                                    onValueChange = {
                                        viewModel.onSlotFieldChange(
                                            row.slot.id,
                                            SlotField.TARGET_EFFORT,
                                            it,
                                        )
                                    },
                                    onFocused = { focused ->
                                        focusedSlotId = if (focused) row.slot.id else null
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
                                            text = "ENTER VALID NUMBERS",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                    TextButton(onClick = { viewModel.discardChanges(row.slot.id) }) {
                                        Text("DISCARD", style = MuscleTomeTextStyles.button)
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
            title = { Text("UNSAVED CHANGES", style = MuscleTomeTextStyles.heading) },
            text = { Text("UNSAVED EDITS TO THIS DAY. SAVE BEFORE LEAVING?", style = MuscleTomeTextStyles.systemMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showBackConfirm = false
                    viewModel.saveChanges()
                    onBack()
                }) {
                    Text("SAVE", style = MuscleTomeTextStyles.button)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackConfirm = false
                    onBack()
                }) {
                    Text("DISCARD AND LEAVE", style = MuscleTomeTextStyles.button)
                }
            },
        )
    }
    deletingSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { deletingSlot = null },
            title = { Text("REMOVE EXERCISE?", style = MuscleTomeTextStyles.heading) },
            text = {
                Text(
                    "REMOVE \"${(slot.exerciseId ?: "THIS SLOT").uppercase(Locale.US)}\" FROM THIS DAY? LOGGED WORKOUTS REMAIN.",
                    style = MuscleTomeTextStyles.systemMessage,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSlot(slot.id)
                        deletingSlot = null
                    },
                ) { Text("REMOVE", style = MuscleTomeTextStyles.button, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingSlot = null }) { Text("CANCEL", style = MuscleTomeTextStyles.button) }
            },
        )
    }

    replacingActiveSlotForRoutineExercise?.let { row ->
        var pickerSearchQuery by remember { mutableStateOf("") }
        var showLibraryPicker by remember { mutableStateOf(false) }

        if (!showLibraryPicker) {
            AlertDialog(
                onDismissRequest = { replacingActiveSlotForRoutineExercise = null },
                title = { Text("REPLACE IN ACTIVE WORKOUT", style = MuscleTomeTextStyles.heading) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "SWAP \"${row.exerciseName.uppercase(Locale.US)}\" INTO ACTIVE WORKOUT OR PICK NEW EXERCISE:",
                            style = MuscleTomeTextStyles.systemMessage,
                        )
                        BrutalistOutlinedButton(
                            text = "PICK LIBRARY EXERCISE INSTEAD...",
                            onClick = { showLibraryPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SectionHeader("REPLACE WHICH ACTIVE EXERCISE?")
                        LazyColumn(
                            modifier = Modifier.height(200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(state.activeSessionSlots) { option ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            replacingActiveSlotForRoutineExercise = null
                                            val targetExerciseId = row.slot.exerciseId
                                            if (targetExerciseId != null) {
                                                viewModel.overrideActiveWorkoutSlot(option.result, targetExerciseId)
                                                val oldName = option.currentExerciseName
                                                val newName = row.exerciseName
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("REPLACED \"${oldName.uppercase(Locale.US)}\" WITH \"${newName.uppercase(Locale.US)}\"")
                                                }
                                            } else {
                                                showLibraryPicker = true
                                            }
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        MonoText(
                                            text = (option.sortOrder + 1).toString().padStart(2, '0'),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = option.currentExerciseName.uppercase(Locale.US),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { replacingActiveSlotForRoutineExercise = null }) {
                        Text("CANCEL", style = MuscleTomeTextStyles.button)
                    }
                },
            )
        } else {
            val filteredCatalog = remember(pickerSearchQuery, state.catalogExercises) {
                ExerciseSearchEngine.filterAndRank(state.catalogExercises, pickerSearchQuery)
            }
            AlertDialog(
                onDismissRequest = {
                    showLibraryPicker = false
                    replacingActiveSlotForRoutineExercise = null
                },
                title = { Text("PICK REPLACEMENT EXERCISE", style = MuscleTomeTextStyles.heading) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectOnFocusOutlinedTextField(
                            value = pickerSearchQuery,
                            onValueChange = { pickerSearchQuery = it },
                            placeholder = { Text("SEARCH EXERCISES...", style = MuscleTomeTextStyles.tag) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        LazyColumn(
                            modifier = Modifier.height(280.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filteredCatalog) { catalogExercise ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val matchingResult = state.activeSessionSlots.find {
                                                it.result.routineSlotId == row.slot.id
                                            } ?: state.activeSessionSlots.firstOrNull()
                                            if (matchingResult != null) {
                                                viewModel.overrideActiveWorkoutSlot(matchingResult.result, catalogExercise.id)
                                                val oldName = matchingResult.currentExerciseName
                                                val newName = catalogExercise.name
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("REPLACED \"${oldName.uppercase(Locale.US)}\" WITH \"${newName.uppercase(Locale.US)}\"")
                                                }
                                            }
                                            showLibraryPicker = false
                                            replacingActiveSlotForRoutineExercise = null
                                        },
                                ) {
                                    Text(
                                        text = catalogExercise.name.uppercase(Locale.US),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        showLibraryPicker = false
                        replacingActiveSlotForRoutineExercise = null
                    }) {
                        Text("CANCEL", style = MuscleTomeTextStyles.button)
                    }
                },
            )
        }
    }
}

@Composable
internal fun SlotMetricField(
    label: String,
    value: String,
    isValueExternal: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {},
    allowDecimal: Boolean = false,
) {
    SelectOnFocusOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        onFocused = onFocused,
        applyValueChange = { old, typed -> applySlotFieldChange(old, typed, allowDecimal) },
        label = { Text(label, style = MuscleTomeTextStyles.label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}

private const val DECIMAL = "."

internal fun isValidSlotFieldText(text: String, allowDecimal: Boolean): Boolean =
    if (allowDecimal) {
        text.isEmpty() || text.matches(Regex("^\\d{1,2}(\\.\\d?)?$"))
    } else {
        text.isEmpty() || (text.all { it.isDigit() } && text.length <= 4)
    }

internal fun applySlotFieldChange(oldText: String, typedText: String, allowDecimal: Boolean): String {
    var text = typedText
    if (text.startsWith(DECIMAL)) {
        text = text.removePrefix(DECIMAL)
    }
    val firstPeriod = text.indexOf(DECIMAL)
    val secondPeriod = if (firstPeriod >= 0) {
        text.indexOf(DECIMAL, firstPeriod + 1)
    } else {
        -1
    }
    if (secondPeriod >= 0) {
        text = text.removeRange(secondPeriod, secondPeriod + 1)
    }
    return if (isValidSlotFieldText(text, allowDecimal)) text else oldText
}
