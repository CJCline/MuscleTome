package com.chy.muscletome.ui.library

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chy.muscletome.data.local.entity.PendingExerciseImportEntity
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.BrutalistCard
import com.chy.muscletome.ui.components.BrutalistOutlinedButton
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseImportReviewScreen(
    onBack: () -> Unit,
    viewModel: ExerciseImportReviewViewModel = hiltViewModel(),
) {
    val pending by viewModel.pending.collectAsState(emptyList())
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("EXERCISE IMPORT REVIEW", style = MuscleTomeTextStyles.heading) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "BACK")
                }
            },
        )
    }) { padding ->
        if (pending.isEmpty()) {
            Text(
                "NO IMPORTS NEED REVIEW",
                style = MuscleTomeTextStyles.systemMessage,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pending, key = { it.id }) { item -> ReviewCard(item, viewModel) }
            }
        }
    }
}

@Composable
private fun ReviewCard(item: PendingExerciseImportEntity, viewModel: ExerciseImportReviewViewModel) {
    val candidates = Json.decodeFromString<List<String>>(item.candidateExerciseIdsJson)
    BrutalistCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.displayName.uppercase(Locale.US), style = MaterialTheme.typography.titleMedium)
            Text(
                "SOURCE: ${(item.sourceKey ?: "UNKNOWN").uppercase(Locale.US)} · ${(item.externalExerciseId ?: "NO EXTERNAL ID").uppercase(Locale.US)}",
                style = MuscleTomeTextStyles.tag,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "MATCHING SIGNALS: NAME, MOVEMENT PATTERN AND TARGET OVERLAP",
                style = MuscleTomeTextStyles.tag,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "MERGE LINKS THIS SOURCE TO EXISTING EXERCISE AND KEEPS CURRENT NAME, INSTRUCTIONS, TARGETS AND EQUIPMENT.",
                style = MuscleTomeTextStyles.systemMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            candidates.forEach { candidate ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton(
                        text = "MERGE INTO ${candidate.take(12).uppercase(Locale.US)}",
                        onClick = { viewModel.merge(item.id, candidate) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistOutlinedButton(
                    text = "KEEP BOTH",
                    onClick = { viewModel.keepBoth(item.id) },
                    modifier = Modifier.weight(1f),
                )
                BrutalistOutlinedButton(
                    text = "DISCARD",
                    onClick = { viewModel.discard(item.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
