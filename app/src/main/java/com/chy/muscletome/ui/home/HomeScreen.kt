package com.chy.muscletome.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.TemplatesBanner
import java.time.Duration

/**
 * Home = "what do I do today?". Active session, next day with an exercise
 * preview, last session one-liner, weekly consistency — plus the starter-
 * programs banner while the user is still assembling a program. The full
 * routine list (and program switching) lives in the Routines tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenWorkout: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardConfirm by remember { mutableStateOf(false) }

    // Starter programs stay collapsed until the user explicitly opens them.
    var templatesExpanded by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.startError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.routineAdded.collect { name ->
            snackbarHostState.showSnackbar("Added \"$name\" — set as your program")
            templatesExpanded = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startSessionId.collect { sessionId ->
            onOpenWorkout(sessionId)
        }
    }

    // Returning to Home (bottom-nav reselect) refreshes the preview and the
    // weekly count — their data is computed once per emission.
    DisposableEffect(viewModel) {
        viewModel.refresh()
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
            // --- Templates banner: only while there is no program ----------
            if (state.routineCount == 0) {
                item {
                    TemplatesBanner(
                        expanded = templatesExpanded,
                        onToggle = { templatesExpanded = !templatesExpanded },
                        onAddTemplate = { template ->
                            viewModel.addRoutineFromTemplate(template)
                        },
                    )
                }
            }
            // While a workout start is in flight, keep showing the "Up next" card.
            // The open session row already exists at that point and would otherwise
            // flash the "Workout in progress" (Resume/Discard) card before
            // navigation lands on the workout screen.
            if ((state.openSession != null) && !state.startingWorkout) {
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
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MicroTag(text = "Up next")
                            Text(
                                listOfNotNull(state.nextRoutineName, state.nextDay?.name)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.preview.forEach { entry ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            entry.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        entry.targetLabel?.let { label ->
                                            MicroTag(
                                                text = label,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                if (state.previewMore > 0) {
                                    Text(
                                        "+${state.previewMore} more",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
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
                        Column {
                            EmptyState(
                                title = "No routines yet",
                                body = "Build your first training tome.",
                            )
                            Button(
                                onClick = onOpenRoutines,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) { Text("Create a routine".uppercase()) }
                        }
                    }
                }
            }

            // --- This week ------------------------------------------------
            item { SectionHeader("This week") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MonoText(
                            text = state.weekSessionCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            if (state.weekSessionCount == 1) "session this week"
                            else "sessions this week",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    val lastLine = lastSessionLine(state)
                    Text(
                        lastLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.weekSessionCount == 0) {
                        Text(
                            "Every session is a page in the tome — log the first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // --- Program pointer ------------------------------------------
            item { SectionHeader("Program") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenRoutines)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.nextRoutineName ?: "No program yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Next: ${state.nextDay?.name ?: "—"} · manage in Routines",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Routines".uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
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

/** One-liner about the last finished session (or a first-run nudge). */
private fun lastSessionLine(state: HomeUiState): String {
    val last = state.lastCompleted ?: return "No sessions logged yet."
    val days = Duration.ofMillis(
        System.currentTimeMillis() - last.startedAtEpochMs,
    ).toDays()
    val whenText = when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 7L -> "$days days ago"
        else -> "over a week ago"
    }
    return "Last session $whenText" +
        if (state.lastCompletedThisWeek) " — this week's tally already counts it."
        else "."
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


