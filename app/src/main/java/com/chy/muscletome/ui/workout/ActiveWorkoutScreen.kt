package com.chy.muscletome.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.domain.model.EffortScale
import com.chy.muscletome.domain.session.EffortScales
import com.chy.muscletome.domain.session.WeightUnits
import com.chy.muscletome.ui.components.ExerciseDemoImage
import com.chy.muscletome.ui.components.MetricStepper
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.PlateRing
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.SetTicks
import com.chy.muscletome.ui.components.dragReorderItem
import com.chy.muscletome.ui.components.rememberDragReorderState
import com.chy.muscletome.ui.library.ExerciseInfoContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exerciseInfo by viewModel.exerciseInfo.collectAsStateWithLifecycle()
    val current = state.current
    var showSwap by remember { mutableStateOf(false) }
    var showSuperset by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showPlates by remember { mutableStateOf(false) }
    var showSetDetails by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    var showFinishConfirm by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetLogEntity?>(null) }

    // The rest-timer notification needs POST_NOTIFICATIONS on Android 13+.
    // The countdown alarm + beep work regardless; ask once up front.
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    // A workout stays on its route when system back is pressed. The explicit
    // toolbar back remains available for intentionally leaving the session open.
    BackHandler(enabled = !state.finished) { }

    val lastPerformance = state.lastSessions.firstOrNull()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            current?.exercise?.name ?: "Workout",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    },
                    navigationIcon = {
                        // Explicit toolbar back leaves this session open.
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showInfo = true },
                            enabled = current != null,
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = "Exercise info")
                        }
                    },
                )
                if (current != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "LAST PERFORMANCE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                lastPerformance?.let {
                                    "${WeightUnits.displayText(it.topWeight)}${state.weightUnitSuffix} · " +
                                        "${it.setCount} sets · e1RM ${it.bestE1rm.toInt()}"
                                } ?: "No previous workout",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Header zone: exercise position, reason, set ticks ---------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MicroTag(
                            text = "Exercise ${state.currentIndex + 1} / " +
                                state.slots.size.coerceAtLeast(1).toString() +
                                if (state.slots.size > 1) " ▾" else "",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = if (state.slots.size > 1) {
                                Modifier.clickable { showExercisePicker = true }
                            } else Modifier,
                        )
                        MicroTag(
                            text = viewModel.reasonLabel(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val planned = state.plannedSets
                    val done = current?.sets?.size ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (planned > 0) {
                                "${state.currentSetNumber.coerceAtMost(planned)} / $planned"
                            } else {
                                state.currentSetNumber.toString()
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        SetTicks(
                            done = done,
                            total = if (planned > 0) planned else done.coerceAtLeast(1),
                        )
                    }

                    if (current?.slot != null) {
                        MicroTag(
                            text = "Target ${current.slot.repRangeMin}–${current.slot.repRangeMax} " +
                                "reps · rest ${current.slot.restSeconds}s" +
                                (state.targetEffortLabel?.let { " · $it" } ?: ""),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.slots.size > 1) {
                            OutlinedButton(onClick = { showExercisePicker = true }) {
                                Text("Exercises")
                            }
                        }
                        if (viewModel.canReroll()) {
                            OutlinedButton(onClick = viewModel::reroll) {
                                Text("Reroll")
                            }
                        }
                        if (current != null && current.sets.isEmpty()) {
                            OutlinedButton(onClick = { showSwap = true }) {
                                Text("Swap")
                            }
                        }
                        if (viewModel.canSuperset()) {
                            OutlinedButton(onClick = { showSuperset = true }) {
                                Text("Superset")
                            }
                        }
                    }
                }
            }

            // --- Up Next Exercise Preview ----------------------------------
            item {
                val nextSlot = state.nextSlot
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (nextSlot != null) {
                                Modifier.clickable { viewModel.selectExercise(nextSlot.result.id) }
                            } else Modifier,
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (nextSlot != null) {
                            val nextExercise = nextSlot.exercise
                            if (nextExercise != null && !nextExercise.demoUri.isNullOrBlank()) {
                                ExerciseDemoImage(
                                    uri = nextExercise.demoUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.FitnessCenter,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                MicroTag(
                                    text = "UP NEXT IN ROUTINE (TAP TO SWITCH)",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = nextSlot.exercise?.name ?: "Next exercise",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val planned = nextSlot.plannedSets
                                val minReps = nextSlot.slot?.repRangeMin ?: 0
                                val maxReps = nextSlot.slot?.repRangeMax ?: 0
                                val repsDetail = if (minReps > 0 && maxReps > 0) " · $minReps–$maxReps reps" else ""
                                val setsDetail = if (planned > 0) "$planned planned sets$repsDetail" else ""
                                if (setsDetail.isNotBlank()) {
                                    Text(
                                        text = setsDetail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                MicroTag(
                                    text = "UP NEXT",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Final exercise in current routine",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // --- Superset banner: the whole group at a glance --------------
            val group = state.currentGroup
            if (group.size > 1) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MicroTag(
                                    text = "Superset",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                MicroTag(
                                    text = "No rest between exercises",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            group.forEach { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectExercise(member.result.id) },
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SetTicks(
                                        done = member.sets.size,
                                        total = member.plannedSets.coerceAtLeast(1),
                                        modifier = Modifier.width(64.dp),
                                    )
                                    Text(
                                        member.exercise?.name ?: "Exercise",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (member.result.id == current?.result?.id) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = if (member.result.id == current?.result?.id) {
                                            FontWeight.ExtraBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (member.isComplete) {
                                        MicroTag(
                                            text = "Done",
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (current != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricStepper(
                            label = "Weight",
                            value = state.weight,
                            onValueChange = viewModel::onWeightChange,
                            onDelta = viewModel::bumpWeight,
                            deltaStep = state.weightStep,
                            longDeltaStep = state.weightLongStep,
                            suffix = state.weightUnitSuffix,
                            large = true,
                        )
                        MetricStepper(
                            label = "Reps",
                            value = state.reps,
                            onValueChange = viewModel::onRepsChange,
                            onDelta = { viewModel.bumpReps(it.toInt()) },
                            deltaStep = 1.0,
                            large = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            OutlinedButton(onClick = { showPlates = true }) { Text("Plates") }
                            OutlinedButton(onClick = viewModel::toggleUnit) {
                                Text("Switch to ${state.otherUnit.name.lowercase()}")
                            }
                            TextButton(onClick = { showSetDetails = true }) {
                                Text("RPE & notes")
                            }
                        }
                    }
                }
            }

            // --- Rest banner: plate-ring countdown -------------------------
            if (state.restSecondsLeft > 0) {
                item {
                    val plannedRest = if (state.totalRestSeconds > 0) {
                        state.totalRestSeconds
                    } else {
                        current?.slot?.restSeconds ?: 0
                    }
                    val fraction = if (plannedRest > 0) {
                        (state.restSecondsLeft.toFloat() / plannedRest).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlateRing(
                                progress = fraction,
                                diameter = 72.dp,
                                strokeWidth = 8f,
                            ) {
                                MonoText(
                                    text = state.restSecondsLeft.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SectionHeader("Rest")
                                    TextButton(
                                        onClick = viewModel::skipRest,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Text("Skip")
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.addRest(-30) },
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                    ) {
                                        Text("-30s")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.addRest(-10) },
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                    ) {
                                        Text("-10s")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.addRest(10) },
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                    ) {
                                        Text("+10s")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.addRest(30) },
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                    ) {
                                        Text("+30s")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Primary action: LOG SET -----------------------------------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.logSet()
                        },
                        enabled = current != null && !state.finished,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            "Log set".uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    if (state.isCurrentComplete && !state.isLastExercise) {
                        val nextName = state.nextSlot?.exercise?.name
                        val buttonText = if (!nextName.isNullOrBlank()) {
                            "Next exercise: $nextName".uppercase()
                        } else {
                            "Next exercise".uppercase()
                        }
                        OutlinedButton(
                            onClick = viewModel::nextExercise,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) { Text(buttonText) }
                    }

                    if (state.isCurrentComplete && state.isLastExercise) {
                        Button(
                            onClick = { showFinishConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) { Text("Finish workout".uppercase()) }
                    }
                }
            }

            // --- Sets logged this exercise ---------------------------------
            if ((current?.sets?.size ?: 0) > 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader("Logged sets")
                        TextButton(onClick = viewModel::undoLastSet) {
                            Text("Undo last")
                        }
                    }
                }
                items(current?.sets ?: emptyList(), key = { it.id }) { set ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingSet = set },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonoText(
                            text = "SET ${set.setNumber.toString().padStart(2, '0')}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonoText(
                                text = "${WeightUnits.displayText(set.weight)}${state.weightUnitSuffix} × ${set.reps}",
                            )
                            if (set.id in state.prSetIds) {
                                MicroTag("PR", color = MaterialTheme.colorScheme.primary)
                            }
                            set.rpe?.let { rpe ->
                                MicroTag("RPE ${rpe.toInt()}")
                                EffortScales.rirFor(rpe)?.let { MicroTag("RIR $it") }
                            }
                        }
                    }
                }
            }

            // --- History + progress chart ----------------------------------
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                ExerciseHistorySection(
                    lastSessions = state.lastSessions,
                    progressPoints = state.progressPoints,
                    progressSpan = state.progressSpan,
                    onSpanChange = viewModel::onProgressSpanChange,
                )
            }
        }
    }

    if (showSetDetails) {
        ModalBottomSheet(
            onDismissRequest = { showSetDetails = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Set details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.effortScale.name, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.effortEntryLabel.isNotBlank()) {
                        MicroTag(state.effortEntryLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EffortScales.chipValues(state.effortScale).forEach { value ->
                        FilterChip(
                            selected = when (state.effortScale) {
                                EffortScale.RPE -> state.rpe == value.toString()
                                EffortScale.RIR ->
                                    EffortScales.rirFor(state.rpe.toFloatOrNull()) == value
                            },
                            onClick = {
                                viewModel.onRpeChange(
                                    when (state.effortScale) {
                                        EffortScale.RPE ->
                                            if (state.rpe == value.toString()) "" else value.toString()
                                        EffortScale.RIR ->
                                            if (EffortScales.rirFor(state.rpe.toFloatOrNull()) == value) ""
                                            else EffortScales.rpeFor(value).toString()
                                    },
                                )
                            },
                            label = { MonoText(value.toString()) },
                        )
                    }
                }
                if (state.rpe.isNotBlank()) {
                    TextButton(onClick = { viewModel.onRpeChange("") }) { Text("Clear effort") }
                }
                com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                    value = state.sessionNote,
                    onValueChange = viewModel::onSessionNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Session note") },
                    placeholder = { Text("How'd it feel? Remarks for this workout…") },
                    supportingText = { Text("Saved automatically · this workout only") },
                    minLines = 2,
                )
                com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Exercise cues") },
                    placeholder = { Text("Cues, setup, form reminders…") },
                    supportingText = { Text("Saved automatically · shared across workouts") },
                    minLines = 2,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Finish is an explicit, confirmed action — never a side effect of Back.
    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Finish workout?") },
            text = {
                Text("Your session will be saved and closed. The rest timer will be cancelled.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishConfirm = false
                        viewModel.finishWorkout()
                    },
                ) { Text("Finish") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("Keep training") }
            },
        )
    }

    // Tap-to-edit a logged set: fix fat-fingered numbers, or delete the set.
    editingSet?.let { set ->
        var weightField by remember(set.id) { mutableStateOf(set.weight.toString()) }
        var repsField by remember(set.id) { mutableStateOf(set.reps.toString()) }
        var rpeField by remember(set.id) { mutableStateOf(set.rpe?.toString() ?: "") }
        var rirField by remember(set.id) {
            mutableStateOf(EffortScales.rirFor(set.rpe)?.toString() ?: "")
        }
        // Editing either field re-derives its twin from the entered one.
        fun onRpeEdit(value: String) {
            rpeField = value
            rirField = EffortScales.rirFor(value.toFloatOrNull())?.toString() ?: ""
        }
        fun onRirEdit(value: String) {
            rirField = value
            rpeField = EffortScales.rpeFor(value.toIntOrNull())?.toString() ?: ""
        }
        val weightValue = weightField.toDoubleOrNull()
        val repsValue = repsField.toIntOrNull()
        AlertDialog(
            onDismissRequest = { editingSet = null },
            title = { Text("Edit set ${set.setNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                        value = weightField,
                        onValueChange = { weightField = it },
                        label = { Text("Weight (${state.weightUnitSuffix.trim()})") },
                        singleLine = true,
                    )
                    com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                        value = repsField,
                        onValueChange = { repsField = it },
                        label = { Text("Reps") },
                        singleLine = true,
                    )
                    com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                        value = rpeField,
                        onValueChange = ::onRpeEdit,
                        label = { Text("RPE (optional)") },
                        singleLine = true,
                    )
                    com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                        value = rirField,
                        onValueChange = ::onRirEdit,
                        label = { Text("RIR (optional)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = weightValue != null && repsValue != null &&
                        weightValue >= 0.0 && repsValue >= 0,
                    onClick = {
                        viewModel.updateSet(
                            set = set,
                            weight = weightValue ?: 0.0,
                            reps = repsValue ?: 0,
                            // RPE is canon; RIR was already folded back into it.
                            rpe = rpeField.toFloatOrNull(),
                        )
                        editingSet = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { editingSet = null }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            viewModel.deleteSet(set)
                            editingSet = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete") }
                }
            },
        )
    }

    if (showSwap) {
        AlertDialog(
            onDismissRequest = {
                showSwap = false
                viewModel.onSwapQueryChange("")
            },
            title = { Text("Replace exercise") },
            text = {
                Column {
                    com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                        value = state.swapQuery,
                        onValueChange = viewModel::onSwapQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search exercises") },
                    )
                    LazyColumn {
                        items(state.catalogExercises, key = { it.id }) { exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.overrideWith(exercise.id)
                                        showSwap = false
                                        viewModel.onSwapQueryChange("")
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (exercise.demoUri.isNullOrBlank()) {
                                    Spacer(Modifier.width(36.dp))
                                } else {
                                    ExerciseDemoImage(
                                        uri = exercise.demoUri,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Text(exercise.name)
                            }
                        }
                        if (state.catalogExercises.isEmpty()) {
                            item {
                                Text(
                                    "No exercises match your search",
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSwap = false }) { Text("Cancel") }
            },
        )
    }

    if (showSuperset) {
        // Ad-hoc modes: alternate the remaining sets (classic superset) or
        // insert a single one-off partner set. Both ride the same flow.
        var oneShot by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                showSuperset = false
                viewModel.onSupersetQueryChange("")
            },
            title = { Text("Superset with…") },
            text = {
                Column {
                    com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                        value = state.supersetQuery,
                        onValueChange = viewModel::onSupersetQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search exercises") },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        FilterChip(
                            selected = !oneShot,
                            onClick = { oneShot = false },
                            label = { Text("Alternate remaining sets") },
                        )
                        FilterChip(
                            selected = oneShot,
                            onClick = { oneShot = true },
                            label = { Text("Just this one set") },
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        items(state.supersetExercises, key = { it.id }) { exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addSupersetPartner(exercise.id, oneShot)
                                        showSuperset = false
                                        viewModel.onSupersetQueryChange("")
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (exercise.demoUri.isNullOrBlank()) {
                                    Spacer(Modifier.width(36.dp))
                                } else {
                                    ExerciseDemoImage(
                                        uri = exercise.demoUri,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Text(exercise.name)
                            }
                        }
                        if (state.supersetExercises.isEmpty()) {
                            item {
                                Text(
                                    "No exercises match your search",
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSuperset = false
                    viewModel.onSupersetQueryChange("")
                }) { Text("Cancel") }
            },
        )
    }

    if (showInfo) {
        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            sheetState = infoSheetState,
        ) {
            val exercise = exerciseInfo.exercise
            if (exercise == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    ExerciseInfoContent(
                        exercise = exercise,
                        equipment = exerciseInfo.equipment,
                        secondaryMuscles = exerciseInfo.secondaryMuscles,
                        primaryMuscleName = exerciseInfo.primaryMuscleName,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // Plate loader: how to build the entry-field weight from bar + plates.
    if (showPlates) {
        PlateCalculatorSheet(
            targetWeight = state.weight.toDoubleOrNull() ?: 0.0,
            unit = state.weightUnit,
            onDismiss = { showPlates = false },
            onUseLoadable = { loadable ->
                viewModel.onWeightChange(WeightUnits.displayText(loadable))
                showPlates = false
            },
        )
    }

    // Exercise picker sheet: select any exercise in the routine during workout
    if (showExercisePicker) {
        ModalBottomSheet(
            onDismissRequest = { showExercisePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Select Exercise",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Choose an exercise from your routine. Progress on skipped exercises is preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                state.slots.forEachIndexed { index, slot ->
                    val isSelected = slot.result.id == current?.result?.id
                    val isDone = slot.isComplete
                    val loggedSetsCount = slot.sets.size
                    val plannedSetsCount = slot.plannedSets

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectExercise(slot.result.id)
                                showExercisePicker = false
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SetTicks(
                                done = loggedSetsCount,
                                total = plannedSetsCount.coerceAtLeast(1),
                                modifier = Modifier.width(56.dp),
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "${index + 1}. ${slot.exercise?.name ?: "Exercise"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                val repDetail = if ((slot.slot?.repRangeMin ?: 0) > 0) {
                                    " · ${slot.slot?.repRangeMin}–${slot.slot?.repRangeMax} reps"
                                } else ""
                                Text(
                                    text = "$loggedSetsCount / $plannedSetsCount sets logged$repDetail",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            when {
                                isSelected -> MicroTag("Current", color = MaterialTheme.colorScheme.primary)
                                isDone -> MicroTag("Complete", color = MaterialTheme.colorScheme.primary)
                                loggedSetsCount > 0 -> MicroTag("In Progress", color = MaterialTheme.colorScheme.secondary)
                                else -> MicroTag("Upcoming", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * History for the exercise on screen: the last 3 sessions' stats and a
 * best-e1RM-per-session line chart with a selectable time span.
 */
@Composable
fun ExerciseHistorySection(
    lastSessions: List<ExerciseSessionSummary>,
    progressPoints: List<ProgressPoint>,
    progressSpan: ProgressSpan,
    onSpanChange: (ProgressSpan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Last 3 workouts")
        if (lastSessions.isEmpty()) {
            Text(
                "No history for this exercise yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            lastSessions.forEach { session ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MonoText(
                        text = dateFormat.format(Date(session.sessionStartEpochMs)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MonoText(
                        text = "${session.setCount} sets · top ${session.topWeight} · " +
                            "e1RM ${session.bestE1rm.toInt()}",
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader("Progress")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressSpan.entries.forEach { span ->
                FilterChip(
                    selected = progressSpan == span,
                    onClick = { onSpanChange(span) },
                    label = { Text(span.label) },
                )
            }
        }

        if (progressPoints.isEmpty()) {
            Text(
                "Nothing logged in this period yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ProgressChart(
                points = progressPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }
    }
}

/** Line chart of best e1RM per session over time — amber line, amber fill. */
@Composable
private fun ProgressChart(
    points: List<ProgressPoint>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Canvas(modifier = modifier) {
        if (points.size < 2) {
            // Single point: draw a dot, no line possible.
            if (points.size == 1) {
                drawCircle(
                    color = primary,
                    radius = 6f,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }
            return@Canvas
        }

        val values = points.map { it.bestE1rm }
        val minVal = values.min()
        val maxVal = values.max()
        val valueRange = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val padding = 16f
        val chartLeft = padding
        val chartRight = size.width - padding
        val chartTop = padding
        val chartBottom = size.height - padding
        val chartHeight = chartBottom - chartTop

        fun xFor(time: Long): Float {
            val tMin = points.first().sessionStartEpochMs
            val tMax = points.last().sessionStartEpochMs
            val tRange = (tMax - tMin).takeIf { it > 0 } ?: 1L
            return chartLeft + (time - tMin).toFloat() / tRange * (chartRight - chartLeft)
        }

        fun yFor(value: Double): Float {
            return chartBottom - ((value - minVal) / valueRange).toFloat() * chartHeight
        }

        // Horizontal grid lines at min and max
        listOf(minVal, maxVal).forEach { gridValue ->
            drawLine(
                color = lineColor,
                start = Offset(chartLeft, yFor(gridValue)),
                end = Offset(chartRight, yFor(gridValue)),
                strokeWidth = 1f,
            )
        }

        // Fill under the curve — a low amber wash, like sunrise on steel.
        val fill = Path()
        fill.moveTo(xFor(points.first().sessionStartEpochMs), chartBottom)
        points.forEach { point ->
            fill.lineTo(xFor(point.sessionStartEpochMs), yFor(point.bestE1rm))
        }
        fill.lineTo(xFor(points.last().sessionStartEpochMs), chartBottom)
        fill.close()
        drawPath(fill, color = primary.copy(alpha = 0.15f), style = Fill)

        // The line
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(point.sessionStartEpochMs)
            val y = yFor(point.bestE1rm)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = primary, style = Stroke(width = 4f, cap = StrokeCap.Round))

        // Points on top
        points.forEach { point ->
            drawCircle(
                color = primary,
                radius = 5f,
                center = Offset(xFor(point.sessionStartEpochMs), yFor(point.bestE1rm)),
            )
        }
    }

    // Min/max labels and x-axis date range under the chart — mono, steel.
    val values = points.map { it.bestE1rm }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MonoText(
            text = "${values.min().toInt()} – ${values.max().toInt()} e1RM",
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
        )
        MonoText(
            text = dateFormat.format(Date(points.first().sessionStartEpochMs)) + " – " +
                dateFormat.format(Date(points.last().sessionStartEpochMs)),
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
        )
    }
}
