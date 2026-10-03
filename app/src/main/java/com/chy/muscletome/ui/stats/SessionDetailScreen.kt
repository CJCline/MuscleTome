package com.chy.muscletome.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import com.chy.muscletome.ui.components.BrutalistCard
import com.chy.muscletome.ui.components.LedgerRow
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

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
                title = { Text("SESSION", style = MuscleTomeTextStyles.heading) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "BACK")
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
                BrutalistCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val previous = state.exercises.getOrNull(index - 1)
                        val startsGroup = (row.supersetGroupId != null) &&
                            (previous?.supersetGroupId != row.supersetGroupId)
                        if (startsGroup) {
                            MicroTag(
                                text = "SUPERSET",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            row.exerciseName.uppercase(Locale.US),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MicroTag(
                            text = row.reason.uppercase(Locale.US),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        row.sets.forEachIndexed { setIndex, set ->
                            val weightDisplay = "${MuscleTomeTextStyles.formatWeight(set.weight)}${WeightUnits.suffix(state.weightUnit).trim()} × ${set.reps}"
                            val effortTag = set.rpe?.let { rpe ->
                                val rir = EffortScales.rirFor(rpe)
                                if (rir != null) "RPE ${rpe.toInt()} / RIR $rir" else "RPE ${rpe.toInt()}"
                            }
                            LedgerRow(
                                index = setIndex + 1,
                                primaryText = "SET ${(setIndex + 1).toString().padStart(2, '0')}",
                                dataText = weightDisplay,
                                bracketTag = effortTag,
                            )
                        }
                    }
                }
            }
        }
    }
}
