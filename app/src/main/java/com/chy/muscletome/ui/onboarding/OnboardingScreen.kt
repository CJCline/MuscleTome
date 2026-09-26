package com.chy.muscletome.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.model.WeightUnit
import com.chy.muscletome.domain.template.RoutineTemplate
import com.chy.muscletome.domain.template.RoutineTemplates
import com.chy.muscletome.ui.components.MicroTag
import com.chy.muscletome.ui.components.SectionHeader

/**
 * Short first-run setup: units, available equipment, and a starter program.
 * Everything here is changeable later in Settings — this just guarantees the
 * first Start button does something sensible.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // System back completes setup with the current selections — the user
    // is never trapped on this screen.
    BackHandler { viewModel.complete(onDone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "MUSCLETOME",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Set up your training tome",
                            style = MaterialTheme.typography.labelSmall,
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
            SectionHeader("Units")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.unit == WeightUnit.KG,
                    onClick = { viewModel.setUnit(WeightUnit.KG) },
                    label = { Text("Kilograms (kg)") },
                )
                FilterChip(
                    selected = state.unit == WeightUnit.LB,
                    onClick = { viewModel.setUnit(WeightUnit.LB) },
                    label = { Text("Pounds (lb)") },
                )
            }

            SectionHeader("Your equipment")
            Text(
                "Only exercises you can actually do get picked.",
                style = MaterialTheme.typography.bodySmall,
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
                        label = { Text(equipment.name) },
                    )
                }
            }

            SectionHeader("Pick a program")
            (RoutineTemplates.ALL + RoutineTemplates.SCRATCH).forEach { template ->
                TemplateCard(
                    template = template,
                    selected = state.selectedTemplate?.id == template.id,
                    onClick = { viewModel.selectTemplate(template) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.complete(onDone) },
                enabled = state.selectedTemplate != null && !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("Finish setup".uppercase()) }
            Text(
                "You can change any of this later in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TemplateCard(
    template: RoutineTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spine = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(IntrinsicSize.Max)
                .background(spine),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (selected) MicroTag(text = "Selected")
            }
            Text(
                template.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (template.days.isNotEmpty()) {
                val slots = template.days.sumOf { it.slots.size }
                val autoPicked = template.days.sumOf { day ->
                    day.slots.count { it.type == SlotType.TARGET }
                }
                MicroTag(
                    text = "${template.days.size} days · $slots slots · $autoPicked auto-picked",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
