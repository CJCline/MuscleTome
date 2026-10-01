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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.repository.MuscleVolume
import com.chy.muscletome.domain.session.WeightUnits
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.StatBlock
import com.chy.muscletome.ui.components.VolumeBar
import androidx.compose.ui.text.font.FontWeight
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

    // Target editing dialog: the row the user tapped (null = closed).
    var targetRow by remember { mutableStateOf<MuscleVolume?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Stats") }) },
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
                val target = row.weeklySetTarget
                val fraction = if (target != null && target > 0) {
                    (row.setsThisWeek.toDouble() / target).coerceAtMost(1.0)
                } else {
                    row.volumeThisWeek / maxWeeklyVolume
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { targetRow = row }
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.muscle.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonoText(
                                text = if (target != null) {
                                    "${row.setsThisWeek} / $target sets"
                                } else {
                                    "${row.setsThisWeek} sets · ${row.volumeThisWeek.toInt()} vol"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (target != null && row.setsThisWeek >= target) {
                                MicroTag(text = "Target met")
                            }
                        }
                    }
                    VolumeBar(fraction = fraction.toFloat())
                    if (target == null) {
                        Text(
                            "Tap to set a weekly set target",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            // --- Personal records: the best-ever ledger ----------------------
            item { SectionHeader("Personal records") }
            if (state.personalRecords.isEmpty()) {
                item {
                    Text(
                        "Beat your best e1RM on an exercise and it's recorded here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.personalRecords, key = { it.exercise.id }) { record ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            record.exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MonoText(
                            text = dateFormat.format(Date(record.set.completedAtEpochMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        MonoText(
                            text = WeightUnits.displayText(record.set.weight) +
                                WeightUnits.suffix(state.weightUnit) +
                                " × ${record.set.reps}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MonoText(
                            text = "e1RM ${record.e1rm.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                        "Last ${WeightUnits.displayText(row.lastWeight)}" +
                            "${WeightUnits.suffix(state.weightUnit)} × ${row.lastReps}"
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

    // Tap-a-muscle target editor: set, adjust, or clear the weekly set goal.
    val row = targetRow
    if (row != null) {
        var text by remember(row.muscle.id) { mutableStateOf(row.weeklySetTarget?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { targetRow = null },
            title = { Text("${row.muscle.name} weekly target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField(
                        value = text,
                        onValueChange = { value ->
                            text = value.filter { it.isDigit() }.take(3)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Sets per week") },
                        supportingText = { Text("Blank = no target") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setWeeklySetTarget(
                            row.muscle.id,
                            text.toIntOrNull(),
                        )
                        targetRow = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { targetRow = null }) { Text("Cancel") }
            },
        )
    }
}
