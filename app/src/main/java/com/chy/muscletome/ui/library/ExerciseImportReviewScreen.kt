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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
            title = { Text("Exercise import review") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
    }) { padding ->
        if (pending.isEmpty()) {
            Text("No imports need review", modifier = Modifier.padding(padding).padding(24.dp))
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
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
            Text("Source: ${item.sourceKey ?: "Unknown"} · ${item.externalExerciseId ?: "No external ID"}")
            Text("Matching signals: name, movement pattern and target overlap")
            candidates.forEach { candidate ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.merge(item.id, candidate) }) {
                        Text("Merge into ${candidate.take(12)}")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.keepBoth(item.id) }) { Text("Keep Both") }
                Button(onClick = { viewModel.discard(item.id) }) { Text("Discard") }
            }
        }
    }
}
