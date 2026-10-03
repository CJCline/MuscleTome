package com.chy.muscletome.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.domain.model.EffortScale
import com.chy.muscletome.domain.session.EffortScales
import com.chy.muscletome.domain.session.WeightUnits
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.BrutalistCard
import com.chy.muscletome.ui.components.BrutalistOutlinedButton
import com.chy.muscletome.ui.components.ExerciseDemoImage
import com.chy.muscletome.ui.components.LedgerRow
import com.chy.muscletome.ui.components.MetricStepper
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.PlateRing
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField
import com.chy.muscletome.ui.components.SetTicks
import com.chy.muscletome.ui.library.ExerciseInfoContent
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
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

    BackHandler(enabled = !state.finished) { }

    val lastPerformance = state.lastSessions.firstOrNull()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            (current?.exercise?.name ?: "WORKOUT").uppercase(Locale.US),
                            style = MuscleTomeTextStyles.heading,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "LEAVE SESSION OPEN")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showInfo = true },
                            enabled = current != null,
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = "EXERCISE INFO")
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
                                style = MuscleTomeTextStyles.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                lastPerformance?.let {
                                    "${WeightUnits.displayText(it.topWeight)}${state.weightUnitSuffix} · " +
                                        "${it.setCount} SETS · E1RM ${it.bestE1rm.toInt()}"
                                } ?: "NO PREVIOUS WORKOUT",
                                style = MuscleTomeTextStyles.tag,
                                color = MaterialTheme.colorScheme.onBackground,
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MicroTag(
                            text = "EXERCISE ${state.currentIndex + 1} / " +
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
                        MonoText(
                            text = if (planned > 0) {
                                "${state.currentSetNumber.coerceAtMost(planned)} / $planned"
                            } else {
                                state.currentSetNumber.toString()
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        SetTicks(
                            done = done,
                            total = if (planned > 0) planned else done.coerceAtLeast(1),
                        )
                    }

                    if (current?.slot != null) {
                        MicroTag(
                            text = "TARGET ${current.slot.repRangeMin}–${current.slot.repRangeMax} " +
                                "REPS · REST ${current.slot.restSeconds}S" +
                                (state.targetEffortLabel?.let { " · $it" } ?: ""),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.slots.size > 1) {
                            BrutalistOutlinedButton(text = "EXERCISES", onClick = { showExercisePicker = true })
                        }
                        if (viewModel.canReroll()) {
                            BrutalistOutlinedButton(text = "REROLL", onClick = viewModel::reroll)
                        }
                        if (current != null && current.sets.isEmpty()) {
                            BrutalistOutlinedButton(text = "SWAP", onClick = { showSwap = true })
                        }
                        if (viewModel.canSuperset()) {
                            BrutalistOutlinedButton(text = "SUPERSET", onClick = { showSuperset = true })
                        }
                    }
                }
            }

            item {
                val nextSlot = state.nextSlot
                BrutalistCard(
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    onClick = if (nextSlot != null) {
                        { viewModel.selectExercise(nextSlot.result.id) }
                    } else null,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    text = (nextSlot.exercise?.name ?: "NEXT EXERCISE").uppercase(Locale.US),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val planned = nextSlot.plannedSets
                                val minReps = nextSlot.slot?.repRangeMin ?: 0
                                val maxReps = nextSlot.slot?.repRangeMax ?: 0
                                val repsDetail = if (minReps > 0 && maxReps > 0) " · $minReps–$maxReps REPS" else ""
                                val setsDetail = if (planned > 0) "$planned PLANNED SETS$repsDetail" else ""
                                if (setsDetail.isNotBlank()) {
                                    Text(
                                        text = setsDetail,
                                        style = MuscleTomeTextStyles.tag,
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
                                    text = "FINAL EXERCISE IN CURRENT ROUTINE",
                                    style = MuscleTomeTextStyles.systemMessage,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            val group = state.currentGroup
            if (group.size > 1) {
                item {
                    BrutalistCard(borderColor = MaterialTheme.colorScheme.primary) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MicroTag(
                                    text = "SUPERSET",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                MicroTag(
                                    text = "NO REST BETWEEN EXERCISES",
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
                                        (member.exercise?.name ?: "EXERCISE").uppercase(Locale.US),
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
                                            text = "DONE",
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
                            label = "WEIGHT",
                            value = state.weight,
                            onValueChange = viewModel::onWeightChange,
                            onDelta = viewModel::bumpWeight,
                            deltaStep = state.weightStep,
                            longDeltaStep = state.weightLongStep,
                            suffix = state.weightUnitSuffix,
                            large = true,
                        )
                        MetricStepper(
                            label = "REPS",
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
                            BrutalistOutlinedButton(text = "PLATES", onClick = { showPlates = true })
                            BrutalistOutlinedButton(
                                text = "SWITCH TO ${state.otherUnit.name}",
                                onClick = viewModel::toggleUnit,
                            )
                            TextButton(onClick = { showSetDetails = true }) {
                                Text("RPE & NOTES", style = MuscleTomeTextStyles.button)
                            }
                        }
                    }
                }
            }

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
                    BrutalistCard(borderColor = MaterialTheme.colorScheme.primary) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                    SectionHeader("REST")
                                    TextButton(
                                        onClick = viewModel::skipRest,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Text("SKIP", style = MuscleTomeTextStyles.button)
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BrutalistOutlinedButton(text = "-30S", onClick = { viewModel.addRest(-30) }, minHeight = 36.dp)
                                    BrutalistOutlinedButton(text = "-10S", onClick = { viewModel.addRest(-10) }, minHeight = 36.dp)
                                    BrutalistOutlinedButton(text = "+10S", onClick = { viewModel.addRest(10) }, minHeight = 36.dp)
                                    BrutalistOutlinedButton(text = "+30S", onClick = { viewModel.addRest(30) }, minHeight = 36.dp)
                                }
                            }
                        }
                    }
                }
            }

            // Primary action bar anchored to bottom thumb zone
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton(
                        text = "LOG SET",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.logSet()
                        },
                        enabled = current != null && !state.finished,
                        minHeight = 64.dp,
                    )

                    if (state.isCurrentComplete && !state.isLastExercise) {
                        val nextName = state.nextSlot?.exercise?.name
                        val buttonText = if (!nextName.isNullOrBlank()) {
                            "NEXT EXERCISE: ${nextName.uppercase(Locale.US)}"
                        } else {
                            "NEXT EXERCISE"
                        }
                        BrutalistOutlinedButton(
                            text = buttonText,
                            onClick = viewModel::nextExercise,
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 56.dp,
                        )
                    }

                    if (state.isCurrentComplete && state.isLastExercise) {
                        AccentButton(
                            text = "FINISH WORKOUT",
                            onClick = { showFinishConfirm = true },
                            minHeight = 56.dp,
                        )
                    }
                }
            }

            if ((current?.sets?.size ?: 0) > 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader("LOGGED SETS")
                        TextButton(onClick = viewModel::undoLastSet) {
                            Text("UNDO LAST", style = MuscleTomeTextStyles.button)
                        }
                    }
                }
                items(current?.sets ?: emptyList(), key = { it.id }) { set ->
                    val weightDisplay = "${MuscleTomeTextStyles.formatWeight(set.weight)}${state.weightUnitSuffix.trim()} × ${set.reps}"
                    val prTag = if (set.id in state.prSetIds) "PR" else null
                    LedgerRow(
                        index = set.setNumber,
                        primaryText = "SET ${set.setNumber.toString().padStart(2, '0')}",
                        dataText = weightDisplay,
                        bracketTag = prTag,
                        onClick = { editingSet = set },
                    )
                }
            }

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
                Text("SET DETAILS", style = MuscleTomeTextStyles.heading)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.effortScale.name,
                        style = MuscleTomeTextStyles.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    TextButton(onClick = { viewModel.onRpeChange("") }) { Text("CLEAR EFFORT", style = MuscleTomeTextStyles.button) }
                }
                SelectOnFocusOutlinedTextField(
                    value = state.sessionNote,
                    onValueChange = viewModel::onSessionNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SESSION NOTE", style = MuscleTomeTextStyles.label) },
                    placeholder = { Text("REMARKS FOR THIS WORKOUT...", style = MuscleTomeTextStyles.tag) },
                    supportingText = { Text("SAVED AUTOMATICALLY · THIS WORKOUT ONLY", style = MuscleTomeTextStyles.tag) },
                    minLines = 2,
                )
                SelectOnFocusOutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("EXERCISE CUES", style = MuscleTomeTextStyles.label) },
                    placeholder = { Text("CUES, SETUP, FORM REMINDERS...", style = MuscleTomeTextStyles.tag) },
                    supportingText = { Text("SAVED AUTOMATICALLY · SHARED ACROSS WORKOUTS", style = MuscleTomeTextStyles.tag) },
                    minLines = 2,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("FINISH WORKOUT?", style = MuscleTomeTextStyles.heading) },
            text = {
                Text(
                    "SESSION WILL BE SAVED AND CLOSED. REST TIMER CANCELLED.",
                    style = MuscleTomeTextStyles.systemMessage,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishConfirm = false
                        viewModel.finishWorkout()
                    },
                ) { Text("FINISH", style = MuscleTomeTextStyles.button, color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("KEEP TRAINING", style = MuscleTomeTextStyles.button) }
            },
        )
    }

    editingSet?.let { set ->
        var weightField by remember(set.id) { mutableStateOf(set.weight.toString()) }
        var repsField by remember(set.id) { mutableStateOf(set.reps.toString()) }
        var rpeField by remember(set.id) { mutableStateOf(set.rpe?.toString() ?: "") }
        var rirField by remember(set.id) {
            mutableStateOf(EffortScales.rirFor(set.rpe)?.toString() ?: "")
        }
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
            title = { Text("EDIT SET ${set.setNumber}", style = MuscleTomeTextStyles.heading) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectOnFocusOutlinedTextField(
                        value = weightField,
                        onValueChange = { weightField = it },
                        label = { Text("WEIGHT (${state.weightUnitSuffix.trim()})", style = MuscleTomeTextStyles.label) },
                        singleLine = true,
                    )
                    SelectOnFocusOutlinedTextField(
                        value = repsField,
                        onValueChange = { repsField = it },
                        label = { Text("REPS", style = MuscleTomeTextStyles.label) },
                        singleLine = true,
                    )
                    SelectOnFocusOutlinedTextField(
                        value = rpeField,
                        onValueChange = ::onRpeEdit,
                        label = { Text("RPE (OPTIONAL)", style = MuscleTomeTextStyles.label) },
                        singleLine = true,
                    )
                    SelectOnFocusOutlinedTextField(
                        value = rirField,
                        onValueChange = ::onRirEdit,
                        label = { Text("RIR (OPTIONAL)", style = MuscleTomeTextStyles.label) },
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
                            rpe = rpeField.toFloatOrNull(),
                        )
                        editingSet = null
                    },
                ) { Text("SAVE", style = MuscleTomeTextStyles.button) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { editingSet = null }) { Text("CANCEL", style = MuscleTomeTextStyles.button) }
                    TextButton(
                        onClick = {
                            viewModel.deleteSet(set)
                            editingSet = null
                        },
                    ) { Text("DELETE", style = MuscleTomeTextStyles.button, color = MaterialTheme.colorScheme.error) }
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
            title = { Text("REPLACE EXERCISE", style = MuscleTomeTextStyles.heading) },
            text = {
                Column {
                    SelectOnFocusOutlinedTextField(
                        value = state.swapQuery,
                        onValueChange = viewModel::onSwapQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("SEARCH EXERCISES", style = MuscleTomeTextStyles.label) },
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
                                Text(exercise.name.uppercase(Locale.US), style = MuscleTomeTextStyles.button)
                            }
                        }
                        if (state.catalogExercises.isEmpty()) {
                            item {
                                Text(
                                    "NO MATCHING EXERCISES",
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    style = MuscleTomeTextStyles.systemMessage,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSwap = false }) { Text("CANCEL", style = MuscleTomeTextStyles.button) }
            },
        )
    }

    if (showSuperset) {
        var oneShot by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                showSuperset = false
                viewModel.onSupersetQueryChange("")
            },
            title = { Text("SUPERSET WITH", style = MuscleTomeTextStyles.heading) },
            text = {
                Column {
                    SelectOnFocusOutlinedTextField(
                        value = state.supersetQuery,
                        onValueChange = viewModel::onSupersetQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("SEARCH EXERCISES", style = MuscleTomeTextStyles.label) },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        FilterChip(
                            selected = !oneShot,
                            onClick = { oneShot = false },
                            label = { Text("ALTERNATE REMAINING SETS", style = MuscleTomeTextStyles.tag) },
                        )
                        FilterChip(
                            selected = oneShot,
                            onClick = { oneShot = true },
                            label = { Text("ONE SET ONLY", style = MuscleTomeTextStyles.tag) },
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
                                Text(exercise.name.uppercase(Locale.US), style = MuscleTomeTextStyles.button)
                            }
                        }
                        if (state.supersetExercises.isEmpty()) {
                            item {
                                Text(
                                    "NO MATCHING EXERCISES",
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    style = MuscleTomeTextStyles.systemMessage,
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
                }) { Text("CANCEL", style = MuscleTomeTextStyles.button) }
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
                        exercise.name.uppercase(Locale.US),
                        style = MuscleTomeTextStyles.heading,
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
                    text = "SELECT EXERCISE",
                    style = MuscleTomeTextStyles.heading,
                )
                Text(
                    text = "CHOOSE EXERCISE FROM ROUTINE. SKIPPED EXERCISE PROGRESS PRESERVED.",
                    style = MuscleTomeTextStyles.systemMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                state.slots.forEachIndexed { index, slot ->
                    val isSelected = slot.result.id == current?.result?.id
                    val isDone = slot.isComplete
                    val loggedSetsCount = slot.sets.size
                    val plannedSetsCount = slot.plannedSets

                    BrutalistCard(
                        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        spineColor = if (isSelected) MaterialTheme.colorScheme.primary else null,
                        onClick = {
                            viewModel.selectExercise(slot.result.id)
                            showExercisePicker = false
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                    text = "${index + 1}. ${(slot.exercise?.name ?: "EXERCISE").uppercase(Locale.US)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                val repDetail = if ((slot.slot?.repRangeMin ?: 0) > 0) {
                                    " · ${slot.slot?.repRangeMin}–${slot.slot?.repRangeMax} REPS"
                                } else ""
                                Text(
                                    text = "$loggedSetsCount / $plannedSetsCount SETS LOGGED$repDetail",
                                    style = MuscleTomeTextStyles.tag,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            when {
                                isSelected -> MicroTag("CURRENT", color = MaterialTheme.colorScheme.primary)
                                isDone -> MicroTag("COMPLETE", color = MaterialTheme.colorScheme.primary)
                                loggedSetsCount > 0 -> MicroTag("IN PROGRESS", color = MaterialTheme.colorScheme.secondary)
                                else -> MicroTag("UPCOMING", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ExerciseHistorySection(
    lastSessions: List<ExerciseSessionSummary>,
    progressPoints: List<ProgressPoint>,
    progressSpan: ProgressSpan,
    onSpanChange: (ProgressSpan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.US) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("LAST 3 WORKOUTS")
        if (lastSessions.isEmpty()) {
            Text(
                "NO HISTORY FOR THIS EXERCISE YET",
                style = MuscleTomeTextStyles.systemMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            lastSessions.forEach { session ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MonoText(
                        text = dateFormat.format(Date(session.sessionStartEpochMs)).uppercase(Locale.US),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MonoText(
                        text = "${session.setCount} SETS · TOP ${MuscleTomeTextStyles.formatWeight(session.topWeight)} · " +
                            "E1RM ${session.bestE1rm.toInt()}",
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader("PROGRESS")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressSpan.entries.forEach { span ->
                FilterChip(
                    selected = progressSpan == span,
                    onClick = { onSpanChange(span) },
                    label = { Text(span.label.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                )
            }
        }

        if (progressPoints.isEmpty()) {
            Text(
                "NO LOGGED DATA IN THIS PERIOD",
                style = MuscleTomeTextStyles.systemMessage,
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

@Composable
private fun ProgressChart(
    points: List<ProgressPoint>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.US) }

    Canvas(modifier = modifier) {
        if (points.size < 2) {
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

        listOf(minVal, maxVal).forEach { gridValue ->
            drawLine(
                color = lineColor,
                start = Offset(chartLeft, yFor(gridValue)),
                end = Offset(chartRight, yFor(gridValue)),
                strokeWidth = 1f,
            )
        }

        val fill = Path()
        fill.moveTo(xFor(points.first().sessionStartEpochMs), chartBottom)
        points.forEach { point ->
            fill.lineTo(xFor(point.sessionStartEpochMs), yFor(point.bestE1rm))
        }
        fill.lineTo(xFor(points.last().sessionStartEpochMs), chartBottom)
        fill.close()
        drawPath(fill, color = primary.copy(alpha = 0.15f), style = Fill)

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(point.sessionStartEpochMs)
            val y = yFor(point.bestE1rm)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = primary, style = Stroke(width = 4f, cap = StrokeCap.Round))

        points.forEach { point ->
            drawCircle(
                color = primary,
                radius = 5f,
                center = Offset(xFor(point.sessionStartEpochMs), yFor(point.bestE1rm)),
            )
        }
    }

    val values = points.map { it.bestE1rm }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MonoText(
            text = "${values.min().toInt()} – ${values.max().toInt()} E1RM",
            style = MuscleTomeTextStyles.tag,
            color = onSurfaceVariant,
        )
        MonoText(
            text = (dateFormat.format(Date(points.first().sessionStartEpochMs)) + " – " +
                dateFormat.format(Date(points.last().sessionStartEpochMs))).uppercase(Locale.US),
            style = MuscleTomeTextStyles.tag,
            color = onSurfaceVariant,
        )
    }
}
