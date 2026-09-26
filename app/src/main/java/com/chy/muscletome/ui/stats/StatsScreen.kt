package com.chy.muscletome.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.StatBlock
import com.chy.muscletome.ui.components.VolumeBar
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.LedgerIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenSession: (String) -> Unit,
    onOpenHome: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
    val maxWeeklyVolume = state.muscles.maxOfOrNull { it.volumeThisWeek } ?: 1.0
    val hasAnything = state.completedSessionCount > 0 || state.sessions.isNotEmpty()

    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) },
    ) { innerPadding ->
        if (!hasAnything) {
            // First-run ledger: nothing to show yet — one primary CTA.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EmptyState(
                    title = "Empty tome",
                    body = "Your training history gets written here, session by session.",
                )
                Button(
                    onClick = onOpenHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text("Start a workout".uppercase()) }
            }
        } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- The ledger header: three monospace stat columns ----------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    StatBlock("Sessions", state.completedSessionCount.toString(), Modifier.weight(1f))
                    StatBlock("Sets", state.totalSets.toString(), Modifier.weight(1f))
                    StatBlock("Volume", state.totalVolume.toInt().toString(), Modifier.weight(1f))
                }
            }

            // --- This week by muscle: amber volume bars ---------------------
            item { SectionHeader("This week by muscle") }
            if (state.muscles.isEmpty()) {
                item {
                    Text(
                        "Log a workout to see weekly volume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.muscles, key = { it.muscle.id }) { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            row.muscle.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MonoText(
                            text = "${row.setsThisWeek} sets · ${row.volumeThisWeek.toInt()} vol",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    VolumeBar(fraction = (row.volumeThisWeek / maxWeeklyVolume).toFloat())
                }
            }

            // --- Exercises: mono columns of last set / e1RM -------------------
            item { SectionHeader("Exercises") }
            items(state.exercises, key = { it.exercise.id }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            row.exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MonoText(
                            text = "${row.setCount} sets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val last = if (row.lastWeight != null && row.lastReps != null) {
                        "Last ${row.lastWeight} × ${row.lastReps}"
                    } else {
                        "No sets yet"
                    }
                    val e1rm = row.estimated1Rm?.let { "e1RM ${it.toInt()}" } ?: ""
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        MonoText(
                            text = last,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (e1rm.isNotBlank()) {
                            MonoText(
                                text = e1rm,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // --- Sessions: ledger rows with mono timestamps ------------------
            item { SectionHeader("Sessions") }
            items(state.sessions, key = { it.session.id }) { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSession(row.session.id) }
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonoText(
                            text = dateFormat.format(Date(row.session.startedAtEpochMs)),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MicroTag(
                            text = if (row.session.endedAtEpochMs != null) "Finished" else "Open",
                            color = if (row.session.endedAtEpochMs != null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    MonoText(
                        text = "${row.setCount} sets · ${row.volume.toInt()} vol",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val names = row.exerciseNames.distinct().joinToString(", ").ifBlank { "No exercises" }
                    Text(
                        names,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }
    }
}
