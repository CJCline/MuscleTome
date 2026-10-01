package com.chy.muscletome.ui.routine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
import com.chy.muscletome.domain.routine.TargetSlotLabel

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

    // Drag-reorder: hold a local order while dragging, persist on drop.
    // Rows expose slot ids for the reorder call; drafts ride along untouched.
    var slotRows by remember { mutableStateOf(state.slots) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.slots) { if (!dragging) slotRows = state.slots }
    val slotListState = rememberLazyListState()
    // Editing near the bottom of a long routine: with the keyboard open, the
    // focused field must stay visible. The list below applies ime + nav-bar
    // insets, so it shrinks; the IME's own bring-into-view only guarantees
    // the cursor, not the rest of the row — and the FAB floats above the
    // keyboard covering the last row. When a field gains focus (or the IME
    // lands after its animation) and the focused row is clipped, scroll it
    // into view. Keys: focus id, IME visibility, and row order — whichever
    // changes re-evaluates the visibility.
    var focusedSlotId by remember { mutableStateOf<String?>(null) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, focusedSlotId, slotRows) {
        if (imeVisible.not()) return@LaunchedEffect
        val slotIndex = focusedSlotId?.let { id ->
            slotRows.indexOfFirst { it.slot.id == id }
        } ?: return@LaunchedEffect
        if (slotIndex < 0) return@LaunchedEffect
        val item = slotListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == focusedSlotId } ?: return@LaunchedEffect
        val isClipped = item.offset < 0 ||
            (item.offset + item.size) > slotListState.layoutInfo.viewportEndOffset
        if (isClipped) {
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
                modifier = Modifier
                    // The FAB otherwise covers the bottom row exactly where
                    // the user is editing; riding above the keyboard keeps
                    // the last exercises reachable while typing.
                    .imePadding()
                    .navigationBarsPadding(),
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
                    .padding(innerPadding)
                    // Under the keyboard the list itself shrinks and scrolls;
                    // the nav-bar inset keeps the last exercise reachable
                    // when the keyboard is closed.
                    .imePadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    // Room for the IME-aware FAB below the last row.
                    bottom = 96.dp,
                ),
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
                                    // TARGET slots speak for themselves:
                                    // "Chest isolation · AI pick" instead of a
                                    // generic "Target slot".
                                    val title = row.targetLabel ?: row.exerciseName
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
                                    label = "Rep min",
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
                                    label = "Rep max",
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
                                    label = "Rest (s)",
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
                                        EffortScale.RPE -> "Target RPE"
                                        EffortScale.RIR -> "Target RIR"
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

/**
 * One editable metric of a slot (sets, rep range, rest, target effort).
 *
 * The field owns its text state: the ViewModel's draft map follows the
 * user's keystrokes through [onValueChange], but the field only re-applies
 * the model's value when the row reports it as external (no unsaved draft)
 * AND the text actually differs — save, discard, and routine reloads.
 * Recomposition can no longer overwrite an in-progress edit with stale
 * state (the draft map re-sends the row on every keystroke).
 *
 * Focus selects the whole value so typing replaces it; partial edits
 * (cursor mid-text, backspace from the end) keep normal caret placement.
 */
@Composable
internal fun SlotMetricField(
    label: String,
    value: String,
    /** True while the row has no unsaved draft: the model owns the value. */
    isValueExternal: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Reports focus changes so the row can be scrolled clear of the IME. */
    onFocused: (Boolean) -> Unit = {},
    /** RPE allows half-points (7.5); whole numbers otherwise. */
    allowDecimal: Boolean = false,
) {
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var isFocused by remember { mutableStateOf(false) }
    var selectAllOnFocus by remember { mutableStateOf(false) }

    LaunchedEffect(value, isValueExternal, isFocused) {
        if (!isFocused && (isValueExternal || fieldValue.text != value)) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { typed ->
            val old = fieldValue.text
            if (selectAllOnFocus) {
                selectAllOnFocus = false
                if (typed.text == old) {
                    fieldValue = typed.copy(selection = TextRange(0, old.length))
                    return@OutlinedTextField
                }
            }
            val text = applySlotFieldChange(old, typed.text, allowDecimal)
            fieldValue = typed.copy(text = text).normalizeSelection(text)
            if (text != old) onValueChange(text)
        },
        modifier = modifier.onFocusChanged { focusState ->
            val gainedFocus = focusState.isFocused && !isFocused
            isFocused = focusState.isFocused
            if (focusState.isFocused) {
                onFocused(true)
                if (gainedFocus) {
                    selectAllOnFocus = true
                    fieldValue = fieldValue.copy(
                        selection = TextRange(0, fieldValue.text.length),
                    )
                }
            } else {
                onFocused(false)
                selectAllOnFocus = false
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}

/** The characters a numeric slot field accepts while the user types. */
private const val DECIMAL = "."

/**
 * Whether [text] is a legal value for a slot metric field: whole-number
 * fields accept up to 4 digits (a rest of 9999 s is conceivable), decimal
 * fields accept 0-2 integer digits with an optional decimal point and at
 * most one fractional digit (RPE halves). Empty is always valid — clearing
 * is part of editing.
 */
internal fun isValidSlotFieldText(text: String, allowDecimal: Boolean): Boolean =
    if (allowDecimal) {
        text.isEmpty() || text.matches(Regex("^\\d{1,2}(\\.\\d?)?$"))
    } else {
        text.isEmpty() || (text.all { it.isDigit() } && text.length <= 4)
    }

/**
 * Applies an IME text change to a metric field. When the typed text would
 * be invalid (letter keys, too many digits, a period that starts the
 * number, or a second period) the previous text is kept instead.
 */
internal fun applySlotFieldChange(oldText: String, typedText: String, allowDecimal: Boolean): String {
    var text = typedText
    // A period cannot start the number (RPE starts at 5) and a second
    // period must be ignored — the IME cannot enforce either itself.
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

/** Keeps the caret inside clamped text (deletions shift it inward). */
private fun TextFieldValue.normalizeSelection(text: String): TextFieldValue =
    if (selection.end > text.length || selection.start > text.length) {
        copy(selection = TextRange(text.length))
    } else {
        this
    }
