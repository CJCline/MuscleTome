package com.chy.muscletome.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.chy.muscletome.data.local.entity.EquipmentEntity
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.data.local.entity.ExerciseMediaEntity
import com.chy.muscletome.data.local.entity.MuscleGroupEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.ExerciseMediaType
import com.chy.muscletome.domain.session.WeightUnits
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.domain.model.MovementPattern
import com.chy.muscletome.domain.model.MovementType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Text("Exercise not found")
            }
            else -> ExerciseDetailContent(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
internal fun ExerciseDetailContent(
    state: ExerciseDetailUiState,
    modifier: Modifier = Modifier,
) {
    val exercise = checkNotNull(state.exercise)
    val canonical = state.canonical
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseInfoContent(
            exercise = exercise,
            equipment = state.equipment,
            secondaryMuscles = state.secondaryMuscles,
            primaryMuscleName = state.primaryMuscleName,
        )

        val instructions = canonical?.instructions.orEmpty().sortedBy { it.sortOrder }
        if (instructions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("How to perform")
                instructions.forEachIndexed { index, step ->
                    Text("${index + 1}. ${step.instruction}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        val media = canonical?.media.orEmpty().filter { it.uri.isNotBlank() }
        val fallbackUri = exercise.demoUri?.takeIf { legacy -> media.none { it.uri == legacy } }
        if (media.isNotEmpty() || fallbackUri != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Media")
                (media.map { it.type to it.uri } + listOfNotNull(fallbackUri?.let { "IMAGE" to it })).forEach { (type, uri) ->
                    if (type == "VIDEO") {
                        Text(
                            "Open video: $uri",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(uri) } },
                        )
                    } else {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = "Exercise media",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                                .semantics { contentDescription = "Exercise image" },
                        )
                    }
                    val record = media.firstOrNull { it.uri == uri }
                    val attribution = mediaAttribution(record)
                    if (attribution.isNotBlank()) {
                        Text(attribution, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text("No exercise media available", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // History ledger block — numbers are monospace, the ledger way.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("History")
            val history = state.history
            if (history.lastSet == null) {
                Text(
                    "No sets logged yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 13.dp),
                ) {
                    MonoText(
                        text = "Last set: ${WeightUnits.displayText(history.lastSet.weight)}" +
                            "${WeightUnits.suffix(state.weightUnit)} × ${history.lastSet.reps}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MonoText(
                        text = "Best weight: " +
                            WeightUnits.display(history.bestWeight ?: 0.0, state.weightUnit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MonoText(
                        text = "Logged in ${history.sessionCount} session" +
                            if (history.sessionCount == 1) "" else "s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Attribution line for one media record: creator/attribution and license,
 * deduplicated, absent when the record carries no provenance.
 */
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
