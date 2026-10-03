package com.chy.muscletome.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType
import com.chy.muscletome.domain.session.WeightUnits
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.BrutalistOutlinedButton
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    val blockedMessage = state.deleteBlockedMessage
    LaunchedEffect(blockedMessage) {
        if (blockedMessage != null) {
            snackbarHostState.showSnackbar(blockedMessage.uppercase(Locale.US))
            viewModel.dismissDeleteBlocked()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text((state.exercise?.name ?: "EXERCISE").uppercase(Locale.US), style = MuscleTomeTextStyles.heading) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "BACK")
                    }
                },
                actions = {
                    if (state.userOwned && state.exercise != null) {
                        IconButton(onClick = { state.exercise?.let { onEdit(it.id) } }) {
                            Icon(Icons.Default.Edit, contentDescription = "EDIT EXERCISE")
                        }
                        IconButton(onClick = viewModel::deleteUserExercise) {
                            Icon(Icons.Default.Delete, contentDescription = "DELETE EXERCISE")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            !state.loaded -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.exercise == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("EXERCISE NOT FOUND", style = MuscleTomeTextStyles.systemMessage)
            }
            else -> ExerciseDetailContent(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onDownloadMedia = viewModel::downloadMedia,
                onReplaceInActiveWorkout = { result, oldName ->
                    viewModel.replaceInActiveWorkout(result)
                    val newName = state.exercise?.name.orEmpty()
                    scope.launch {
                        snackbarHostState.showSnackbar("REPLACED \"${oldName.uppercase(Locale.US)}\" WITH \"${newName.uppercase(Locale.US)}\"")
                    }
                },
            )
        }
    }
}

@Composable
internal fun ExerciseDetailContent(
    state: ExerciseDetailUiState,
    modifier: Modifier = Modifier,
    onDownloadMedia: () -> Unit = {},
    onReplaceInActiveWorkout: (SessionSlotResultEntity, String) -> Unit = { _, _ -> },
) {
    val exercise = checkNotNull(state.exercise)
    val canonical = state.canonical
    val uriHandler = LocalUriHandler.current
    var showReplaceDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.activeSessionSlots.isNotEmpty()) {
            BrutalistOutlinedButton(
                text = "REPLACE IN ACTIVE WORKOUT",
                onClick = { showReplaceDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ExerciseInfoContent(
            exercise = exercise,
            equipment = state.equipment,
            secondaryMuscles = state.secondaryMuscles,
            primaryMuscleName = state.primaryMuscleName,
        )

        val instructions = canonical?.instructions.orEmpty().sortedBy { it.sortOrder }
        if (instructions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("HOW TO PERFORM")
                instructions.forEachIndexed { index, step ->
                    Text("${index + 1}. ${step.instruction}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        val media = canonical?.media.orEmpty().filter { it.uri.isNotBlank() }
        val fallbackUri = exercise.demoUri?.takeIf { legacy -> media.none { it.uri == legacy } }
        if (media.isNotEmpty() || fallbackUri != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("MEDIA")
                if (state.downloadableMedia && !state.mediaFullyCached) {
                    AccentButton(
                        text = "DOWNLOAD / SHOW OFFLINE",
                        onClick = onDownloadMedia,
                    )
                }
                if (state.mediaFullyCached) {
                    Text(
                        "SAVED FOR OFFLINE",
                        style = MuscleTomeTextStyles.tag,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                (media.map { it.type to it.uri } + listOfNotNull(fallbackUri?.let { "IMAGE" to it })).forEach { (type, uri) ->
                    if (type == "VIDEO") {
                        Text(
                            "OPEN VIDEO: $uri",
                            color = MaterialTheme.colorScheme.primary,
                            style = MuscleTomeTextStyles.button,
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(uri) } },
                        )
                    } else {
                        val cached = state.cachedMediaUris[media.firstOrNull { it.uri == uri }?.id]
                        Image(
                            painter = rememberAsyncImagePainter(cached ?: uri),
                            contentDescription = "EXERCISE MEDIA",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                                .semantics { contentDescription = "EXERCISE IMAGE" },
                        )
                    }
                    val record = media.firstOrNull { it.uri == uri }
                    val attribution = mediaAttribution(record)
                    if (attribution.isNotBlank()) {
                        Text(
                            attribution.uppercase(Locale.US),
                            style = MuscleTomeTextStyles.tag,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Text(
                "NO EXERCISE MEDIA AVAILABLE",
                style = MuscleTomeTextStyles.systemMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("HISTORY")
            val history = state.history
            if (history.lastSet == null) {
                Text(
                    "NO SETS LOGGED YET",
                    style = MuscleTomeTextStyles.systemMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 13.dp),
                ) {
                    MonoText(
                        text = "LAST SET: ${MuscleTomeTextStyles.formatWeight(history.lastSet.weight)}" +
                            "${WeightUnits.suffix(state.weightUnit)} × ${history.lastSet.reps}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MonoText(
                        text = "BEST WEIGHT: " +
                            WeightUnits.display(history.bestWeight ?: 0.0, state.weightUnit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MonoText(
                        text = "LOGGED IN ${history.sessionCount} SESSION" +
                            if (history.sessionCount == 1) "" else "S",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            title = { Text("REPLACE IN ACTIVE WORKOUT", style = MuscleTomeTextStyles.heading) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "WHICH EXERCISE DO YOU WANT TO REPLACE WITH \"${exercise.name.uppercase(Locale.US)}\"?",
                        style = MuscleTomeTextStyles.systemMessage,
                    )
                    LazyColumn(
                        modifier = Modifier.height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.activeSessionSlots) { option ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showReplaceDialog = false
                                        onReplaceInActiveWorkout(option.result, option.currentExerciseName)
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
                TextButton(onClick = { showReplaceDialog = false }) {
                    Text("CANCEL", style = MuscleTomeTextStyles.button)
                }
            },
        )
    }
}

internal fun mediaAttribution(record: ExerciseMediaEntity?): String =
    listOfNotNull(record?.creator, record?.attribution, record?.licenseName)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString(" · ")

@Preview
@Composable
private fun ExerciseDetailContentPreview() {
    MaterialTheme {
        ExerciseDetailContent(
            state = ExerciseDetailUiState(
                exercise = ExerciseEntity(
                    id = "squat",
                    name = "Barbell Back Squat",
                    description = "A compound lower-body strength exercise.",
                    movementPattern = MovementPattern.SQUAT,
                    movementType = MovementType.COMPOUND,
                    primaryMuscleGroupId = "quads",
                    difficulty = Difficulty.INTERMEDIATE,
                ),
                equipment = listOf(
                    EquipmentEntity("barbell", "Barbell"),
                    EquipmentEntity("rack", "Rack"),
                ),
                secondaryMuscles = listOf(
                    MuscleGroupEntity("glutes", "Glutes", "legs"),
                    MuscleGroupEntity("hamstrings", "Hamstrings", "legs"),
                ),
                history = ExerciseHistory(
                    lastSet = SetLogEntity(
                        id = "set-1",
                        sessionSlotResultId = "result-1",
                        setNumber = 3,
                        weight = 100.0,
                        reps = 8,
                        completedAtEpochMs = 0L,
                    ),
                    bestWeight = 105.0,
                    sessionCount = 4,
                ),
                primaryMuscleName = "Quads",
                loaded = true,
            ),
        )
    }
}
