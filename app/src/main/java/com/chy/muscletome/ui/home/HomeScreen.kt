package com.chy.muscletome.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.LedgerIndex
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenWorkout: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.startSessionId.collect { sessionId ->
            onOpenWorkout(sessionId)
        }
    }

    // Start failed (e.g. no exercise matches a slot with the user's
    // equipment) — surface it instead of silently doing nothing.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.startError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Clear the "starting workout" flag once Home has fully left composition
    // (i.e. the navigation transition to the workout screen has finished).
    DisposableEffect(viewModel) {
        onDispose { viewModel.clearStartingWorkout() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "MUSCLETOME",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Every rep, a page.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // While a workout start is in flight, keep showing the "Up next" card.
            // The open session row already exists at that point and would otherwise
            // flash the "Workout in progress" (Resume/Discard) card before
            // navigation lands on the workout screen.
            if (state.openSession != null && !state.startingWorkout) {
                item {
                    SpineCard(accent = MaterialTheme.colorScheme.error) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MicroTag(
                                text = "Workout in progress",
                                color = MaterialTheme.colorScheme.error,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = viewModel::resumeOpenSession,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Resume".uppercase()) }
                                Button(
                                    onClick = { showDiscardConfirm = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) { Text("Discard".uppercase()) }
                            }
                        }
                    }
                }
            } else if (state.nextDay != null) {
                item {
                    SpineCard(accent = MaterialTheme.colorScheme.primary) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MicroTag(text = "Up next")
                            Text(
                                listOfNotNull(state.nextRoutineName, state.nextDay?.name)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Button(
                                onClick = viewModel::startNextDay,
                                enabled = !state.startingWorkout,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) { Text("Start workout".uppercase()) }
                        }
                    }
                }
            } else {
                item {
                    SpineCard(accent = MaterialTheme.colorScheme.outline) {
                        EmptyState(
                            title = "No routines yet",
                            body = "Build your first training tome.",
                        )
                        Button(
                            onClick = onOpenRoutines,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) { Text("Create a routine".uppercase()) }
                    }
                }
            }

            item { SectionHeader("Routines") }
            itemsIndexed(state.routines, key = { _, routine -> routine.id }) { index, routine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRoutine(routine.id) }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LedgerIndex(index = index + 1)
                    Text(
                        routine.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    if (routine.id == state.activeRoutineId) {
                        MicroTag(text = "Active")
                    } else {
                        TextButton(onClick = { viewModel.setActiveRoutine(routine.id) }) {
                            Text("Set active")
                        }
                    }
                }
            }
        }
    }

    // Discard deletes the in-progress session and every logged set — never
    // a one-tap action.
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard workout?") },
            text = {
                Text("All sets logged in this session will be permanently deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        viewModel.discardOpenSession()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep it") }
            },
        )
    }
}

/**
 * A monolithic ink card with a heavy vertical spine on its left edge —
 * the book-spine motif. [accent] colors the spine (amber = action,
 * rust = warning, steel = neutral).
 */
@Composable
private fun SpineCard(
    accent: Color,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(160.dp)
                .background(accent),
        )
        Column(modifier = Modifier.weight(1f)) { content() }
    }
}
