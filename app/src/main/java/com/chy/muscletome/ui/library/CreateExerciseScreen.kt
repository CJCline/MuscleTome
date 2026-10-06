package com.chy.muscletome.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.ui.components.AccentButton
import com.chy.muscletome.ui.components.SectionHeader
import com.chy.muscletome.ui.components.SelectOnFocusOutlinedTextField
import com.chy.muscletome.ui.theme.AgedPaper
import com.chy.muscletome.ui.theme.ArchivalCream
import com.chy.muscletome.ui.theme.Ink
import com.chy.muscletome.ui.theme.MutedGrayBorder
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateExerciseScreen(
    onBack: () -> Unit,
    viewModel: CreateExerciseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var parentSectionExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    val presetCategories = listOf("CHEST", "BACK", "LEGS", "SHOULDERS", "ARMS", "CORE")
    val presetMuscles = mapOf(
        "CHEST" to listOf("PECTORALIS"),
        "BACK" to listOf("LATS", "ERECTOR SPINAE", "RHOMBOIDS", "TRAPS"),
        "LEGS" to listOf("QUADRICEPS", "HAMSTRINGS", "GLUTES", "CALVES"),
        "SHOULDERS" to listOf("DELTOIDS", "FRONT DELTS", "SIDE DELTS", "REAR DELTS"),
        "ARMS" to listOf("BICEPS", "TRICEPS", "FOREARMS"),
        "CORE" to listOf("ABS", "OBLIQUES"),
    )

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
                title = { Text("CREATE CUSTOM EXERCISE", style = MuscleTomeTextStyles.heading, color = ArchivalCream) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "BACK", tint = ArchivalCream)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SelectOnFocusOutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = MutedGrayBorder, shape = RoundedCornerShape(2.dp)),
                label = { Text("EXERCISE NAME", style = MuscleTomeTextStyles.label) },
                singleLine = true,
                isError = state.nameTaken,
                supportingText = if (state.nameTaken) {
                    { Text("EXERCISE WITH THIS NAME ALREADY EXISTS", style = MuscleTomeTextStyles.systemMessage) }
                } else null,
            )

            SectionHeader("CATEGORY")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetCategories.forEach { categoryName ->
                    FilterChip(
                        selected = state.category == categoryName,
                        onClick = {
                            viewModel.onCategoryChange(categoryName)
                            val defaultMuscle = presetMuscles[categoryName]?.firstOrNull() ?: ""
                            if (defaultMuscle.isNotBlank()) viewModel.onMuscleGroupChange(defaultMuscle)
                        },
                        label = { Text(categoryName, style = MuscleTomeTextStyles.tag) },
                    )
                }
            }
            SelectOnFocusOutlinedTextField(
                value = state.category,
                onValueChange = viewModel::onCategoryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = MutedGrayBorder, shape = RoundedCornerShape(2.dp)),
                label = { Text("CUSTOM CATEGORY", style = MuscleTomeTextStyles.label) },
                singleLine = true,
            )

            SectionHeader("PRIMARY MUSCLE GROUP")
            val availableMuscles = presetMuscles[state.category] ?: emptyList()
            if (availableMuscles.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableMuscles.forEach { muscleName ->
                        FilterChip(
                            selected = state.muscleGroup == muscleName,
                            onClick = { viewModel.onMuscleGroupChange(muscleName) },
                            label = { Text(muscleName, style = MuscleTomeTextStyles.tag) },
                        )
                    }
                }
            }
            SelectOnFocusOutlinedTextField(
                value = state.muscleGroup,
                onValueChange = viewModel::onMuscleGroupChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = MutedGrayBorder, shape = RoundedCornerShape(2.dp)),
                label = { Text("CUSTOM PRIMARY MUSCLE GROUP", style = MuscleTomeTextStyles.label) },
                singleLine = true,
            )

            SectionHeader("LINK AS VARIATION OF PARENT EXERCISE (OPTIONAL)")
            CreateExerciseParentVariationCard(
                parentExercises = state.parentExercises,
                selectedParentId = state.parentExerciseId,
                expanded = parentSectionExpanded,
                onToggleExpand = { parentSectionExpanded = !parentSectionExpanded },
                onSelectParent = viewModel::onParentExerciseSelected,
            )

            AccentButton(
                text = "SAVE CUSTOM EXERCISE",
                onClick = viewModel::save,
                enabled = state.canSave,
                minHeight = 56.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun CreateExerciseParentVariationCard(
    parentExercises: List<ExerciseEntity>,
    selectedParentId: String?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectParent: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedParent = parentExercises.find { it.id == selectedParentId }
    val selectedParentName = selectedParent?.name?.uppercase(Locale.US) ?: "NONE (STANDALONE PARENT EXERCISE)"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MutedGrayBorder, shape = RoundedCornerShape(2.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CURRENT LINK",
                    style = MuscleTomeTextStyles.label,
                    color = MutedGrayBorder,
                )
                Text(
                    text = selectedParentName,
                    style = MuscleTomeTextStyles.button,
                    color = ArchivalCream,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "COLLAPSE PARENT EXERCISE OPTIONS" else "EXPAND PARENT EXERCISE OPTIONS",
                tint = ArchivalCream,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (selectedParentId == null) ArchivalCream else AgedPaper,
                            shape = RoundedCornerShape(2.dp),
                        )
                        .clickable { onSelectParent(null) }
                        .padding(12.dp),
                ) {
                    Text(
                        "NONE (STANDALONE PARENT EXERCISE)",
                        style = MuscleTomeTextStyles.button,
                        color = if (selectedParentId == null) ArchivalCream else AgedPaper,
                    )
                }

                parentExercises.forEach { parent ->
                    val isSelected = selectedParentId == parent.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ArchivalCream else MutedGrayBorder,
                                shape = RoundedCornerShape(2.dp),
                            )
                            .clickable { onSelectParent(parent.id) }
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                parent.name.uppercase(Locale.US),
                                style = MuscleTomeTextStyles.button,
                                color = if (isSelected) ArchivalCream else AgedPaper,
                            )
                            if (isSelected) {
                                Text("[LINKED]", style = MuscleTomeTextStyles.tag, color = ArchivalCream)
                            }
                        }
                    }
                }
            }
        }
    }
}
