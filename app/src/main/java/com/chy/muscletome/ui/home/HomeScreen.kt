package com.chy.muscletome.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.R
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.BrutalistCard
import com.chy.muscletome.ui.components.BrutalistOutlinedButton
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.TemplatesBanner
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.time.Duration
import java.util.Locale

/**
 * Home = "what do I do today?". Active session, next day with an exercise
 * preview, last session one-liner, weekly consistency — plus the starter-
 * programs banner while the user is still assembling a program.
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

    var templatesExpanded by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.startError.collect { message ->
            snackbarHostState.showSnackbar(message.uppercase(Locale.US))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.routineAdded.collect { name ->
            snackbarHostState.showSnackbar("ADDED \"${name.uppercase(Locale.US)}\" — SET AS PROGRAM")
            templatesExpanded = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startSessionId.collect { sessionId ->
            onOpenWorkout(sessionId)
        }
    }

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
                            style = MuscleTomeTextStyles.heading,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            stringResource(R.string.tagline),
                            style = MuscleTomeTextStyles.tag,
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

            val hasActiveSession = (state.openSession != null) && !state.startingWorkout
            if (hasActiveSession || state.nextDay != null) {
                item {
                    BrutalistCard(
                        spineColor = if (hasActiveSession) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MicroTag(
                                text = if (hasActiveSession) "WORKOUT IN PROGRESS" else "UP NEXT",
                                color = if (hasActiveSession) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                listOfNotNull(state.nextRoutineName, state.nextDay?.name)
                                    .joinToString(" · ")
                                    .uppercase(Locale.US),
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
                                            entry.name.uppercase(Locale.US),
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
                                        "+${state.previewMore} MORE",
                                        style = MuscleTomeTextStyles.tag,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (hasActiveSession) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AccentButton(
                                        text = "RESUME",
                                        onClick = viewModel::resumeOpenSession,
                                        modifier = Modifier.weight(1f),
                                    )
                                    BrutalistOutlinedButton(
                                        text = "DISCARD",
                                        onClick = { showDiscardConfirm = true },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            } else {
                                AccentButton(
                                    text = "START WORKOUT",
                                    onClick = viewModel::startNextDay,
                                    enabled = !state.startingWorkout,
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    BrutalistCard(spineColor = MaterialTheme.colorScheme.outline) {
                        Column {
                            EmptyState(
                                title = "NO ROUTINES YET",
                                body = "BUILD YOUR FIRST TRAINING TOME.",
                            )
                            AccentButton(
                                text = "CREATE A ROUTINE",
                                onClick = onOpenRoutines,
                            )
                        }
                    }
                }
            }

            // --- THIS WEEK ------------------------------------------------
            item { SectionHeader("THIS WEEK") }
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
                            if (state.weekSessionCount == 1) "SESSION THIS WEEK"
                            else "SESSIONS THIS WEEK",
                            style = MuscleTomeTextStyles.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    val lastLine = lastSessionLine(state)
                    Text(
                        lastLine,
                        style = MuscleTomeTextStyles.systemMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- PROGRAM POINTER ------------------------------------------
            item { SectionHeader("PROGRAM") }
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
                            (state.nextRoutineName ?: "NO PROGRAM YET").uppercase(Locale.US),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "NEXT: ${(state.nextDay?.name ?: "—").uppercase(Locale.US)} · MANAGE IN ROUTINES",
                            style = MuscleTomeTextStyles.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "ROUTINES",
                        style = MuscleTomeTextStyles.button,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("DISCARD WORKOUT?", style = MuscleTomeTextStyles.heading) },
            text = {
                Text(
                    "ALL LOGGED SETS WILL BE PERMANENTLY DELETED.",
                    style = MuscleTomeTextStyles.systemMessage,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        viewModel.discardOpenSession()
                    },
                ) {
                    Text(
                        "DISCARD",
                        style = MuscleTomeTextStyles.button,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("KEEP WORKOUT", style = MuscleTomeTextStyles.button)
                }
            },
        )
    }
}

private fun lastSessionLine(state: HomeUiState): String {
    val last = state.lastCompleted ?: return "NO SESSIONS LOGGED YET"
    val days = Duration.ofMillis(
        System.currentTimeMillis() - last.startedAtEpochMs,
    ).toDays()
    val whenText = when {
        days <= 0L -> "TODAY"
        days == 1L -> "YESTERDAY"
        days < 7L -> "$days DAYS AGO"
        else -> "OVER A WEEK AGO"
    }
    return "LAST SESSION $whenText"
}
