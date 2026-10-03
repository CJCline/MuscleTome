package com.chy.muscletome.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.repository.MuscleVolume
import com.chy.muscletome.domain.session.WeightUnits
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.EmptyState
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.MonoText
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField
import com.chy.muscletome.ui.components.StatBlock
import com.chy.muscletome.ui.components.VolumeBar
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
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
    val dateFormat = SimpleDateFormat("EEE d MMM HH:mm", Locale.US)
    val maxWeeklyVolume = state.muscles.maxOfOrNull { it.volumeThisWeek } ?: 1.0
    val hasAnything = state.completedSessionCount > 0 || state.sessions.isNotEmpty()

    var targetRow by remember { mutableStateOf<MuscleVolume?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("STATS", style = MuscleTomeTextStyles.heading) }) },
    ) { innerPadding ->
        if (!hasAnything) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EmptyState(
                    title = "EMPTY TOME",
                    body = "YOUR TRAINING HISTORY GETS WRITTEN HERE, SESSION BY SESSION.",
                )
                AccentButton(
                    text = "START A WORKOUT",
                    onClick = onOpenHome,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBlock("SESSIONS", state.completedSessionCount.toString(), Modifier.weight(1f))
                        StatBlock("SETS", state.totalSets.toString(), Modifier.weight(1f))
                        StatBlock("VOLUME", state.totalVolume.toInt().toString(), Modifier.weight(1f))
                    }
                }

                item { SectionHeader("THIS WEEK BY MUSCLE") }
                if (state.muscles.isEmpty()) {
                    item {
                        Text(
                            "LOG A WORKOUT TO SEE WEEKLY VOLUME.",
                            style = MuscleTomeTextStyles.systemMessage,
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
                                row.muscle.name.uppercase(Locale.US),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MonoText(
                                    text = if (target != null) {
                                        "${row.setsThisWeek} / $target SETS"
                                    } else {
                                        "${row.setsThisWeek} SETS · ${row.volumeThisWeek.toInt()} VOL"
                                    },
                                    style = MuscleTomeTextStyles.tag,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (target != null && row.setsThisWeek >= target) {
                                    MicroTag(text = "TARGET MET")
                                }
                            }
                        }
                        VolumeBar(fraction = fraction.toFloat())
                        if (target == null) {
                            Text(
                                "TAP TO SET A WEEKLY SET TARGET",
                                style = MuscleTomeTextStyles.tag,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }

                item { SectionHeader("PERSONAL RECORDS") }
                if (state.personalRecords.isEmpty()) {
                    item {
                        Text(
                            "BEAT YOUR BEST E1RM ON AN EXERCISE AND IT'S RECORDED HERE.",
                            style = MuscleTomeTextStyles.systemMessage,
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
                                record.exercise.name.uppercase(Locale.US),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            MonoText(
                                text = dateFormat.format(Date(record.set.completedAtEpochMs)).uppercase(Locale.US),
                                style = MuscleTomeTextStyles.tag,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            MonoText(
                                text = MuscleTomeTextStyles.formatWeight(record.set.weight) +
                                    WeightUnits.suffix(state.weightUnit) +
                                    " × ${record.set.reps}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            MonoText(
                                text = "E1RM ${record.e1rm.toInt()}",
                                style = MuscleTomeTextStyles.tag,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                item { SectionHeader("EXERCISES") }
                items(state.exercises, key = { it.exercise.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                row.exercise.name.uppercase(Locale.US),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            MonoText(
                                text = "${row.setCount} SETS",
                                style = MuscleTomeTextStyles.tag,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val last = if (row.lastWeight != null && row.lastReps != null) {
                            "LAST ${MuscleTomeTextStyles.formatWeight(row.lastWeight)}" +
                                "${WeightUnits.suffix(state.weightUnit)} × ${row.lastReps}"
                        } else {
                            "NO SETS YET"
                        }
                        val e1rm = row.estimated1Rm?.let { "E1RM ${it.toInt()}" } ?: ""
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            MonoText(
                                text = last,
                                style = MuscleTomeTextStyles.tag,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (e1rm.isNotBlank()) {
                                MonoText(
                                    text = e1rm,
                                    style = MuscleTomeTextStyles.tag,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                item { SectionHeader("SESSIONS") }
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
                                text = dateFormat.format(Date(row.session.startedAtEpochMs)).uppercase(Locale.US),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            MicroTag(
                                text = if (row.session.endedAtEpochMs != null) "FINISHED" else "OPEN",
                                color = if (row.session.endedAtEpochMs != null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        MonoText(
                            text = "${row.setCount} SETS · ${row.volume.toInt()} VOL",
                            style = MuscleTomeTextStyles.tag,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val names = row.exerciseNames.distinct().joinToString(", ").uppercase(Locale.US).ifBlank { "NO EXERCISES" }
                        Text(
                            names,
                            style = MuscleTomeTextStyles.tag,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    val row = targetRow
    if (row != null) {
        var text by remember(row.muscle.id) { mutableStateOf(row.weeklySetTarget?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { targetRow = null },
            title = { Text("${row.muscle.name.uppercase(Locale.US)} WEEKLY TARGET", style = MuscleTomeTextStyles.heading) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectOnFocusOutlinedTextField(
                        value = text,
                        onValueChange = { value ->
                            text = value.filter { it.isDigit() }.take(3)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("SETS PER WEEK", style = MuscleTomeTextStyles.label) },
                        supportingText = { Text("BLANK = NO TARGET", style = MuscleTomeTextStyles.tag) },
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
                ) { Text("SAVE", style = MuscleTomeTextStyles.button) }
            },
            dismissButton = {
                TextButton(onClick = { targetRow = null }) { Text("CANCEL", style = MuscleTomeTextStyles.button) }
            },
        )
    }
}
