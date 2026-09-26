package com.chy.muscletome.ui.workout

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.ui.library.ExerciseInfoContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onFinished: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exerciseInfo by viewModel.exerciseInfo.collectAsStateWithLifecycle()
    val current = state.current
    var showSwap by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.exercise?.name ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = viewModel::finishWorkout) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Finish")
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
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Exercise ${state.currentIndex + 1} of ${state.slots.size.coerceAtLeast(1)}",
                style = MaterialTheme.typography.labelLarge,
            )
            if (current != null) {
                val setInfo = if (state.plannedSets > 0) {
                    "Set ${state.currentSetNumber.coerceAtMost(state.plannedSets)} of ${state.plannedSets}"
                } else {
                    "Set ${state.currentSetNumber}"
                }
                val repInfo = if (current.slot != null) {
                    " · ${current.slot.repRangeMin}-${current.slot.repRangeMax} reps"
                } else {
                    ""
                }
                Text(
                    "$setInfo$repInfo",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                viewModel.reasonLabel(),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.bumpWeight(-2.5) }) { Text("-") }
                OutlinedTextField(
                    value = state.weight,
                    onValueChange = viewModel::onWeightChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Weight") },
                )
                OutlinedButton(onClick = { viewModel.bumpWeight(2.5) }) { Text("+") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.bumpReps(-1) }) { Text("-") }
                OutlinedTextField(
                    value = state.reps,
                    onValueChange = viewModel::onRepsChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Reps") },
                )
                OutlinedButton(onClick = { viewModel.bumpReps(1) }) { Text("+") }
            }

            OutlinedTextField(
                value = state.rpe,
                onValueChange = viewModel::onRpeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("RPE (optional)") },
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Exercise note") },
                placeholder = { Text("Cues, setup, form reminders…") },
                supportingText = { Text("Saved automatically · shared across workouts") },
                minLines = 2,
            )

            if (state.restSecondsLeft > 0) {
                Text("Rest ${state.restSecondsLeft}s")
                TextButton(onClick = viewModel::skipRest) { Text("Skip rest") }
            }

            Button(
                onClick = viewModel::logSet,
                enabled = current != null && !state.finished,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log set")
            }

            if (state.isCurrentComplete && !state.isLastExercise) {
                Button(
                    onClick = viewModel::nextExercise,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Next exercise") }
            }

            if (state.isCurrentComplete && state.isLastExercise) {
                Button(
                    onClick = viewModel::finishWorkout,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Finish workout") }
            }

            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f)) {
                // --- Exercise history: last 3 sessions + progress chart ---------
                item {
                    ExerciseHistorySection(
                        lastSessions = state.lastSessions,
                        progressPoints = state.progressPoints,
                        progressSpan = state.progressSpan,
                        onSpanChange = viewModel::onProgressSpanChange,
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }
                items(current?.sets ?: emptyList(), key = { it.id }) { set ->
                    ListItem(
                        headlineContent = { Text("Set ${set.setNumber}") },
                        supportingContent = {
                            val rpeText = set.rpe?.let { " · RPE $it" } ?: ""
                            Text("${set.weight} × ${set.reps}$rpeText")
                        },
                    )
                }
            }
        }
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
                    OutlinedTextField(
                        value = state.swapQuery,
                        onValueChange = viewModel::onSwapQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search exercises") },
                    )
                    LazyColumn {
                        items(state.catalogExercises, key = { it.id }) { exercise ->
                            Text(
                                exercise.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.overrideWith(exercise.id)
                                        showSwap = false
                                        viewModel.onSwapQueryChange("")
                                    }
                                    .padding(vertical = 8.dp),
                            )
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

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Last 3 workouts", style = MaterialTheme.typography.titleMedium)
        if (lastSessions.isEmpty()) {
            Text(
                "No history for this exercise yet.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            lastSessions.forEach { session ->
                Text(
                    "${dateFormat.format(Date(session.sessionStartEpochMs))} · " +
                        "${session.setCount} sets · top ${session.topWeight} · " +
                        "e1RM ${session.bestE1rm.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Progress", style = MaterialTheme.typography.titleMedium)

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

/** Line chart of best e1RM per session over time. */
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

    // Min/max labels and x-axis date range under the chart
    val values = points.map { it.bestE1rm }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "${values.min().toInt()} – ${values.max().toInt()} e1RM",
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
        )
        Text(
            "${dateFormat.format(Date(points.first().sessionStartEpochMs))} – " +
                dateFormat.format(Date(points.last().sessionStartEpochMs)),
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
        )
    }
}
