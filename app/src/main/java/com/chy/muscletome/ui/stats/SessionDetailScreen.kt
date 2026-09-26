package com.chy.muscletome.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.session.EffortScales
import com.chy.muscletome.domain.session.WeightUnits
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val indexed = state.exercises.mapIndexed { i, row -> i to row }
            items(indexed, key = { "${it.second.exerciseName}-${it.first}" }) { (index, row) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Tag the first row of each superset run; grouped rows
                    // share the tag until a different (or null) group follows.
                    val previous = state.exercises.getOrNull(index - 1)
                    val startsGroup = (row.supersetGroupId != null) &&
                        (previous?.supersetGroupId != row.supersetGroupId)
                    if (startsGroup) {
                        MicroTag(
                            text = "Superset",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        row.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    MicroTag(
                        text = row.reason,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.sets.forEachIndexed { index, set ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MonoText(
                                text = "SET ${(index + 1).toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MonoText(
                                    text = WeightUnits.displayText(set.weight) +
                                        WeightUnits.suffix(state.weightUnit) +
                                        " × ${set.reps}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                set.rpe?.let { rpe ->
                                    MicroTag("RPE ${rpe.toInt()}")
                                    EffortScales.rirFor(rpe)?.let { MicroTag("RIR $it") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
