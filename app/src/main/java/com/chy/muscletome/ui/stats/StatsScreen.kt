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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())

    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("Sessions", state.completedSessionCount.toString(), Modifier.weight(1f))
                    StatCard("Sets", state.totalSets.toString(), Modifier.weight(1f))
                    StatCard("Volume", state.totalVolume.toInt().toString(), Modifier.weight(1f))
                }
            }

            item { Text("This week by muscle", style = MaterialTheme.typography.titleMedium) }
            if (state.muscles.isEmpty()) {
                item { Text("Log a workout to see weekly volume.") }
            }
            items(state.muscles, key = { it.muscle.id }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(row.muscle.name) },
                        supportingContent = {
                            Text("${row.setsThisWeek} sets · ${row.volumeThisWeek.toInt()} volume")
                        },
                    )
                }
            }

            item { Text("Exercises", style = MaterialTheme.typography.titleMedium) }
            items(state.exercises, key = { it.exercise.id }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(row.exercise.name) },
                        supportingContent = {
                            val last = if (row.lastWeight != null && row.lastReps != null) {
                                "Last ${row.lastWeight} × ${row.lastReps}"
                            } else {
                                "No sets yet"
                            }
                            val e1rm = row.estimated1Rm?.let { " · e1RM ${it.toInt()}" } ?: ""
                            Text("$last$e1rm · ${row.setCount} sets")
                        },
                    )
                }
            }

            item { Text("Sessions", style = MaterialTheme.typography.titleMedium) }
            items(state.sessions, key = { it.session.id }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = {
                            Text(dateFormat.format(Date(row.session.startedAtEpochMs)))
                        },
                        supportingContent = {
                            val names = row.exerciseNames.distinct().joinToString(", ").ifBlank { "No exercises" }
                            val done = if (row.session.endedAtEpochMs != null) "Finished" else "Open"
                            Text("$done · ${row.setCount} sets · ${row.volume.toInt()} vol\n$names")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}