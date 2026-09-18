package com.chy.muscletome.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onFinished: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state.current

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
                Text(
                    "Set ${state.currentSetNumber.coerceAtMost(state.plannedSets)} of ${state.plannedSets} · ${current.slot?.repRangeMin}-${current.slot?.repRangeMax} reps",
                    style = MaterialTheme.typography.titleMedium,
                )
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

            if (state.restSecondsLeft > 0) {
                Text("Rest ${state.restSecondsLeft}s")
                TextButton(onClick = viewModel::skipRest) { Text("Skip rest") }
            }

            Button(
                onClick = viewModel::logSet,
                enabled = current != null && !state.isCurrentComplete && !state.finished,
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

            LazyColumn(modifier = Modifier.weight(1f)) {
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
}
