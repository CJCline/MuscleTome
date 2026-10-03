package com.chy.muscletome.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { viewModel.complete(onDone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "MUSCLETOME",
                            style = MuscleTomeTextStyles.heading,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "SET UP YOUR TRAINING TOME",
                            style = MuscleTomeTextStyles.tag,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("UNITS")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.unit == WeightUnit.KG,
                    onClick = { viewModel.setUnit(WeightUnit.KG) },
                    label = { Text("KILOGRAMS (KG)", style = MuscleTomeTextStyles.tag) },
                )
                FilterChip(
                    selected = state.unit == WeightUnit.LB,
                    onClick = { viewModel.setUnit(WeightUnit.LB) },
                    label = { Text("POUNDS (LB)", style = MuscleTomeTextStyles.tag) },
                )
            }

            SectionHeader("YOUR EQUIPMENT")
            Text(
                "ONLY EXERCISES MATCHING YOUR EQUIPMENT WILL BE PICKED.",
                style = MuscleTomeTextStyles.systemMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.equipment.forEach { equipment ->
                    FilterChip(
                        selected = equipment.id in state.selectedEquipmentIds,
                        onClick = { viewModel.toggleEquipment(equipment.id) },
                        label = { Text(equipment.name.uppercase(Locale.US), style = MuscleTomeTextStyles.tag) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            AccentButton(
                text = "FINISH SETUP",
                onClick = { viewModel.complete(onDone) },
                enabled = !state.busy,
            )
            Text(
                "PICK A STARTER PROGRAM NEXT ON TODAY TAB.",
                style = MuscleTomeTextStyles.tag,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
